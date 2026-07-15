-- Epic L / system design §15: the currency foundation for leveling and the (future) skill tree.
-- Both default to 0 so every existing character starts with an empty balance; RewardService begins
-- accumulating them per battle from here on. `unspent_stat_points` is spendable now (AllocateStatPoints);
-- `skill_points` accumulates but has no spend path until the skill tree epic (M).
ALTER TABLE characters ADD COLUMN unspent_stat_points INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN skill_points INTEGER NOT NULL DEFAULT 0;
