# AGENTS.md — quizup-matchmaking

> Service de **matchmaking** : appariement public (« Défier le monde ») et salons privés (salle
> d'attente). Architecture : Axon Framework (CQRS/EDA) + JPA (projections). Sagas et deadlines
> pour l'appariement et l'expiration.
> Pour les règles de patterns : [
`../../best-practices/.backend/folder-structure.md`](../../best-practices/.backend/folder-structure.md).

---

## 1. Rôle

Deux responsabilités **distinctes**, chacune avec son agrégat :

- **Appariement public** (`MatchmakingAggregate`) : trouve un adversaire pour un sujet
  (même thème, niveau ±5, langues **en union** couvertes par le thème) ; à l'échéance de **5 s**
  sans adversaire, crée une **partie bot**. Le bot n'est jamais un participant du salon.
- **Défi nominatif** (`ChallengeAggregate`) : intention **asynchrone** « A défie B » (TTL 1 h,
  accept/refuse/annule/expire), sans présence ni partie. À l'acceptation, une saga crée la salle ;
  le lien vers la salle est **dérivé** (`ChallengeRoomId`, déterministe) et vit dans le read model,
  jamais dans l'agrégat. Un état terminal est purgé après rétention (2 min).
- **Salle temps réel** (`LobbyAggregate`) : présence des deux humains (lien partageable
  `/join/{lobbyId}` ou salle issue d'un défi). Chacun **entre** (`EnterLobbyRoom`) ; quand les deux
  sont présents, un **compte à rebours de 3 s** précède la création de la partie. Un salon non
  lancé expire après **1 jour** ; une **sortie explicite est non destructive** (retour possible) et
  seul un joueur **hors ligne** ferme ses salles ouvertes (`RoomPresenceHandler`).

### Cycle de vie (statuts réduits)

Les trois agrégats sont **éphémères** : statuts réduits au cycle de vie et suppression différée
(le défi conserve ses statuts de réponse, sans `CLOSED`/`FAILED`).

- `MatchmakingStatus = SEARCHING | CLOSED | FAILED` ; `LobbyStatus = CREATED | CLOSED | FAILED` ;
  `ChallengeStatus = PENDING | ACCEPTED | DECLINED | CANCELLED | EXPIRED`.
- `CLOSED` = fin normale (partie créée, annulée, refusée, expirée) ; `FAILED` = échec système
  (création de partie impossible). L'issue exacte est portée par l'événement terminal et la
  notification.
- **Aucune suppression immédiate** : l'agrégat reste joignable pendant la **rétention**
  (`LOBBY_PURGE` / `MATCHMAKING_PURGE` / `CHALLENGE_PURGE`, 2 min) puis la saga envoie la commande
  de purge ; `markDeleted()` est appelé dans l'`@EventSourcingHandler` du `*PurgedEvent` (standard
  Axon) et la projection supprime sa ligne. L'event store conserve l'historique
  (`GET .../notifications` reste lisible après purge).

**Package** : `io.github.quizup.matchmaking`

---

## 2. Surface (headless)

Service **headless** : aucun contrôleur REST ni WebSocket. La surface applicative unique est le
**`quizup-bff`** (`/api/**` + `/ws`) ; il interroge ce service via le **query bus** Axon et consomme
ses événements. Les handlers de requête/commande, sagas et projections restent la seule surface
exposée par le service.

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
| `MatchmakingPlayerPort`           | `quizup-profile`  | `ProfileQuery.GetProfileQuery` + `ProgressionQuery.GetProgressionQuery` |
| `TopicAvailabilityPort`           | `quizup-theme`    | `QuestionQuery.CountApprovedQuestionsByTopicAndLanguagesQuery` |

Implémentation : `application/service/MatchmakingPlayerService` (nom + niveau + XP + pays),
`TopicAvailabilityService` (couverture des langues), `PlayerService`.

**Ports sortants locaux** : `MatchmakingPoolPort`, `MatchmakingRepositoryPort`,
`MatchmakingEventStorePort`, `LobbyRepositoryPort`, `LobbyEventStorePort`,
`ChallengeRepositoryPort`.

### Défi nominatif (`ChallengeSaga` + `RoomSaga`)

- `ChallengeSaga` : `ChallengeCreatedEvent` → expiration **1 h** ; chaque terminal
  (`Accepted`, `Declined`, `Cancelled`, `Expired`) → purge planifiée (rétention **2 min**,
  `CHALLENGE_PURGE`) ; `ChallengePurgedEvent` termine la saga (`markDeleted`).
- `RoomSaga` : `ChallengeAcceptedEvent` → **one-shot** (saga créée et terminée dans le même
  handler) : `CreateLobbyCommand` (roomId déterministe `ChallengeRoomId`) + `JoinLobbyCommand`.
  Aucun lien salle n'est écrit dans l'agrégat : la projection du défi dérive le `roomId` sur
  l'acceptation.
- `ChallengePurgeSweeper` : balayeur périodique (60 s) qui envoie une commande de purge
  idempotente pour les défis terminaux plus vieux que rétention + marge (10 min) — filet pour
  l'historique antérieur à la saga de purge ou les deadlines perdues.

### Appariement public (`MatchmakingSaga`)

- `MatchmakingStartedEvent` → planifie une deadline **5 s** ; **le dernier arrivé** initie le
  pairage : `MatchmakingPoolPort.candidates` (même sujet, niveau ±5, plus anciens), filtre par
  langues (`TopicAvailabilityPort`), `claim` atomique, `CreateGameCommand(HUMAN/HUMAN)` puis
  `MarkMatchmakingMatchedCommand` des deux tickets.
- Deadline atteinte sans adversaire → `CreateGameCommand(BOT)` + `MarkMatchmakingMatchedCommand(vsBot=true)`.
- Échec de création de partie → `FailMatchmakingCommand` (les deux tickets).
- **`MatchmakingPoolPort`** isole l'index de recherche (implémentation Postgres ; un adaptateur
  Redis pourra le remplacer sans changer le domaine ; l'agrégat reste la source de vérité).

### Salon privé (`LobbySaga`)

- Création nominative (`opponentId`) : auto-défi interdit (`CannotChallengeSelfProblem`) et garde
  linguistique (`TopicAvailabilityPort` + langues des deux profils) → `TopicNotAvailableInLanguageProblem`.
- `LobbyCreatedEvent` → expiration planifiée (**1 jour**, seule borne du salon tant que la partie
  n'est pas lancée ; annulée par les états terminaux).
- `LobbyJoinedEvent` (2ᵉ humain connu) → l'expiration reste la borne courante.
- `LobbyAllPlayersPresentEvent` (les deux entrés) → **compte à rebours 3 s** (`LOBBY_READY_CHECK`).
- `LobbyLeftEvent` (sortie **non destructive**) → compte à rebours annulé ; le joueur peut revenir
  jusqu'à l'expiration, le participant reste enregistré.
- Fin du compte à rebours → `CreateGameCommand(HUMAN/HUMAN)` **attendu** (`sendAndWait`, pour
  compenser un échec asynchrone) puis `CompleteLobbyCommand` ; échec → `FailLobbyCommand`.
- Expiration → `ExpireLobbyCommand` (`EXPIRED`).
- `PlayerWentOfflineEvent` (profile) → `RoomPresenceHandler` ferme les salles ouvertes du joueur.
- `LobbyCancelled|Declined|Expired|Completed|Failed` → `CLOSED`/`FAILED` + purge planifiée
  (rétention 2 min) ; `LobbyPurgedEvent` termine la saga (`markDeleted`).
- **`MatchmakingPoolPort`** : le claim n'écrit plus de statut (uniquement `claimed_by`), le statut
  reste `SEARCHING` jusqu'à la projection du `Matched`.

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
- Read models : `matchmaking_entry` (pool + ticket), `lobby_entry` (salon). L'agrégat reste la
  source de vérité ; le pool est reconstructible à partir des `MatchmakingStartedEvent`.
- Statuts : matchmaking `SEARCHING|CLOSED|FAILED` ; salon `CREATED|CLOSED|FAILED`
  (rétention puis purge).
