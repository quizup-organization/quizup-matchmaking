# AGENTS.md — quizup-matchmaking

> Service de **matchmaking** : lobbies et appariement de joueurs. Architecture : Axon Framework
> (CQRS/EDA) + JPA (projections). Utilise des sagas et deadlines pour la gestion des lobbies.
> Pour les règles de patterns : [`../../best-practices/hexagonal-architecture.md`](../../best-practices/hexagonal-architecture.md).

---

## 1. Rôle

Gestion des **lobbies** (salles) ouvertes pour trouver des adversaires : création, recherche,
rejoindre, annulation. Les lobbies sont le point d'entrée pour démarrer un jeu (voir
`quizup-game`).

**Package** : `io.github.quizup.matchmaking`

---

## 2. Endpoints REST

### `LobbyController` — `/api/lobbies`
| Méthode | Chemin | Handler | Response |
|---|---|---|---|
| POST | `/api/lobbies/search` | `search(SearchRequest)` | `PageResponse<LobbyResponse>` |
| POST | `/api/lobbies` | `create(OpenLobbyRequest)` | `IdResponse` |
| GET | `/api/lobbies/{lobbyId}` | `get(String)` | `LobbyResponse` |
| GET | `/api/lobbies/{lobbyId}/notifications` | `notifications(String)` | `Collection<LobbyNotification>` |
| POST | `/api/lobbies/{lobbyId}/join` | `join(String)` | `IdResponse` |
| DELETE | `/api/lobbies/{lobbyId}` | `cancel(String)` | `IdResponse` |

**DTO** : `LobbyResponse`, `LobbyNotification` (interface polymorphe, pattern notifications §6).

---

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

| Port out | Service cible | Query Axon envoyée (QueryGateway) |
|---|---|---|
| `UserPort` | `quizup-identity` | `UserQuery.FindUserQuery` |

Implémentation : `application/service/UserService` (→ identity, `.map(User::name)`).

**Ports sortants locaux** : `LobbyRepositoryPort`, `LobbyEventStorePort`.

---

## 5. Contrats cassés / TODO

- **Aucun contrat cassé détecté** pour ce service. Le frontend
  (`web-applications/quizup-frontend/src/features/matchmaking/api/matchmaking.api.ts`) appelle
  `/matchmaking-service/api/lobbies/...` qui est correctement routé par le gateway vers ce service.

---

## 6. Patterns de référence

Ce service utilise les **sagas** et **deadlines** (pattern avancé). Voir
[`../../best-practices/hexagonal-architecture.md`](../../best-practices/hexagonal-architecture.md)
(§5 sagas/deadlines, §6 notifications WebSocket, §7 event store adapter, §8 infrastructure).
