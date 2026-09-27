-- V2: Read model « ticket » de matchmaking (statuts produit explicites), alimenté par la
-- projection des événements de lobby. Table : matchmaking_ticket_entry.

CREATE TABLE matchmaking_ticket_entry (
    ticket_id    VARCHAR(255) NOT NULL,
    topic_id     VARCHAR(255) NOT NULL,
    initiator_id VARCHAR(255) NOT NULL,
    opponent_id  VARCHAR(255),
    game_id      VARCHAR(255),
    vs_bot       BOOLEAN      NOT NULL DEFAULT FALSE,
    status       VARCHAR(20)  NOT NULL,  -- SEARCHING, MATCHED, CANCELLED
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (ticket_id)
);

CREATE INDEX idx_ticket_initiator ON matchmaking_ticket_entry (initiator_id);
CREATE INDEX idx_ticket_status    ON matchmaking_ticket_entry (status);
