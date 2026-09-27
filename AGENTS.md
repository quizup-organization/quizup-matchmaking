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
| `ProfileRepositoryPort` | `quizup-profile` | `ProfileQuery.GetProfileQuery`   |
| `MatchmakingPlayerPort` (présence) | `quizup-profile` | `PresenceQuery.GetPresencesByIdsQuery` |

Implémentation : `application/service/MatchmakingPlayerService` (→ profile/progression, nom + niveau + pays).

**Ports sortants locaux** : `LobbyRepositoryPort`, `LobbyEventStorePort`.

### Contrat BFF (ticket)

- `POST /api/matchmaking/tickets` (file d'attente) ; `GET /{ticketId}` ;
  `POST /{ticketId}/cancel` ; `GET /{ticketId}/notifications`.
- **Read model ticket dédié** (`matchmaking_ticket_entry`, projection
  `matchmaking-ticket-projection`) : statuts produit explicites `SEARCHING|MATCHED|CANCELLED`,
  alimenté par les événements de lobby. `GET /{ticketId}` lit ce ticket (le BFF n'interprète plus
  `LobbyStatus`). Le service expose son event store en **`EventEnvelope`** SDK
  (`GetLobbyEventsQuery`, payload `LobbyEvent` typé) ; c'est le BFF qui mappe vers les notifications
  web (`TicketNotification`, `LobbyJoinedEvent` non exposé) et les pousse sur
  `/topic/matchmaking/tickets/{ticketId}`.
- `MatchmakingService` apparie par **sujet + niveau ±5 + préférence pays + présence** (via
  `MatchmakingPlayerPort.filterOnline`, requête `PresenceQuery.GetPresencesByIdsQuery`) : on ne
  rejoint jamais un lobby dont l'initiateur est hors ligne. Les courses de join (lobby annulé ou
  rempli entre la sélection et la commande) sont rejouées sur le candidat suivant (borné).
- **Fallback bot automatique** (pas de queue infinie) : à l'échéance de 10 s sans adversaire, la
  `LobbySaga` injecte le bot si l'initiateur est toujours en ligne, sinon elle **annule** le lobby
  (initiateur parti) au lieu de créer une partie fantôme.
- Les queries `SearchLobbyQuery` restent disponibles pour les **futures surfaces d'administration**.

Le second joueur compatible rejoint le lobby du premier (les deux tickets pointent donc le
**même** `lobbyId`). Le read model ticket reste lisible après fermeture (jusqu'à la purge).

**Purge event-driven** (pas de scheduler JPA) : `LobbySaga`, à réception de
`LobbyCompletedEvent`/`LobbyCancelledEvent`, planifie un deadline `LOBBY_PURGE`
(`LobbyDeadline.LOBBY_RETENTION_DURATION`, 1 h). À l'échéance, la saga envoie `PurgeLobbyCommand`
→ `LobbyPurgedEvent` → l'agrégat `markDeleted()` (flux supprimé côté event store logique) et
`LobbyProjection` supprime sa ligne (`deleteById`). La saga se termine sur `LobbyPurgedEvent`.
Les deadlines sont annulés de façon ciblée (`cancelSchedule(name, scheduleId)`, id conservé).

