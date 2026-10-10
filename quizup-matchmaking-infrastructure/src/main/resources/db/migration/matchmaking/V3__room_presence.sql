-- V3: Salle — présence des joueurs et compte à rebours de lancement.
-- Le défi nominatif vit dans challenge_entry (V2) ; room_entry porte la salle
-- (présence + ready check + création de partie).

ALTER TABLE room_entry ADD COLUMN initiator_present   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE room_entry ADD COLUMN participant_present BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE room_entry ADD COLUMN all_present_at      TIMESTAMP;
ALTER TABLE room_entry ADD COLUMN ready_deadline_at   TIMESTAMP;

CREATE INDEX idx_room_participant ON room_entry (participant_id);
