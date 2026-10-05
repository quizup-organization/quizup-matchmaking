-- V3: Salle temps réel — présence des joueurs et compte à rebours de lancement.
-- Le défi nominatif vit dans challenge_entry (V2) ; lobby_entry devient la salle
-- (présence + ready check + création de partie).

ALTER TABLE lobby_entry ADD COLUMN initiator_present  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lobby_entry ADD COLUMN participant_present BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lobby_entry ADD COLUMN all_present_at     TIMESTAMP;
ALTER TABLE lobby_entry ADD COLUMN ready_deadline_at  TIMESTAMP;
ALTER TABLE lobby_entry ADD COLUMN missed_reason      VARCHAR(64);

CREATE INDEX idx_lobby_participant ON lobby_entry (participant_id);
