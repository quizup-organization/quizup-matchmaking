# AGENTS.md — quizup-matchmaking

> Service de **matchmaking** : appariement public (« Défier le monde »), défis nominatifs et
> **salles** (salle d'attente temps réel). Architecture : Axon Framework (CQRS/EDA) + JPA
> (projections). Sagas et deadlines pour l'appariement, l'acceptation des défis et la préparation
> des parties.
> Pour les règles de patterns :
> [`../../best-practices/.backend/folder-structure.md`](../../best-practices/.backend/folder-structure.md)
> et [`command-preparation.md`](../../best-practices/.backend/command-preparation.md).

---

## 1. Rôle

Trois responsabilités **distinctes**, chacune avec son agrégat :

- **Appariement public** (`MatchmakingAggregate`) : trouve un adversaire pour un sujet
  (même thème, niveau ±5, langues **en union** couvertes par le thème) ; à l'échéance de **5 s**
  sans adversaire, crée une **salle bot**. Le matchmaking **ne prépare jamais la partie** : il crée
  une salle (nominative ou bot) et marque les tickets. L'agrégat ne conserve que ce dont ses
  handlers ont besoin (identifiant, joueur, statut, salle) ; les critères d'appariement (sujet,
  niveau, langues) et l'issue (adversaire, vsBot) vivent dans les événements (saga + pool + BFF).
- **Défi nominatif** (`ChallengeAggregate`) : intention **asynchrone** « A défie B » (TTL 1 h,
  accept/refuse/annule/expire), sans présence ni partie et **sans validation de faisabilité**
  (couverture linguistique, questions) : l'agrégat se limite aux invariants de la commande
  (identifiants, pas d'auto-défi, statut). À l'acceptation, `ChallengeSaga` crée la salle ; le lien
  est **déterministe** (`ChallengeRoomId`) et vit dans le read model, jamais dans l'agrégat.
- **Salle** (`RoomAggregate`) : deux humains (salle partagée ou appariement) **ou** un humain
  contre le bot, **apparition client-driven**. Le client émet `JoinRoomCommand` (commande unique)
  quand l'utilisateur est réellement dans la salle : elle porte la présence et, pour le second
  joueur, l'enregistrement du participant. Seule exception système : le bot est présent **dès la
  création** d'une salle `vsBot` (présence appliquée par l'agrégat, aucun join système). `RoomSaga`
  est le **seul créateur de partie** : elle
  prépare la partie dès les deux joueurs présents (**vérification du thème n°2** : profils + tirage
  des questions) et la **crée immédiatement en fin de préparation** (aucun compte à rebours de
  lancement), avance le statut de préparation et, en cas d'échec, **annonce l'échec** aux
  joueurs présents (`RoomFailedEvent` → `ROOM_FAILED`). Un salon non lancé expire après **1 jour** ;
  tant que la préparation n'est pas engagée, une **sortie explicite est non destructive** (le
  joueur peut revenir) ; dès que les deux sont présents, la sortie est refusée — l'initiateur peut
  toujours **annuler**.

### Cycle de vie (statuts)

- `MatchmakingStatus = SEARCHING | COMPLETED | CANCELLED | FAILED` ;
  `ChallengeStatus = PENDING | ACCEPTED | DECLINED | CANCELLED | EXPIRED`.
