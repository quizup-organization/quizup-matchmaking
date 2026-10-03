-- V1: Schéma matchmaking — recherche d'appariement public + salon privé.
-- Tables : matchmaking_entry (pool + ticket produit), lobby_entry (salon privé).
-- Statuts réduits au cycle de vie : SEARCHING|CLOSED|FAILED (ticket), CREATED|CLOSED|FAILED
-- (salon). L'issue exacte est portée par l'événement terminal et la notification ; les états
-- terminaux sont purgés par la saga après rétention (deadline), puis markDeleted.

CREATE TABLE matchmaking_entry (
    matchmaking_id VARCHAR(255) NOT NULL,
    player_id      VARCHAR(255) NOT NULL,
    topic_id       VARCHAR(255) NOT NULL,
    level          INTEGER      NOT NULL,
    languages      VARCHAR(64)  NOT NULL,   -- codes ISO 639-1 séparés par des virgules
    opponent_id    VARCHAR(255),
    game_id        VARCHAR(255),
    vs_bot         BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL,   -- SEARCHING, CLOSED, FAILED
    claimed_by     VARCHAR(255),            -- réservation atomique du pool (le statut reste SEARCHING)
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (matchmaking_id)
);

-- Index du pool d'appariement : sujet + statut + niveau + ordre d'arrivée.
CREATE INDEX idx_matchmaking_pool ON matchmaking_entry (topic_id, status, level, created_at);
CREATE INDEX idx_matchmaking_player ON matchmaking_entry (player_id);

CREATE TABLE lobby_entry (
    lobby_id       VARCHAR(255) NOT NULL,
    topic_id       VARCHAR(255) NOT NULL,
    initiator_id   VARCHAR(255) NOT NULL,
    opponent_id    VARCHAR(255),            -- défi nominatif : seul cet invité peut rejoindre
    participant_id VARCHAR(255),
    game_id        VARCHAR(255),
    status         VARCHAR(20)  NOT NULL,   -- CREATED, CLOSED, FAILED
    created_at     TIMESTAMP    NOT NULL,
    expires_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (lobby_id)
);

CREATE INDEX idx_lobby_initiator ON lobby_entry (initiator_id);
CREATE INDEX idx_lobby_opponent ON lobby_entry (opponent_id);
CREATE INDEX idx_lobby_status ON lobby_entry (status);
