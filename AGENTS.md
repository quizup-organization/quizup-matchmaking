# AGENTS.md — quizup-matchmaking

> Service de **matchmaking** : appariement public (« Défier le monde ») et salons privés (salle
> d'attente). Architecture : Axon Framework (CQRS/EDA) + JPA (projections). Sagas et deadlines
> pour l'appariement et l'expiration.
> Pour les règles de patterns : [
`../../best-practices/.backend/hexagonal-architecture.md`](../../best-practices/.backend/hexagonal-architecture.md).

---

## 1. Rôle

Deux responsabilités **distinctes**, chacune avec son agrégat :

- **Appariement public** (`MatchmakingAggregate`) : trouve un adversaire pour un sujet
  (même thème, niveau ±5, langues **en union** couvertes par le thème) ; à l'échéance de **5 s**
  sans adversaire, crée une **partie bot**. Le bot n'est jamais un participant du salon.
- **Salon privé** (`LobbyAggregate`) : salle d'attente entre **deux humains**. Le lien de partage
  est l'identifiant de l'agrégat (`/join/{lobbyId}`) — pas de code dédié. Dès que le second joueur
  a rejoint, la partie est créée puis le salon est **purgé immédiatement**.

**Package** : `io.github.quizup.matchmaking`

---

## 2. Surface (headless)

Service **headless** : aucun contrôleur REST ni WebSocket. La surface applicative unique est le
**`quizup-bff`** (`/api/**` + `/ws`) ; il interroge ce service via le **query bus** Axon et consomme
ses événements. Les handlers de requête/commande, sagas et projections restent la seule surface
exposée par le service.

## 3. Use cases (ports entrants — `domain/port/in/`)

- `MatchmakingUseCase` — démarrer / annuler une recherche d'appariement public
- `LobbyUseCase` — créer / rejoindre / quitter / annuler un salon privé
- `GetMatchmakingUseCase` — récupération d'un ticket + événements (event store)
- `GetLobbyUseCase` — récupération d'un salon, salons ouverts du joueur + événements

---

## 4. Dépendances inter-services

| Port out                          | Service cible     | Query Axon envoyée (QueryGateway)          |
|-----------------------------------|-------------------|--------------------------------------------|
| `ProfileRepositoryPort`           | `quizup-profile`  | `ProfileQuery.GetProfileQuery`              |
| `MatchmakingPlayerPort` (présence)| `quizup-profile`  | `PresenceQuery.GetPresencesByIdsQuery`      |
| `TopicAvailabilityPort`           | `quizup-theme`    | `QuestionQuery.CountApprovedQuestionsByTopicAndLanguagesQuery` |

Implémentation : `application/service/MatchmakingPlayerService` (nom + niveau + pays),
`TopicAvailabilityService` (couverture des langues), `PlayerService`.

**Ports sortants locaux** : `MatchmakingPoolPort`, `MatchmakingRepositoryPort`,
`MatchmakingEventStorePort`, `LobbyRepositoryPort`, `LobbyEventStorePort`.

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

- `LobbyCreatedEvent` → expiration planifiée (1 h).
- `LobbyJoinedEvent` (2ᵉ humain) → `CreateGameCommand(HUMAN/HUMAN)` puis `CompleteLobbyCommand`
  (le salon est purgé immédiatement) ; échec → `FailLobbyCommand`.
- `LobbyCancelledEvent` / `LobbyExpiredEvent` / `LobbyFailedEvent` → purge après rétention ;
  `LobbyPurgedEvent` termine la saga.

### Contrats BFF

- **Appariement** : `POST /api/matchmaking/tickets` (file) ; `GET /{ticketId}` ;
  `POST /{ticketId}/cancel` ; `GET /{ticketId}/notifications` ; WS
  `/topic/matchmaking/tickets/{ticketId}` (`SEARCHING|MATCHED|CANCELLED|FAILED`).
- **Salons** : `POST /api/lobbies { topicId }` ; `GET /{lobbyId}` ; `GET /mine` ;
  `POST /{lobbyId}/join|leave|cancel` ; `GET /{lobbyId}/notifications` ; WS
  `/topic/lobbies/{lobbyId}` (`LOBBY_CREATED|JOINED|COMPLETED|CANCELLED|EXPIRED|FAILED`).
- Read models : `matchmaking_entry` (pool + ticket), `lobby_entry` (salon). L'agrégat reste la
  source de vérité ; le pool est reconstructible à partir des `MatchmakingStartedEvent`.
- Statuts : matchmaking `SEARCHING|MATCHED|CANCELLED|FAILED` ; salon `OPEN|CANCELLED|EXPIRED|FAILED`
  (la réussite ne persiste pas : purge).
