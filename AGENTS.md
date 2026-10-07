# AGENTS.md — quizup-matchmaking

> Service de **matchmaking** : appariement public (« Défier le monde ») et salons privés (salle
> d'attente). Architecture : Axon Framework (CQRS/EDA) + JPA (projections). Sagas et deadlines
> pour l'appariement et l'expiration.
> Pour les règles de patterns : [
`../../best-practices/.backend/hexagonal-architecture.md`](../../best-practices/.backend/hexagonal-architecture.md).

---

## 1. Rôle

Deux responsabilités **distinctes**, chacune avec son agrégat :

- **Appariement public** (store chaud Redis) : trouve un adversaire pour un sujet
  (même thème, niveau ±5, langues **en union** couvertes par le thème) ; à l'échéance de **5 s**
  sans adversaire, crée une **partie bot**. Le bot n'est jamais un participant du salon.
  Plus d'agrégat event-sourcé : tickets et file d'attente vivent dans Redis (TTL, claim Lua
  atomique), les timers sont durables (ZSET) et les événements passent par une **outbox** →
  bus Axon (historique `…/notifications` et notifications BFF inchangés).
- **Défi nominatif** (store chaud Redis) : intention **asynchrone** « A défie B » (TTL 1 h,
  accept/refuse/annule/expire), sans présence ni partie. À l'acceptation, la salle temps réel est
  créée (id déterministe) et reliée au défi.
- **Salle temps réel** (store chaud Redis) : présence des deux humains (lien partageable
  `/join/{lobbyId}` ou salle issue d'un défi). Chacun **entre** (`EnterLobbyRoom`) ; quand les deux
  sont présents, un **compte à rebours de 3 s** précède la création de la partie. Un salon non
  lancé expire après **1 jour** ; une **sortie explicite est non destructive** (retour possible) et
  seul un joueur **hors ligne** ferme ses salles ouvertes (`RoomPresenceHandler`).

### Cycle de vie (statuts réduits)

Les états du session tier sont **éphémères** : statuts réduits au cycle de vie et **TTL Redis**
(rétention native, plus de purge ni d'agrégat supprimé).

- `MatchmakingStatus = SEARCHING | CLOSED | FAILED` ; `LobbyStatus = CREATED | CLOSED | FAILED` ;
  `ChallengeStatus = PENDING | ACCEPTED | DECLINED | CANCELLED | EXPIRED`.
- `CLOSED` = fin normale (partie créée, annulée, refusée, expirée) ; `FAILED` = échec système
  (création de partie impossible). L'issue exacte est portée par l'événement terminal et la
  notification.
- **Rétention** : tickets ~10 min, salons ouverts 1 jour puis 2 min après clôture, défis 1 h.
  Toute transition terminale écrit son événement dans l'**outbox** → bus Axon : l'event store
  conserve l'historique (`GET .../notifications` reste lisible après expiration du TTL).

**Package** : `io.github.quizup.matchmaking`

---

## 2. Surface (headless)

Service **headless** : aucun contrôleur REST ni WebSocket. La surface applicative unique est le
**`quizup-bff`** (`/api/**` + `/ws`) ; il interroge ce service via le **query bus** Axon et consomme
ses événements. Les handlers de requête/commande, le worker de timers et le relais d'outbox
restent la seule surface exposée par le service.

## 3. Use cases (ports entrants — `domain/port/in/`)

- `MatchmakingUseCase` — démarrer / annuler une recherche d'appariement public
- `LobbyUseCase` — créer / rejoindre / refuser / quitter / annuler un salon privé
- `GetMatchmakingUseCase` — récupération d'un ticket + événements (event store)
- `GetLobbyUseCase` — récupération d'un salon, salons ouverts du joueur (initiateur ou invité) + événements

---

## 4. Dépendances inter-services

| Port out                          | Service cible     | Query Axon envoyée (QueryGateway)          |
|-----------------------------------|-------------------|--------------------------------------------|
| `ProfileRepositoryPort`           | `quizup-profile`  | `ProfileQuery.GetProfileQuery`              |
| `MatchmakingPlayerPort` (présence)| `quizup-profile`  | `PresenceQuery.GetPresencesByIdsQuery`      |
| `TopicAvailabilityPort`           | `quizup-theme`    | `QuestionQuery.CountApprovedQuestionsByTopicAndLanguagesQuery` |

Implémentation : `application/service/MatchmakingPlayerService` (nom + niveau + pays),
`TopicAvailabilityService` (couverture des langues), `PlayerService`.

**Ports sortants locaux** : `MatchmakingStorePort`, `LobbyStorePort`, `ChallengeStorePort`
(store chaud Redis), `SessionTimerPort`, `SessionOutboxPort`, `MatchmakingEventStorePort`,
`LobbyEventStorePort`.

### Appariement public (`MatchmakingService`)

- `CreateMatchmakingCommand` → enfile le ticket (Redis) et **le dernier arrivé initie le
  pairage** : candidats plus anciens du même sujet (niveau ±5), filtre langues
  (`TopicAvailabilityPort`), **claim atomique Lua** de la paire, `CreateGameCommand(HUMAN/HUMAN)`
  puis finalisation des deux tickets + événements outbox.
- Timer `MATCHMAKING_DEADLINE` (5 s) → `CreateGameCommand(BOT)` si le ticket cherche encore ;
  échec de création → `FAILED` + `MatchmakingFailedEvent` (les deux tickets pour une paire).
- Annulation (`CancelMatchmakingCommand`) : atomique, uniquement tant que `SEARCHING`.
- `SessionTimerWorker` (ZSET `session:timers`) et `SessionOutboxRelay` (liste `session:outbox`
  → `EventGateway`, `DomainEventMessage` avec agrégat + séquence) portent l'orchestration.
- **`MatchmakingStorePort` / `SessionTimerPort` / `SessionOutboxPort`** isolent le store chaud ;
  l'agrégat n'est plus la source de vérité (Redis l'est), les événements restent la trace durable.

### Salon privé (`LobbyService`)

- Création nominative (`opponentId`) : auto-défi interdit (`CannotChallengeSelfProblem`) et garde
  linguistique (`TopicAvailabilityPort` + langues des deux profils) → `TopicNotAvailableInLanguageProblem`.
- Expiration planifiée (**1 jour**, TTL Redis + timer `LOBBY_EXPIRY`, seule borne tant que la
  partie n'est pas lancée).
- `Join` (idempotent) : nominatif → invité uniquement (`LobbyNotInvitedProblem`, 403 sinon) ;
  salon partagé → premier arrivé (`LobbyAlreadyFullProblem` au-delà).
- Entrée en salle (`EnterLobbyRoom`, idempotente) : quand les deux sont présents → **compte à
  rebours 3 s** (`LOBBY_READY_CHECK`, timer reprogrammé à chaque ré-entrée).
- Fin du compte à rebours → `CreateGameCommand(HUMAN/HUMAN)` **attendu** (`sendAndWait`) puis
  clôture du salon ; échec → `FAILED` (`GAME_CREATION_FAILED`).
- Sortie **non destructive** : présence retirée, salon toujours `CREATED`, retour possible
  jusqu'à l'expiration (un timer ready devenu obsolète est un no-op).
- Expiration → `CLOSED` (`EXPIRED`).
- `PlayerWentOfflineEvent` (profile) → `RoomPresenceHandler` ferme les salles ouvertes du joueur.
- États terminaux → `CLOSED`/`FAILED` + **rétention TTL 2 min** dans Redis (pas de purge saga).

### Défi nominatif (`ChallengeService`)

- Création : auto-défi interdit, garde linguistique, TTL **1 h** (timer `CHALLENGE_EXPIRY`).
- Acceptation (invité uniquement, idempotente) → salle créée (id déterministe `room:<challengeId>`),
  invité marqué comme participant (`JoinLobby`) puis `roomId` relié au défi
  (`ChallengeRoomCreatedEvent`).
- Refus/annulation → états terminaux (rétention native TTL), expiration par timer si `PENDING`.

### Contrats BFF

- **Appariement** : `POST /api/matchmaking/tickets` (file) ; `GET /{ticketId}` ;
  `POST /{ticketId}/cancel` ; `GET /{ticketId}/notifications` ; WS
  `/topic/matchmaking/tickets/{ticketId}` (`SEARCHING|MATCHED|CANCELLED|FAILED`).
- **Salons** : `POST /api/lobbies { topicId, opponentId? }` (nominatif si `opponentId`) ;
  `GET /{lobbyId}` ; `GET /mine` (initiateur **ou** invité) ;
  `POST /{lobbyId}/join|decline|leave|cancel` ; `GET /{lobbyId}/notifications` ; WS
  `/topic/lobbies/{lobbyId}` (`LOBBY_CREATED|JOINED|DECLINED|COMPLETED|CANCELLED|EXPIRED|FAILED`).
- **Notifications personnelles** : consommées par `quizup-notification` depuis le bus Kafka ;
  poussées par le BFF sur `/topic/notifications/{userId}`.
- Read models : **Redis** (`session:mm:ticket:*`, `session:mm:queue:*`, `session:lobby:*`,
  `session:challenge:*` — TTL natifs) ; l'historique d'événements reste l'event store Axon
  (alimenté par l'outbox), lisible via `GET .../notifications`.
- Statuts : matchmaking `SEARCHING|CLOSED|FAILED` ; salon `CREATED|CLOSED|FAILED`
  (rétention puis purge).
