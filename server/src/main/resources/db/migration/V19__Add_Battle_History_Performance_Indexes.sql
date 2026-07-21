-- battle_history has no index beyond its own primary key today. Every quest check (§19), the daily battle
-- cap (§20), ranked Elo/rank (§21), and every achievement/character-page read (§22) filters this table by
-- character_id, several also ordering by created_at DESC — all currently full table scans. One composite
-- index covers both the plain character_id filters and the ORDER BY created_at DESC queries.
CREATE INDEX battle_history_character_id_created_at_idx ON battle_history (character_id, created_at DESC);

-- characters.account_id is read by every character-list/character-count lookup; far lower row-count risk
-- than battle_history, but equally uncovered by any existing index.
CREATE INDEX characters_account_id_idx ON characters (account_id);
