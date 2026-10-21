ALTER TABLE matchmaking_entry RENAME COLUMN level TO rating;
ALTER INDEX IF EXISTS idx_matchmaking_pool RENAME TO idx_matchmaking_pool_rating;
