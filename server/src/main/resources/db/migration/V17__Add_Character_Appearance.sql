-- Purely cosmetic (system design §23) — no stats, no combat interaction, not read by anything yet (M4 is
-- still placeholder-rendering the combat screen; M5's real art pipeline is what eventually consumes this).
-- A JSONB blob, not real columns, for the same reason skill_tree (§16) is one: avoids inventing atlas/sprite
-- IDs before real art exists to back them.
ALTER TABLE characters ADD COLUMN appearance JSONB NOT NULL DEFAULT '{}'::jsonb;
