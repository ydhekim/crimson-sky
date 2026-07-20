-- S4 (system design §22): a character can wear one unlocked title. Nullable — nothing is equipped by
-- default, and clearing the title sets it back to NULL. The unlock-ownership of whatever id lands here is
-- enforced in CharacterPageService against achievement_unlocks, not by a FK on this column.
ALTER TABLE characters ADD COLUMN equipped_title VARCHAR(50);
