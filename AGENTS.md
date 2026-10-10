# AGENTS.md — quizup-matchmaking

> Service de **matchmaking** : appariement public (« Défier le monde ») et salles (salle
> d'attente temps réel). Architecture : Axon Framework (CQRS/EDA) + JPA (projections). Sagas et
> deadlines pour l'appariement, l'acceptation des défis et le lancement des parties.
> Pour les règles de patterns :
> [`../../best-practices/.backend/folder-structure.md`](../../best-practices/.backend/folder-structure.md)
> et [`command-preparation.md`](../../best-practices/.backend/command-preparation.md).

---

## 1. Rôle

Trois responsabilités **distinctes**, chacune avec son agrégat :

- **Appariement public** (`MatchmakingAggregate`) : trouve un adversaire pour un sujet
  (même thème, niveau ±5, langues **en union** couvertes par le thème) ; à l'échéance de **5 s**
  sans adversaire, crée une **partie bot**. Le bot n'est jamais un participant de la salle.
- **Défi nominatif** (`ChallengeAggregate`) : intention **asynchrone** « A défie B » (TTL 1 h,
  accept/refuse/annule/expire), sans présence ni partie et **sans validation de faisabilité**
  (couverture linguistique, questions) : l'agrégat se limite aux invariants de la commande
  (identifiants, pas d'auto-défi, statut). À l'acceptation, `ChallengeSaga` crée la salle ; le lien
  est **déterministe** (`ChallengeRoomId`) et vit dans le read model, jamais dans l'agrégat.
- **Salle** (`RoomAggregate`) : deux humains, **apparition client-driven**. Le client émet
  `JoinRoomCommand` (commande unique) quand l'utilisateur est réellement dans la salle : elle porte
  la présence et, pour le second humain, l'enregistrement du participant. Aucune saga ne simule une
  action client. `RoomSaga` **prépare la partie** pendant le compte à rebours (profils + questions),
  crée la partie et, en cas d'échec, **annonce l'échec** aux joueurs présents (`RoomFailedEvent` →
  `ROOM_FAILED`). Un salon non lancé expire après **1 jour** ; une **sortie explicite est non
  destructive** et la **présence n'entre pas en jeu** (jamais de fermeture sur passage hors ligne).

### Cycle de vie (statuts réduits)

Les trois agrégats sont **éphémères** : statuts réduits au cycle de vie et suppression différée.

- `MatchmakingStatus = SEARCHING | CLOSED | FAILED` ; `RoomStatus = CREATED | CLOSED | FAILED` ;
  `ChallengeStatus = PENDING | ACCEPTED | DECLINED | CANCELLED | EXPIRED`.
- `CLOSED` = fin normale (partie créée, annulée, expirée) ; `FAILED` = échec système (préparation
  de partie impossible). L'issue exacte est portée par l'événement terminal et la notification.
- **Aucune suppression immédiate** : l'agrégat reste joignable pendant la **rétention**
  (`ROOM_PURGE` / `MATCHMAKING_PURGE` / `CHALLENGE_PURGE`, 2 min) puis la saga envoie la commande
  de purge ; `markDeleted()` est appelé dans l'`@EventSourcingHandler` du `*PurgedEvent` (standard
  Axon) et la projection supprime sa ligne.

**Package** : `io.github.quizup.matchmaking`

---

## 2. Surface (headless)

Service **headless** : aucun contrôleur REST ni WebSocket. La surface applicative unique est le
**`quizup-bff`** (`/api/**` + `/ws`) ; il interroge ce service via le **query bus** Axon et consomme
ses événements. Handlers de commande/requête, sagas et projections sont la seule surface exposée.

**Paradigme** : aucun port externe n'est interrogé dans un `@CommandHandler`
([`command-preparation.md`](../../best-practices/.backend/command-preparation.md)). Les agrégats
sont purs (validations `requireXxx` + `apply`) ; les I/O inter-services vivent dans les **sagas**
(fenêtres d'attente) et les adaptations (`application/service`).

## 3. Commandes / queries (bus) et sagas

- `MatchmakingCommand` : `CreateMatchmakingCommand`, `CancelMatchmakingCommand`,
  `MarkMatchmakingMatchedCommand`, `FailMatchmakingCommand`, `PurgeMatchmakingCommand`.
- `ChallengeCommand` : `CreateChallengeCommand`, `Accept/Decline/Cancel/Expire/Purge`.
- `RoomCommand` : `CreateRoomCommand`, `JoinRoomCommand` (apparition), `LeaveRoomCommand`,
  `CancelRoomCommand`, `CompleteRoomCommand`, `FailRoomCommand`, `ExpireRoomCommand`,
  `PurgeRoomCommand`.
- `MatchmakingQuery`, `ChallengeQuery`, `RoomQuery` (`GetRoomById`, `GetMyOpenRooms`,
  `GetRoomEventsQuery`).

## 4. Dépendances inter-services

| Port / service sortant    | Service cible     | Query Axon envoyée (QueryGateway)          |
|---------------------------|-------------------|--------------------------------------------|
| `ProfileRepositoryPort`   | `quizup-profile`  | `ProfileQuery.GetProfileQuery` + `ProgressionQuery.GetProgressionQuery` |
| `MatchmakingPlayerPort`   | `quizup-profile`  | `ProfileQuery.GetProfileQuery` + `ProgressionQuery.GetProgressionQuery` |
| `TopicAvailabilityPort`   | `quizup-theme`    | `QuestionQuery.CountApprovedQuestionsByTopicAndLanguagesQuery` |
| `QuestionDrawService`     | `quizup-theme`    | `QuestionQuery.GetRandomApprovedQuestionsQuery` (mappée en `GameQuestion`) |

Implémentations : `application/service/MatchmakingPlayerService` (nom + niveau + XP + pays),
`TopicAvailabilityService` (couverture des langues, filtre de candidats),
`QuestionDrawService` (tirage strict `ORDER BY RANDOM()` dans toutes les langues + mapping
`theme.Question → game.GameQuestion`, dupliqué par émetteur), `PlayerService` (profil salle).

**Ports sortants locaux** : `MatchmakingPoolPort`, `MatchmakingRepositoryPort`,
`MatchmakingEventStorePort`, `RoomRepositoryPort`, `RoomEventStorePort`, `ChallengeRepositoryPort`.

### Défi nominatif (`ChallengeSaga`)

- `ChallengeCreatedEvent` → expiration **1 h** ; chaque terminal (`Accepted`, `Declined`, `Cancelled`,
  `Expired`) → purge planifiée (rétention **2 min**, `CHALLENGE_PURGE`) ;
  `ChallengePurgedEvent` termine la saga (`markDeleted`).
- `ChallengeAcceptedEvent` → **création de la salle** `CreateRoomCommand(ChallengeRoomId.of(...))`
  (one-shot, flag `roomRequested` anti-rejeu). La saga **ne joint personne** : l'apparition est
  client-driven.
- `ChallengePurgeSweeper` : balayeur périodique (60 s) filet de sécurité pour les défis terminaux
  plus vieux que rétention + marge (10 min).

### Appariement public (`MatchmakingSaga`)

- `MatchmakingStartedEvent` → planifie une deadline **5 s** ; **le dernier arrivé** initie le
  pairage : `MatchmakingPoolPort.candidates` (même sujet, niveau ±5, plus anciens), filtre par
  langues (`TopicAvailabilityPort`), `claim` atomique, **tirage des questions**
  (`QuestionDrawService`), puis `CreateGameCommand(HUMAN/HUMAN, questions)` et
  `MarkMatchmakingMatchedCommand` des deux tickets.
- Deadline atteinte sans adversaire → tirage des questions → `CreateGameCommand(BOT, questions)` +
  `MarkMatchmakingMatchedCommand(vsBot=true)`.
- Échec de préparation/création → `FailMatchmakingCommand` (les deux tickets).

### Salle (`RoomSaga`)

- `RoomCreatedEvent` → expiration planifiée (**1 jour**).
- `RoomEnteredEvent` (apparition client) → mémorise le participant (non-initiateur).
- `RoomAllPlayersPresentEvent` → **prefetch** profils + questions pendant le compte à rebours
  (**3 s**, `ROOM_READY_CHECK`) ; questions insuffisantes → `FailRoomCommand` immédiat
  (`TOPIC_NOT_AVAILABLE_IN_LANGUAGE`).
- Fin du compte à rebours → `CreateGameCommand(HUMAN/HUMAN, questions)` **attendu** (`sendAndWait`)
  puis `CompleteRoomCommand` ; échec → `FailRoomCommand` (`GAME_CREATION_FAILED`).
- `RoomLeftEvent` (sortie non destructive) → compte à rebours annulé ; le joueur peut revenir.
- `RoomCancelled|Expired|Failed|Completed` → `CLOSED`/`FAILED` + purge planifiée (rétention 2 min) ;
  `RoomPurgedEvent` termine la saga (`markDeleted`).

## 5. Contrats BFF

- **Appariement** : `POST /api/matchmaking/tickets` (file) ; `GET /{ticketId}` ;
  `POST /{ticketId}/cancel` ; `GET /{ticketId}/notifications` ; WS
  `/topic/matchmaking/tickets/{ticketId}` (`SEARCHING|MATCHED|CANCELLED|FAILED`).
- **Défis** : `POST /api/challenges { topicId, opponentId }` ; `GET /{challengeId}` ;
  `GET /mine` ; `POST /{id}/accept|decline|cancel` — `ChallengeView.roomId` dès l'acceptation
  (dérivé, déterministe).
- **Salles** : `POST /api/rooms { topicId }` (salle partagée) ; `GET /{roomId}` ;
  `GET /mine` (initiateur **ou** invité) ; `POST /{roomId}/join` (**apparition unique**) ;
  `POST /{roomId}/leave|cancel` ; `GET /{roomId}/notifications` ; WS
  `/topic/rooms/{roomId}` (`ROOM_CREATED|ROOM_ENTERED|ROOM_ALL_PRESENT|ROOM_LEFT|ROOM_COMPLETED|ROOM_CANCELLED|ROOM_EXPIRED|ROOM_FAILED`).
- **Notifications personnelles** : consommées par `quizup-notification` depuis le bus Kafka
  (`CHALLENGE_RECEIVED`, `CHALLENGE_DECLINED`, `ROOM_ACCEPTED`) ; poussées par le BFF sur
  `/topic/notifications/{userId}`.
- Read models : `matchmaking_entry` (pool + ticket), `challenge_entry`, `room_entry`. L'agrégat
  reste la source de vérité ; le pool est reconstructible à partir des `MatchmakingStartedEvent`.
