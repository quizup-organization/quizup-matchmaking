-- V1: Création du schéma lobby (nomenclature Lobby refactorisée)
-- Tables : lobby_entry

CREATE TABLE lobby_entry (
    lobby_id       VARCHAR(255) NOT NULL,
    topic_id       VARCHAR(255) NOT NULL,
    initiator_id   VARCHAR(255) NOT NULL,
    challenger_id  VARCHAR(255),
    game_id        VARCHAR(255),
    vs_bot         BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL,  -- OPEN, CLOSED, CANCELLED
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (lobby_id)
);

-- Index FIFO matchmaking : topic + statut + ordre d'arrivée
CREATE INDEX idx_lobby_topic_status_created ON lobby_entry (topic_id, status, created_at);
-- Index de traçabilité joueurs
CREATE INDEX idx_lobby_initiator  ON lobby_entry (initiator_id);
CREATE INDEX idx_lobby_challenger ON lobby_entry (challenger_id);