- `RoomStatus = WAITING_PLAYERS | FETCHING_PLAYERS | FETCHING_QUESTIONS | CREATING_GAME |
  COMPLETED | CANCELLED | EXPIRED | FAILED` : le statut suit l'**avancement de la préparation** et est poussé au
  client (WS + `RoomView`).
  - `WAITING_PLAYERS` → `ALL_PRESENT` → `FETCHING_PLAYERS` → `MarkRoomPlayersLoadedCommand` →
    `FETCHING_QUESTIONS` → `MarkRoomQuestionsLoadedCommand` → `CREATING_GAME` (création immédiate
    de la partie) → `CompleteRoomCommand` → `COMPLETED`.
  - `CANCELLED` = annulée par l'initiateur ; `EXPIRED` = jamais lancée, expirée ;
    `FAILED` = échec de préparation/création.
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
(fenêtres d'attente) via des **ports sortants** (`domain/port/out`) à **types locaux** — jamais un
service applicatif injecté, jamais un type d'un autre `*-domain`.

## 3. Commandes / queries (bus) et sagas

- `MatchmakingCommand` : `CreateMatchmakingCommand`, `CancelMatchmakingCommand`,
  `MarkMatchmakingMatchedCommand` (roomId), `FailMatchmakingCommand`, `PurgeMatchmakingCommand`.
- `ChallengeCommand` : `CreateChallengeCommand`, `Accept/Decline/Cancel/Expire/Purge`.
- `RoomCommand` : `CreateRoomCommand` (`vsBot`, `botDifficulty`), `JoinRoomCommand` (apparition),
  `LeaveRoomCommand` (refusée après engagement), `CancelRoomCommand` (toujours possible tant que
  non terminale), `MarkRoomPlayersLoadedCommand`, `MarkRoomQuestionsLoadedCommand`,
  `CompleteRoomCommand`, `FailRoomCommand`, `ExpireRoomCommand`, `PurgeRoomCommand`.
- `RoomEvent` : `RoomCreatedEvent` (`vsBot`, `botDifficulty`), `RoomEnteredEvent`,
  `RoomAllPlayersPresentEvent`, `RoomPlayersLoadedEvent`, `RoomQuestionsLoadedEvent`,
  `RoomLeftEvent`, `RoomCancelledEvent`, `RoomCompletedEvent`, `RoomFailedEvent`,
  `RoomExpiredEvent`, `RoomPurgedEvent`.
- `MatchmakingQuery`, `ChallengeQuery`, `RoomQuery` (`GetRoomById`, `GetMyOpenRooms`,
  `GetRoomEventsQuery`).

## 4. Dépendances inter-services

| Port sortant              | Service cible     | Query Axon envoyée (QueryGateway)          |
|---------------------------|-------------------|--------------------------------------------|
| `ProfileRepositoryPort`   | `quizup-profile`  | `ProfileQuery.GetProfileQuery` + `ProgressionQuery.GetProgressionQuery` |
| `TopicAvailabilityPort`   | `quizup-theme`    | `QuestionQuery.CountApprovedQuestionsByTopicAndLanguagesQuery` |
| `QuestionDrawPort`        | `quizup-theme`    | `QuestionQuery.GetRandomApprovedQuestionsQuery` (adaptée en `DrawnQuestion` local) |

Implémentations : `application/service/PlayerService` (profil salle + progression),
`TopicAvailabilityService` (couverture des langues, filtre de candidats),
`QuestionDrawService` (adaptateur de `QuestionDrawPort` : tirage strict `ORDER BY RANDOM()` dans
toutes les langues + mapping `theme.Question → DrawnQuestion` local ; la traduction vers
`game.GameQuestion` est faite par la saga émettrice elle-même, comme le préconise
`command-preparation.md`).

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

- `MatchmakingStartedEvent` → porte `deadlineAt` (fin de recherche, repli bot) et planifie une
  deadline **5 s** ; **le dernier arrivé** initie le
  pairage : `MatchmakingPoolPort.candidates` (même sujet, niveau ±5, plus anciens), filtre par
  langues (`TopicAvailabilityPort`, **vérification du thème n°1**), `claim` atomique, puis
  `CreateRoomCommand` (salle nominative, opponentId = candidat) et `MarkMatchmakingMatchedCommand`
  des deux tickets.
- Deadline atteinte sans adversaire → `CreateRoomCommand(vsBot=true, opponentId = SYSTEM_USER_ID)`
  (le bot est déjà présent dans la salle créée) + `MarkMatchmakingMatchedCommand(vsBot=true)`.
- Échec de création de salle → `FailMatchmakingCommand` (les deux tickets, `ROOM_CREATION_FAILED`).

### Salle (`RoomSaga`)

- `RoomCreatedEvent` → expiration planifiée (**1 jour**). La présence du bot (`vsBot`) est portée
  par l'agrégat (événement d'apparition appliqué à la création) : aucun join système côté saga.
