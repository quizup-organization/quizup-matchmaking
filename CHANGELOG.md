## [5.0.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.5.1...v5.0.0) (2026-10-08)

### ⚠ BREAKING CHANGES

* **matchmaking:** LinkChallengeRoomCommand et ChallengeNotAcceptedProblem
sont supprimes du contrat quizup-matchmaking-domain.

### Features

* **matchmaking:** purge des defis et roomId derive hors de l'agregat ([d570d54](https://github.com/quizup-organization/quizup-matchmaking/commit/d570d54873d1f915680977c92e8fb8f527197116))

## [4.5.1](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.5.0...v4.5.1) (2026-10-07)

### Bug Fixes

* **deps:** pin versions publiées (game 5.2.0, profile 3.1.0, theme 4.5.2) ([aa2c816](https://github.com/quizup-organization/quizup-matchmaking/commit/aa2c8162a39a44c09ad2ea59a769f9a489b2fe57))

### Reverts

* Revert "feat(matchmaking): lobby, challenge et timers en redis (store chaud, ready-check durable)" ([010e8bb](https://github.com/quizup-organization/quizup-matchmaking/commit/010e8bb0f7104b4928bcbde3c0e5e430b94d5481))
* Revert "feat(matchmaking): tickets et appariement public en redis (store chaud + outbox)" ([ea3a2ff](https://github.com/quizup-organization/quizup-matchmaking/commit/ea3a2ffcbf79bdde6021e89419c3e46fdf376f75))

## [4.5.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.4.0...v4.5.0) (2026-10-07)

### Features

* **matchmaking:** lobby, challenge et timers en redis (store chaud, ready-check durable) ([bc0383f](https://github.com/quizup-organization/quizup-matchmaking/commit/bc0383fe828691b3a90bc41f11d9e514cb71ab1e))

## [4.4.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.3.0...v4.4.0) (2026-10-07)

### Features

* **matchmaking:** tickets et appariement public en redis (store chaud + outbox) ([6a7a15b](https://github.com/quizup-organization/quizup-matchmaking/commit/6a7a15b87802566390be57d46cbd46f07ee997ec))

## [4.3.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.2.2...v4.3.0) (2026-10-05)

### Features

* **matchmaking:** sortie non destructive et expiration du salon a 1 jour ([cb8477e](https://github.com/quizup-organization/quizup-matchmaking/commit/cb8477eed5db09403fe362e6bed3b2e31625accc))

## [4.2.2](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.2.1...v4.2.2) (2026-10-05)

### Bug Fixes

* **matchmaking:** pin theme-domain 4.1.0 (resolution transitoire 4.0.0) ([e6ce6cd](https://github.com/quizup-organization/quizup-matchmaking/commit/e6ce6cdfb3cae92201d73202122153dfae2fd1cf))

## [4.2.1](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.2.0...v4.2.1) (2026-10-05)

### Bug Fixes

* **matchmaking:** compte a rebours de lancement a 3 s ([89a511a](https://github.com/quizup-organization/quizup-matchmaking/commit/89a511abd523b99a7e9ff29a01822bd3e097072c))

## [4.2.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.1.0...v4.2.0) (2026-10-05)

### Features

* **matchmaking:** agregat Challenge (defi nominatif) — domaine ([ac4ddad](https://github.com/quizup-organization/quizup-matchmaking/commit/ac4ddad54a7e1918620ab229814eb24750ee6597))
* **matchmaking:** infra Challenge (saga, projection, persistance, services) ([ebba8ee](https://github.com/quizup-organization/quizup-matchmaking/commit/ebba8ee1e94089a0fd7a9399863f3d4abc9efcb6))
* **matchmaking:** salle temps reel (presence, fenetre 3 min, ready 20 s) ([81500d8](https://github.com/quizup-organization/quizup-matchmaking/commit/81500d87d43d1e58b95db2154842eaa0d9af9de8))

## [4.1.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v4.0.0...v4.1.0) (2026-10-03)

### Features

* **matchmaking:** nominative lobbies, decline and simplified statuses ([7410a37](https://github.com/quizup-organization/quizup-matchmaking/commit/7410a374d3e7cf0ac6d6b61f79eb778fe63e8216))

## [4.0.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v3.0.0...v4.0.0) (2026-10-02)

### ⚠ BREAKING CHANGES

* **matchmaking:** public matchmaking aggregate + private lobby

### Code Refactoring

* **matchmaking:** public matchmaking aggregate + private lobby ([df83de2](https://github.com/quizup-organization/quizup-matchmaking/commit/df83de2bc644fae6041a79456e3af651eefebeba))

## [3.0.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v2.1.0...v3.0.0) (2026-09-30)

### ⚠ BREAKING CHANGES

* **matchmaking:** LobbyPlayer/PlayerSummary carry the player language; enqueue rejects topics unavailable in the player language; pairing requires the topic to cover both languages; LobbySaga forwards required languages to CreateGameCommand (pins SDK 4.1.0, profile 3.0.0, game 4.0.0, theme 4.0.0).

### Features

* **matchmaking:** language-aware pairing and ticket availability guards ([67bfa7d](https://github.com/quizup-organization/quizup-matchmaking/commit/67bfa7d79d678391b7cd86b37720cf4db15b86e0))

## [2.1.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v2.0.0...v2.1.0) (2026-09-27)

### Features

* **matchmaking:** ticket read model, presence-aware matching and bounded bot fallback ([dad5241](https://github.com/quizup-organization/quizup-matchmaking/commit/dad5241dc2c426002a8a17ed49d89982cd3e7ff4))

## [2.0.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.6.3...v2.0.0) (2026-09-25)

### ⚠ BREAKING CHANGES

* **matchmaking:** the service no longer exposes its REST API nor WebSocket (the
BFF is the sole surface); the queue command handlers are synchronous and search
use cases use the SDK SearchRequest/SearchResponse DTOs.

### Features

* **matchmaking:** headless service + synchronous queue command handlers ([31790c7](https://github.com/quizup-organization/quizup-matchmaking/commit/31790c7f7733d1d6e4b54122618131342d3b28d9))

## [1.6.3](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.6.2...v1.6.3) (2026-09-24)

### Bug Fixes

* **deps:** bump quizup-parent to 2.4.4 (typed PageResult over query transport) ([d819424](https://github.com/quizup-organization/quizup-matchmaking/commit/d81942424e46fe9d864d23a2b5b9e142c85f4639))

## [1.6.2](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.6.1...v1.6.2) (2026-09-24)

### Bug Fixes

* **deps:** bump quizup-parent to 2.4.3 (bus-only search criteria type info) ([545a359](https://github.com/quizup-organization/quizup-matchmaking/commit/545a359b359ce34d5dd02dc487e583ca4641454d))

## [1.6.1](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.6.0...v1.6.1) (2026-09-24)

### Bug Fixes

* **deps:** bump quizup-parent to 2.4.2 (search criteria type info) ([2609d2c](https://github.com/quizup-organization/quizup-matchmaking/commit/2609d2c815f7a29b94a2bc1cac1c4fa4885cd731))

## [1.6.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.5.5...v1.6.0) (2026-09-24)

### Features

* **matchmaking:** handle enqueue/cancel matchmaking commands on the distributed bus ([0104817](https://github.com/quizup-organization/quizup-matchmaking/commit/0104817916d0f54d3be4288683de527d143102c4))

## [1.5.5](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.5.4...v1.5.5) (2026-09-23)

### Bug Fixes

* **matchmaking:** lobby/queue cancel as POST action and bump SDK to 2.4.1 ([677fc34](https://github.com/quizup-organization/quizup-matchmaking/commit/677fc341cd7825a35b17dee44b9a29f9a741a6bc))

## [1.5.4](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.5.3...v1.5.4) (2026-09-22)

### Bug Fixes

* **quizup-matchmaking:** upgrade quizup-parent to 2.3.4 ([2b89d0a](https://github.com/quizup-organization/quizup-matchmaking/commit/2b89d0a7becdef9fe3d4f31c240b1d331183e6e7))

## [1.5.3](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.5.2...v1.5.3) (2026-09-22)

### Bug Fixes

* **quizup-matchmaking:** upgrade quizup-parent to 2.3.3 ([65a0611](https://github.com/quizup-organization/quizup-matchmaking/commit/65a06112a682eeb4f5a7b8141e7b0fa05f7de194))

## [1.5.2](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.5.1...v1.5.2) (2026-09-22)

### Bug Fixes

* **quizup-matchmaking:** upgrade quizup-parent to 2.3.2 ([4b6fec2](https://github.com/quizup-organization/quizup-matchmaking/commit/4b6fec28b468fff35887ad14ea4b80ffe48c9bf1))

## [1.5.1](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.5.0...v1.5.1) (2026-09-22)

### Bug Fixes

* **matchmaking:** upgrade quizup-parent to 2.3.1 (registration address fix) ([413bb30](https://github.com/quizup-organization/quizup-matchmaking/commit/413bb306eae7dde9bde840d84ed9ff59b1e13df8))

## [1.5.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.4.7...v1.5.0) (2026-09-22)

### Features

* **matchmaking:** explicit Axon processing groups ([15ee9ed](https://github.com/quizup-organization/quizup-matchmaking/commit/15ee9ed43e35754b2f43b3c073d9128d279d5fa7))

## [1.4.7](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.4.6...v1.4.7) (2026-09-21)

### Bug Fixes

* **observability:** keep readable console logs in local ([774bfb8](https://github.com/quizup-organization/quizup-matchmaking/commit/774bfb8adcd42e672ec155f879c9f90cd64e7765))

## [1.4.6](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.4.5...v1.4.6) (2026-09-20)

### Bug Fixes

* **deps:** bump quizup-sdk to 2.1.1 ([a43735e](https://github.com/quizup-organization/quizup-matchmaking/commit/a43735e1a0ddeebeed0f83e7464abc3d14e9f6c9))

## [1.4.5](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.4.4...v1.4.5) (2026-09-20)

### Bug Fixes

* **config:** align prod service URLs with quizup-* names ([6f70e13](https://github.com/quizup-organization/quizup-matchmaking/commit/6f70e139d7aa900225ac4dfffa64b8d190085dfc))

## [1.4.4](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.4.3...v1.4.4) (2026-09-20)

### Bug Fixes

* **deps:** bump quizup-sdk to 2.1.0 ([32cf1c1](https://github.com/quizup-organization/quizup-matchmaking/commit/32cf1c15beb5fc45aa19be5251ac5ca0a89bac41))

## [1.4.3](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.4.2...v1.4.3) (2026-09-20)

### Bug Fixes

* **system:** use the unified system account ([d4a03ce](https://github.com/quizup-organization/quizup-matchmaking/commit/d4a03ce6aeb706a5a7f3a6f42c2b2666885e6aeb))

## [1.4.2](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.4.1...v1.4.2) (2026-09-20)

### Bug Fixes

* **observability:** remove WebSocket and business KPI metrics (consume quizup-sdk 1.4.3) ([15e80dc](https://github.com/quizup-organization/quizup-matchmaking/commit/15e80dc37359431b48a862b1105dfd28a3aaf859))

## [1.4.1](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.4.0...v1.4.1) (2026-09-20)

### Bug Fixes

* **observability:** consume quizup-sdk 1.4.2 (Axon activity metrics fix + Swagger server URL) ([fa5dfd5](https://github.com/quizup-organization/quizup-matchmaking/commit/fa5dfd54ca4033cb21c9d1af104e03226de22a1a))

## [1.4.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.3.0...v1.4.0) (2026-09-20)

### Features

* **observability:** consume quizup-sdk 1.4.0 (Axon activity metrics) ([69f19b9](https://github.com/quizup-organization/quizup-matchmaking/commit/69f19b9e05632025de33e4684a4cd39c806d56ab))

## [1.3.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.2.0...v1.3.0) (2026-09-20)

### Features

* **observability:** matchmaking KPIs (lobbies, matches, wait time) + consume quizup-sdk 1.3.0 ([b4dd339](https://github.com/quizup-organization/quizup-matchmaking/commit/b4dd33979a10bbe0b49791ffa5f2d17f09dec927))

## [1.2.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.1.0...v1.2.0) (2026-09-20)

### Features

* **observability:** consume quizup-sdk 1.2.0 (structured logs + tracing) ([d847538](https://github.com/quizup-organization/quizup-matchmaking/commit/d847538200853e465529abb309731d2777ffc764))

## [1.1.0](https://github.com/quizup-organization/quizup-matchmaking/compare/v1.0.0...v1.1.0) (2026-09-20)

### Features

* **observability:** consume quizup-sdk 1.1.0 (Prometheus metrics) ([693af63](https://github.com/quizup-organization/quizup-matchmaking/commit/693af632decd5feb1ea107143bac792fc605b3d7))

## 1.0.0 (2026-09-19)

### Features

* first commit ([35f3804](https://github.com/quizup-organization/quizup-matchmaking/commit/35f3804ea72deb639d49783d3bb9426ab2e70f02))

### Bug Fixes

* **maven:** pin inter-service domains to 0.0.1; align parent to quizup-parent 1.0.0 ([6849525](https://github.com/quizup-organization/quizup-matchmaking/commit/6849525f52ab995c68d5ccd3c2db14f4e7de2d44))
