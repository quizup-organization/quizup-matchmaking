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
  (même thème, niveau ±5, langues **en union** couvertes par le thème) ; à l'échéance de **10 s**
  sans adversaire, le ticket **expire** (`EXPIRED`) — **aucun repli bot automatique** : le client
  propose de relancer une recherche ou de basculer explicitement sur un **entraînement** contre un
  robot (duel **sans XP**, la progression ne
  vient que des duels entre joueurs, cf. `quizup-profile`). Le matchmaking **ne prépare jamais la
  partie** : il crée
  une salle (nominative ou bot) et marque les tickets. L'agrégat ne conserve que ce dont ses
  handlers ont besoin (identifiant, joueur, statut, salle) ; les critères d'appariement (sujet,
  niveau, langues), la **photo de couverture linguistique du sujet** (`TopicLanguageCoverage`,
  préparée par la façade) et l'issue (adversaire, vsBot) vivent dans les événements (saga + pool
  + BFF).
- **Défi nominatif** (`ChallengeAggregate`) : intention **asynchrone** « A défie B » (TTL 1 h,
  accept/refuse/annule/expire), sans présence ni partie et **sans validation de faisabilité**
  (couverture linguistique, questions) : l'agrégat se limite aux invariants de la commande
  (identifiants, pas d'auto-défi, statut). À l'acceptation, `ChallengeSaga` crée la salle ; le lien
  est **déterministe** (`ChallengeRoomId`) et vit dans le read model, jamais dans l'agrégat.
- **Salle** (`RoomAggregate`) : deux humains (salle partagée ou appariement) **ou** un humain
  contre le bot, **apparition client-driven**. Le client émet `JoinRoomCommand` (commande unique)
  quand l'utilisateur est réellement dans la salle : elle porte la présence et, pour le second
  joueur, l'enregistrement du participant, **avec le snapshot de profil `RoomPlayer` préparé par la
  façade** (le client ne le fournit jamais ; voir `command-preparation.md`). Seule exception
  système : le bot est présent **dès la
  création** d'une salle `vsBot` (présence appliquée par l'agrégat, aucun join système). `RoomSaga`
  est le **seul créateur de partie** : elle
  prépare la partie dès les deux joueurs présents (**vérification du thème n°2** : profils portés
  par les événements d'apparition + historique et tirage via des **requêtes non bloquantes**) et la
  **crée immédiatement en fin de préparation** (aucun compte à rebours de
  lancement), avance le statut de préparation et, en cas d'échec, **annonce l'échec** aux
  joueurs présents (`RoomFailedEvent` → `ROOM_FAILED`). Un salon non lancé expire après **1 jour** ;
  tant que la préparation n'est pas engagée, une **sortie explicite est non destructive** (le
  joueur peut revenir) ; dès que les deux sont présents, la sortie est refusée — l'initiateur peut
  toujours **annuler**. Un joueur présent peut **relancer** (« wizz ») l'autre joueur humain
  **absent** pour l'inviter à rejoindre la salle : pur temps réel (aucune préparation déclenchée),
  cadence **10 s par joueur** (`RoomWizzRules`), jamais de cible bot — la relance nourrit la
  notification personnelle de la cible (`WIZZ`, cf. `quizup-notification`) et le temps réel de la
  salle (`ROOM_WIZZED`).

### Cycle de vie (statuts)

- `MatchmakingStatus = SEARCHING | COMPLETED | CANCELLED | EXPIRED | FAILED` ;
  `ChallengeStatus = PENDING | ACCEPTED | DECLINED | CANCELLED | EXPIRED`.
- `RoomStatus = WAITING_PLAYERS | FETCHING_PLAYERS | FETCHING_PLAYED_QUESTIONS | FETCHING_QUESTIONS |
  CREATING_GAME | GAME_CREATED | COMPLETED | CANCELLED | EXPIRED | FAILED` : le statut suit
  l'**avancement de la préparation** et est poussé au client (WS + `RoomView`).
  - `WAITING_PLAYERS` → `ALL_PRESENT` → `FETCHING_PLAYERS` → `MarkRoomPlayersLoadedCommand` →
    `FETCHING_PLAYED_QUESTIONS` → `MarkRoomPlayedQuestionsLoadedCommand` → `FETCHING_QUESTIONS`
    (tirage filtré : jamais-jouées + difficulté déduite) → `MarkRoomQuestionsLoadedCommand` →
    `CREATING_GAME` (demande de création de la partie, fire-and-forget) →
    `MarkRoomGameCreatedCommand` → `GAME_CREATED` (accusé du service game) →
    `CompleteRoomCommand` → `COMPLETED`.
  - `CANCELLED` = annulée par l'initiateur ; `EXPIRED` = jamais lancée, expirée ;
    `FAILED` = échec de préparation, accusé de création absent (`GAME_CREATION_TIMEOUT`) ou
    création impossible.
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
service applicatif injecté, jamais un type d'un autre `*-domain` — et **jamais en bloquant** : les
requêtes renvoient des `CompletableFuture` dont le callback envoie une commande
([`anti-patterns.md`](../../best-practices/.backend/anti-patterns.md) § 1).

