-- Renames the Faction enum's stored values from the placeholder 'A'/'B' to 'CRIMSON'/'SKYBORN',
-- matching the common.model.Faction constants. Declaration order is preserved on the Java side, so
-- Kryo's ordinal-based enum serialization is unaffected.
--
-- Order matters: the old CHECK is dropped first, because it would reject the new values on the
-- UPDATEs themselves. V1 declared that CHECK inline and unnamed, so Postgres generated the name
-- `characters_faction_check` (verified against the dev database).

ALTER TABLE characters DROP CONSTRAINT characters_faction_check;

UPDATE characters SET faction = 'CRIMSON' WHERE faction = 'A';
UPDATE characters SET faction = 'SKYBORN' WHERE faction = 'B';

ALTER TABLE characters ADD CONSTRAINT characters_faction_check CHECK (faction IN ('CRIMSON', 'SKYBORN'));
