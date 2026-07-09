-- Adds the physical-path resource pool (Stamina) to characters, mirroring max_mp exactly
-- (system design §4.2/§4.4). Default matches the existing max_hp/max_mp literal convention (100);
-- a stat-derived formula (50 + strength * 5) is a later tuning concern, not applied here.
--
-- NOTE (flagged for planning close-out): system design §8 pencilled V6 in for the future
-- Battle_History migration. This stamina column is a required prerequisite for the M2 combat core
-- (A5/A7) and landed first, so it takes V6; the Battle_History migration should become V7 when
-- Epic C is implemented.
ALTER TABLE characters ADD COLUMN max_stamina INTEGER NOT NULL DEFAULT 100;
