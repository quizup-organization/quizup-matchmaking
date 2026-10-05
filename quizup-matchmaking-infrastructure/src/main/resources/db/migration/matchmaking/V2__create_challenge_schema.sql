-- V2: Défi nominatif (intention asynchrone) — distinct de la salle temps réel.
-- Le défi porte la réponse (accepté/refusé/annulé/expiré) et le lien vers la salle créée ;
-- ni présence ni partie ici. L'identifiant sert de référence côté notifications.

CREATE TABLE challenge_entry (
    challenge_id  VARCHAR(255) NOT NULL,
    topic_id      VARCHAR(255) NOT NULL,
    challenger_id VARCHAR(255) NOT NULL,
    opponent_id   VARCHAR(255) NOT NULL,
    room_id       VARCHAR(255),
    status        VARCHAR(20)  NOT NULL,   -- PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED
    created_at    TIMESTAMP    NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    resolved_at   TIMESTAMP,
    PRIMARY KEY (challenge_id)
);

CREATE INDEX idx_challenge_challenger ON challenge_entry (challenger_id);
CREATE INDEX idx_challenge_opponent ON challenge_entry (opponent_id);
CREATE INDEX idx_challenge_status ON challenge_entry (status);
