-- V1: Schéma matchmaking — recherche d'appariement public + salle.
-- Tables : matchmaking_entry (pool + ticket produit), room_entry (salle : deux humains ou humain/bot).
-- Statuts : WAITING_PLAYERS|FETCHING_PLAYERS|FETCHING_QUESTIONS|CREATING_GAME|COMPLETED|CANCELLED|EXPIRED|FAILED
-- (salle, avancement de la préparation) et SEARCHING|COMPLETED|CANCELLED|EXPIRED|FAILED (ticket). L'issue exacte est
-- portée par l'événement terminal et la notification ; les états terminaux sont purgés par la
-- saga après rétention (deadline), puis markDeleted. La data est réinitialisée à chaque breaking
-- change des contrats (pas de migrations de compatibilité).

CREATE TABLE matchmaking_entry (
    matchmaking_id VARCHAR(255) NOT NULL,
    player_id      VARCHAR(255) NOT NULL,
    topic_id       VARCHAR(255) NOT NULL,
    level          INTEGER      NOT NULL,
    languages      VARCHAR(64)  NOT NULL,   -- codes ISO 639-1 séparés par des virgules
    opponent_id    VARCHAR(255),
    room_id        VARCHAR(255),            -- salle d'appariement créée par la saga
    vs_bot         BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL,   -- SEARCHING, COMPLETED, CANCELLED, EXPIRED, FAILED
    claimed_by     VARCHAR(255),            -- réservation atomique du pool (le statut reste SEARCHING)
    created_at     TIMESTAMP    NOT NULL,
    deadline_at    TIMESTAMP    NOT NULL,   -- fin de la fenêtre de recherche (10 s), exposée au client
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (matchmaking_id)
);

-- Index du pool d'appariement : sujet + statut + niveau + ordre d'arrivée.
CREATE INDEX idx_matchmaking_pool ON matchmaking_entry (topic_id, status, level, created_at);
CREATE INDEX idx_matchmaking_player ON matchmaking_entry (player_id);

CREATE TABLE room_entry (
    room_id        VARCHAR(255) NOT NULL,
    topic_id       VARCHAR(255) NOT NULL,
    initiator_id   VARCHAR(255) NOT NULL,
    opponent_id    VARCHAR(255),            -- salle nominative : seul cet invité peut apparaître
    participant_id VARCHAR(255),            -- enregistré à l'apparition du second joueur
    game_id        VARCHAR(255),
    vs_bot         BOOLEAN      NOT NULL DEFAULT FALSE, -- second joueur = bot (join automatique)
    status         VARCHAR(30)  NOT NULL,   -- WAITING_PLAYERS ... COMPLETED, CLOSED, FAILED
    created_at     TIMESTAMP    NOT NULL,
    expires_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (room_id)
);

CREATE INDEX idx_room_initiator ON room_entry (initiator_id);
CREATE INDEX idx_room_opponent ON room_entry (opponent_id);
CREATE INDEX idx_room_status ON room_entry (status);