## 3. Commandes / queries (bus) et sagas

- `MatchmakingCommand` : `CreateMatchmakingCommand` (+ `TopicLanguageCoverage` préparée par la
  façade), `CancelMatchmakingCommand`,
  `MarkMatchmakingMatchedCommand` (roomId), `FailMatchmakingCommand`, `PurgeMatchmakingCommand`.
- `ChallengeCommand` : `CreateChallengeCommand`, `Accept/Decline/Cancel/Expire/Purge`, `Start`
  (commande interne de saga : la partie du défi a été créée).
- `ChallengeEvent` : `ChallengeCreatedEvent`, `ChallengeAcceptedEvent`, `ChallengeDeclinedEvent`,
  `ChallengeCancelledEvent`, `ChallengeExpiredEvent`, `ChallengePurgedEvent` et
  **`ChallengeStartedEvent`** (`challengeId`, `challengerId`, `opponentId`, `gameId`) — le défi
  **a abouti sur une partie** (distinct de l'acceptation, qui ne garantit pas la présence de
  l'invité). Consommé par `quizup-profile` (badge **Défis**) ; non exposé par le BFF.
- `RoomCommand` : `CreateRoomCommand` (`vsBot`, `botDifficulty`), `JoinRoomCommand` (apparition,
  + snapshot `RoomPlayer`),
  `LeaveRoomCommand` (refusée après engagement), `CancelRoomCommand` (toujours possible tant que
  non terminale), `WizzRoomCommand` (relance d'un adversaire absent — émetteur **présent**,
  cible **humaine absente**, cadence 10 s par joueur ; refusée sinon, ignorée en préparation
  engagée/salle terminale),
  `MarkRoomPlayersLoadedCommand`, `MarkRoomPlayedQuestionsLoadedCommand`
  (+ `Set<String>` des questions jouées), `MarkRoomQuestionsLoadedCommand` (+ `List<DrawnQuestion>`),
  `MarkRoomGameCreatedCommand` (accusé du service game),
  `CompleteRoomCommand`, `FailRoomCommand`, `ExpireRoomCommand`, `PurgeRoomCommand`.
- `MatchmakingEvent.MatchmakingStartedEvent` porte `TopicLanguageCoverage`.
- `RoomEvent` : `RoomCreatedEvent` (`vsBot`, `botDifficulty`), `RoomEnteredEvent` (+ snapshot
  `RoomPlayer`, `null` pour le bot), `RoomAllPlayersPresentEvent`, `RoomPlayersLoadedEvent`,
  `RoomPlayedQuestionsLoadedEvent` (+ ids joués), `RoomQuestionsLoadedEvent` (+ `DrawnQuestion`),
  `RoomGameCreatedEvent`, `RoomLeftEvent`, `RoomWizzedEvent` (émetteur, cible, instant — pur temps
  réel, aucun effet de préparation), `RoomCancelledEvent`,
  `RoomCompletedEvent`, `RoomFailedEvent`, `RoomExpiredEvent`, `RoomPurgedEvent`.
- `MatchmakingQuery`, `ChallengeQuery`, `RoomQuery` (`GetRoomById`, `GetMyOpenRooms`,
  `GetRoomEventsQuery`).

## 4. Dépendances inter-services

| Port sortant              | Service cible     | Query Axon envoyée (QueryGateway, **non bloquante**)          |
|---------------------------|-------------------|--------------------------------------------|
| `PlayedQuestionsPort`     | `quizup-game`     | `GameQuery.GetPlayedQuestionIdsQuery` (questions déjà jouées, adaptée en `Set<String>` local, renvoyée en `CompletableFuture`) |
| `QuestionDrawPort`        | `quizup-theme`    | `QuestionQuery.GetRandomApprovedQuestionsQuery` (préférences jamais-jouées + difficulté cible, adaptée en `DrawnQuestion` local, renvoyée en `CompletableFuture`) |

Implémentations : `application/service/PlayedQuestionsService` (historique des questions jouées,
`Set<String>` local, **sans `.join()`**),
`QuestionDrawService` (adaptateur de `QuestionDrawPort` : tirage strict sur les langues avec
préférences de tri best-effort + mapping `theme.Question → DrawnQuestion` local, **sans `.join()`** ;
la traduction vers `game.GameQuestion` est faite par la saga émettrice elle-même, comme le préconise
`command-preparation.md`).

> **Aucun port bloquant dans une saga** : les profils sont portés par les événements d'apparition
> (préparés par la façade), la couverture linguistique de l'appariement est portée par
> `MatchmakingStartedEvent`, et les requêtes historique/tirage sont non bloquantes (callback →
> commande → événement). Voir
> [`anti-patterns.md`](../../best-practices/.backend/anti-patterns.md) § 1.

**Ports sortants locaux** : `MatchmakingPoolPort`, `MatchmakingRepositoryPort`,
`MatchmakingEventStorePort`, `RoomRepositoryPort`, `RoomEventStorePort`, `ChallengeRepositoryPort`.

### Défi nominatif (`ChallengeSaga`)

- `ChallengeCreatedEvent` → expiration **1 h** ; chaque terminal (`Accepted`, `Declined`, `Cancelled`,
  `Expired`) → purge planifiée (rétention **2 min**, `CHALLENGE_PURGE`) ;
  `ChallengePurgedEvent` termine la saga (`markDeleted`).
- `ChallengeAcceptedEvent` → **création de la salle** `CreateRoomCommand(ChallengeRoomId.of(...))`
  (one-shot, flag `roomRequested` anti-rejeu). La saga **s'associe à cette salle** pour savoir
  quand le défi **aboutit** : `RoomGameCreatedEvent` de la salle déterministe →
  `StartChallengeCommand(challengeId, gameId)` → `ChallengeStartedEvent` (consommé par
  `quizup-profile` pour le badge Défis : **un défi ne compte que s'il a été accepté et a produit
  une partie**). La saga **ne joint personne** : l'apparition est
  client-driven.
- `ChallengePurgeSweeper` : balayeur périodique (60 s) filet de sécurité pour les défis terminaux
  plus vieux que rétention + marge (10 min).

### Appariement public (`MatchmakingSaga`)

- `MatchmakingStartedEvent` → porte `deadlineAt` (fin de la fenêtre de recherche, exposée au
  client pour le compte à rebours) **et la photo de couverture linguistique du sujet**
  (`TopicLanguageCoverage`, préparée par la façade) ; planifie une deadline **10 s** ;
  **le dernier arrivé** initie le
  pairage : `MatchmakingPoolPort.candidates` (même sujet, niveau ±5, plus anciens), filtre par
  langues **local** (`topicCoverage.approvedCountFor(union) >= GameRules.TOTAL_ROUNDS`,
  **vérification du thème n°1**, aucune query bloquante), `claim` atomique, puis
  `CreateRoomCommand` (salle nominative, opponentId = candidat) et `MarkMatchmakingMatchedCommand`
  des deux tickets.
- Deadline atteinte sans adversaire → `ExpireMatchmakingCommand` (`EXPIRED`) : **aucune salle**
  n'est créée. Le client propose alors de relancer une recherche ou de basculer sur un
  entraînement contre un robot (`POST /api/rooms/bot`).
- Création de salle **fire-and-forget** (`send`, comme `ChallengeSaga`) : la salle est l'unique
  point d'échec (`RoomFailedEvent` → `ROOM_FAILED`). Les exceptions synchrones des ports (pool) sont
  converties par l'`@ExceptionHandler` de la saga en
  `FailMatchmakingCommand(matchmakingId, "MATCHMAKING_ERROR")`.

### Salle (`RoomSaga`)

- `RoomCreatedEvent` → expiration planifiée (**1 jour**). La présence du bot (`vsBot`) est portée
  par l'agrégat (événement d'apparition appliqué à la création) : aucun join système côté saga.
