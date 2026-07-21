-- A battle_history row is owned by character_id (the attacker); opponent_character_id is a secondary
-- reference to whoever they fought. It was declared ON DELETE CASCADE (V8), which deletes THIS row — the
-- attacker's own history — when the OPPONENT's character is later deleted, silently corrupting a
-- completely different player's live-computed win count/achievements/ranked standing (system design
-- §19/§20/§21/§22 all read this table live). A deleted opponent should read the same as a bot opponent
-- (opponent_character_id NULL, opponent_is_bot already distinguishes the two) — not delete the row.
ALTER TABLE battle_history DROP CONSTRAINT battle_history_opponent_character_id_fkey;
ALTER TABLE battle_history ADD CONSTRAINT battle_history_opponent_character_id_fkey
    FOREIGN KEY (opponent_character_id) REFERENCES characters (id) ON DELETE SET NULL;
