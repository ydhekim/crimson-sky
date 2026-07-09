-- Adds the matchmaking rating to characters (system design §8, story B1). Matchmaking pairs by
-- closest Elo within a range, so the column is a prerequisite for the queue's pairing logic.
--
-- Only read for now (CharacterDao.getElo): applying an Elo delta after a battle concludes — and
-- exposing `elo` on the shared Character record — belongs to story C1 (reward persistence).
-- The battle_history table §8 sketches alongside this column also lands with C1, not here.
ALTER TABLE characters ADD COLUMN elo INTEGER NOT NULL DEFAULT 1000;