- `RoomEnteredEvent` (apparition client) → mémorise le participant **et les snapshots de profil
  `RoomPlayer`** (préparés par la façade) : aucune I/O de la saga pour résoudre les joueurs.
- `RoomAllPlayersPresentEvent` → les profils sont connus, simple avancement de statut
  (`MarkRoomPlayersLoadedCommand`) ; `RoomPlayersLoadedEvent` → étape
  **historique des questions jouées** (`PlayedQuestionsPort`, quizup-game, joueurs humains
  uniquement, **requête non bloquante**) ; `RoomPlayedQuestionsLoadedEvent` → étape **questions** :
  tirage filtré
  (`QuestionDrawPort`, **requête non bloquante**) avec préférences jamais-jouées + difficulté cible
  déduite du niveau moyen
  des joueurs (`QuestionLevelRules` : ≤9 EASY, 10–19 MEDIUM, 20–39 HARD, ≥40 EXPERT) ; chaque étape
  avance le statut de la salle. Chaque callback envoie une commande (`Mark*` portant le résultat,
  ou `FailRoomCommand`) — jamais de mutation de la saga depuis le callback ; timeout d'étape
  (`ROOM_PREPARATION_STEP_TIMEOUT`, 10 s) → `FailRoomCommand("ROOM_PREPARATION_FAILED")`.
  Questions insuffisantes → `FailRoomCommand` immédiat
  (`TOPIC_NOT_AVAILABLE_IN_LANGUAGE`).
