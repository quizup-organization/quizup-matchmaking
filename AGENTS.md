# AGENTS.md — quizup-matchmaking

> Service de **matchmaking** : lobbies et appariement de joueurs. Architecture : Axon Framework
> (CQRS/EDA) + JPA (projections). Utilise des sagas et deadlines pour la gestion des lobbies.
> Pour les règles de patterns : [
`../../best-practices/.backend/hexagonal-architecture.md`](../../best-practices/.backend/hexagonal-architecture.md).

---

## 1. Rôle

Gestion des **lobbies** (salles) ouvertes pour trouver des adversaires : création, recherche,
rejoindre, annulation. Les lobbies sont le point d'entrée pour démarrer un jeu (voir
`quizup-game`).

**Package** : `io.github.quizup.matchmaking`

---

## 2. Surface (headless)

Service **headless** : aucun contrôleur REST ni WebSocket. La surface applicative unique est le
**`quizup-bff`** (`/api/**` + `/ws`) ; il interroge ce service via le **query bus** Axon et consomme
ses événements. Les handlers de requête/commande, sagas et projections restent la seule surface
exposée par le service.
## 3. Use cases (ports entrants — `domain/port/in/`)

- `OpenLobbyUseCase` — création d'un lobby ouvert
- `JoinLobbyUseCase` — un joueur rejoint un lobby
- `CancelLobbyUseCase` — annulation d'un lobby
- `GetLobbyUseCase` — récupération par id
- `GetLobbyEventsUseCase` — lecture des événements d'un lobby (event store)
- `GetOpenLobbiesByTopicUseCase` — lobbies ouverts pour un topic
- `SearchLobbyUseCase` — recherche paginée

---

## 4. Dépendances inter-services

| Port out   | Service cible     | Query Axon envoyée (QueryGateway) |
|------------|-------------------|-----------------------------------|
| `ProfileRepositoryPort` | `quizup-profile` | `ProfileQuery.FindProfileQuery`   |

Implémentation : `application/service/UserService` (→ profile, `.map(Profile::displayName)`).

**Ports sortants locaux** : `LobbyRepositoryPort`, `LobbyEventStorePort`.

### File d'attente (`/api/matchmaking/queue`)

- `POST /api/matchmaking/queue` → met en file (ticket = lobby) ; `GET /{id}` ; `POST /{id}/cancel` → `200` (annule le lobby associé, transition d'état).
- `MatchmakingService` (application) apparie par **sujet + niveau ±5 + préférence pays**, via
  `MatchmakingPlayerPort` (profil + progression). S'il n'y a pas d'adversaire compatible, un
  lobby est ouvert ; `LobbySaga` gère le **fallback bot** après expiration.
- Notifications live sur `/topic/lobbies/{ticketId}` (réutilise `LobbyNotificationService`).

Le second joueur compatible rejoint le lobby du premier (les deux tickets pointent donc le
**même** `lobbyId`). **`LobbyProjection` conserve les lobbies `COMPLETED`/`CANCELLED`** (statut +
`gameId`) : le client lit le `gameId` via `GET /api/matchmaking/queue/{ticketId}` (statut `MATCHED`).

**Purge event-driven** (pas de scheduler JPA) : `LobbySaga`, à réception de
`LobbyCompletedEvent`/`LobbyCancelledEvent`, planifie un deadline `LOBBY_PURGE`
(`LobbyDeadline.LOBBY_RETENTION_DURATION`, 1 h). À l'échéance, la saga envoie `PurgeLobbyCommand`
→ `LobbyPurgedEvent` → l'agrégat `markDeleted()` (flux supprimé côté event store logique) et
`LobbyProjection` supprime sa ligne (`deleteById`). La saga se termine sur `LobbyPurgedEvent`.
Les deadlines sont annulés de façon ciblée (`cancelSchedule(name, scheduleId)`, id conservé).

Conséquence : `POST /api/lobbies/search` renvoie aussi les lobbies fermés (jusqu'à purge) →
filtrer `status=OPEN`.

