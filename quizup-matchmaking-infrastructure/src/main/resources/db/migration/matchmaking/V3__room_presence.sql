-- V3: Salle — présence des joueurs.
-- Le défi nominatif vit dans challenge_entry (V2) ; room_entry porte la salle
-- (présence + préparation de la partie).

ALTER TABLE room_entry ADD COLUMN initiator_present   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE room_entry ADD COLUMN participant_present BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_room_participant ON room_entry (participant_id);