- `RoomQuestionsLoadedEvent` → la saga envoie `CreateGameCommand` en **fire-and-forget**
  (HUMAN/HUMAN ou HUMAN/BOT, commande assemblée par `GamePreparationService` — le mapping local →
  `game.GameQuestion` ne vit pas dans la saga) et s'associe au `gameId` ; `GameCreatedEvent` →
  `MarkRoomGameCreatedCommand` → `GAME_CREATED`, puis `CompleteRoomCommand`. Accusé absent sous
  **30 s** → `FailRoomCommand("GAME_CREATION_TIMEOUT")` ; exceptions synchrones (mapping, envoi) →
  `FailRoomCommand("ROOM_PREPARATION_FAILED")` (`@ExceptionHandler`).
- `RoomLeftEvent` (sortie non destructive avant engagement) → la salle reste ouverte, le joueur
  peut revenir. L'agrégat refuse la sortie ensuite (`RoomPreparationStartedProblem`).
- `RoomCancelled|Expired|Failed|Completed` → `CANCELLED`/`EXPIRED`/`FAILED`/`COMPLETED` + purge planifiée
  (rétention 2 min) ; `RoomPurgedEvent` termine la saga (`markDeleted`).

## 5. Contrats BFF

- **Appariement** : `POST /api/matchmaking/tickets` (file) ; `GET /{ticketId}` ;
  `POST /{ticketId}/cancel` ; `GET /{ticketId}/notifications` ; WS
  `/topic/matchmaking/tickets/{ticketId}` (`SEARCHING|MATCHED|CANCELLED|EXPIRED|FAILED`) — `MATCHED` porte
  le `roomId` de la salle créée.
- **Défis** : `POST /api/challenges { topicId, opponentId, gameId? }` — `gameId` référence la
  **partie terminée d'origine** d'une revanche (validée côté BFF : participation, duel humain,
  adversaire) ; `GET /{challengeId}` / `GET /mine` exposent `ChallengeView.gameId` ;
  `POST /{id}/accept|decline|cancel` — `ChallengeView.roomId` dès l'acceptation (dérivé, déterministe).
- **Salles** : `POST /api/rooms { topicId }` (partagée) ; `POST /api/rooms/bot
  { topicId, difficulty? }` (le bot rejoint automatiquement) ; `GET /{roomId}` ;
  `GET /mine` (initiateur **ou** invité) ; `POST /{roomId}/join` (**apparition unique**) ;
  `POST /{roomId}/leave` (refusée après engagement) | `cancel` (initiateur, toujours possible) |
  `wizz` (relance de l'adversaire humain absent — pur temps réel, cadence 10 s par joueur) ;
  `GET /{roomId}/notifications` ; WS `/topic/rooms/{roomId}`
  (`ROOM_CREATED|ROOM_ENTERED|ROOM_ALL_PRESENT|ROOM_PLAYERS_LOADED|ROOM_PLAYED_QUESTIONS_LOADED|ROOM_QUESTIONS_LOADED|ROOM_GAME_CREATED|ROOM_LEFT|ROOM_WIZZED|ROOM_COMPLETED|ROOM_CANCELLED|ROOM_EXPIRED|ROOM_FAILED`).
- **Notifications personnelles** : consommées par `quizup-notification` depuis le bus Kafka
  (`CHALLENGE_RECEIVED`, `CHALLENGE_DECLINED`, `ROOM_ACCEPTED`, `WIZZ`) ; poussées par le BFF sur
  `/topic/notifications/{userId}`.
- Read models : `matchmaking_entry` (pool + ticket, colonnes `room_id` et `deadline_at`),
  `challenge_entry` (`game_id` = partie d'origine de la revanche, `room_id`),
  `room_entry` (`vs_bot`, avancement). L'agrégat reste la source de vérité ; le pool est
  reconstructible à partir des `MatchmakingStartedEvent`.
- **Data prod** : les breaking changes des contrats sont accompagnés d'un **reset** de la data
  (schéma recréé, pas de migration de compatibilité).