- `RoomEnteredEvent` (apparition client) → mémorise le participant (non-initiateur).
- `RoomAllPlayersPresentEvent` → démarre la **préparation** : étape profils puis étape questions
  (vérification du sujet n°2) ; chaque étape avance le statut de la salle. Questions
  insuffisantes → `FailRoomCommand` immédiat (`TOPIC_NOT_AVAILABLE_IN_LANGUAGE`).
- `RoomQuestionsLoadedEvent` → **fin de préparation** : `CreateGameCommand` **attendu**
  (`sendAndWait`, HUMAN/HUMAN ou HUMAN/BOT, mapping local → `game.GameQuestion` dans la saga) puis
  `CompleteRoomCommand` ; échec → `FailRoomCommand("GAME_CREATION_FAILED")`.
- `RoomLeftEvent` (sortie non destructive avant engagement) → la salle reste ouverte, le joueur
  peut revenir. L'agrégat refuse la sortie ensuite (`RoomPreparationStartedProblem`).
- `RoomCancelled|Expired|Failed|Completed` → `CANCELLED`/`EXPIRED`/`FAILED`/`COMPLETED` + purge planifiée
  (rétention 2 min) ; `RoomPurgedEvent` termine la saga (`markDeleted`).

## 5. Contrats BFF

- **Appariement** : `POST /api/matchmaking/tickets` (file) ; `GET /{ticketId}` ;
  `POST /{ticketId}/cancel` ; `GET /{ticketId}/notifications` ; WS
  `/topic/matchmaking/tickets/{ticketId}` (`SEARCHING|MATCHED|CANCELLED|FAILED`) — `MATCHED` porte
  le `roomId` de la salle créée.
- **Défis** : `POST /api/challenges { topicId, opponentId, gameId? }` — `gameId` référence la
  **partie terminée d'origine** d'une revanche (validée côté BFF : participation, duel humain,
  adversaire) ; `GET /{challengeId}` / `GET /mine` exposent `ChallengeView.gameId` ;
  `POST /{id}/accept|decline|cancel` — `ChallengeView.roomId` dès l'acceptation (dérivé, déterministe).
- **Salles** : `POST /api/rooms { topicId }` (partagée) ; `POST /api/rooms/bot
  { topicId, difficulty? }` (le bot rejoint automatiquement) ; `GET /{roomId}` ;
  `GET /mine` (initiateur **ou** invité) ; `POST /{roomId}/join` (**apparition unique**) ;
  `POST /{roomId}/leave` (refusée après engagement) | `cancel` (initiateur, toujours possible) ;
  `GET /{roomId}/notifications` ; WS `/topic/rooms/{roomId}`
  (`ROOM_CREATED|ROOM_ENTERED|ROOM_ALL_PRESENT|ROOM_PLAYERS_LOADED|ROOM_QUESTIONS_LOADED|ROOM_LEFT|ROOM_COMPLETED|ROOM_CANCELLED|ROOM_EXPIRED|ROOM_FAILED`).
- **Notifications personnelles** : consommées par `quizup-notification` depuis le bus Kafka
  (`CHALLENGE_RECEIVED`, `CHALLENGE_DECLINED`, `ROOM_ACCEPTED`) ; poussées par le BFF sur
  `/topic/notifications/{userId}`.
- Read models : `matchmaking_entry` (pool + ticket, colonnes `room_id` et `deadline_at`),
  `challenge_entry` (`game_id` = partie d'origine de la revanche, `room_id`),
  `room_entry` (`vs_bot`, avancement). L'agrégat reste la source de vérité ; le pool est
  reconstructible à partir des `MatchmakingStartedEvent`.
- **Data prod** : les breaking changes des contrats sont accompagnés d'un **reset** de la data
  (schéma recréé, pas de migration de compatibilité).
