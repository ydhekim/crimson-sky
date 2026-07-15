package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure leveling/milestone helpers on {@link RewardService} (Epic L / system design §15), tested
 * without a battle or a database. The wiring into a real reward transaction (persisted level/points, and
 * the bonus item-grant write path) is covered by {@code RewardServiceTest} and
 * {@code BattleLeavesInventoryAloneTest}.
 */
class RewardServiceProgressionTest {

    @Test
    void expCurveIsAnchoredSoLevelOneNeedsZero() {
        // §0's correction: 8×L² − 8, not a literal 8×L². Verified against §15's own worked numbers.
        assertEquals(0L, RewardService.expNeededForLevel(1));
        assertEquals(24L, RewardService.expNeededForLevel(2));
        assertEquals(64L, RewardService.expNeededForLevel(3));
    }

    @Test
    void thresholdIncrementsMatchTheGrowthFormula() {
        // By construction expNeededForLevel(L+1) − expNeededForLevel(L) == 8×(2L+1) — 24 for the first.
        for (int l = 1; l < RewardService.LEVEL_CAP; l++) {
            long increment = RewardService.expNeededForLevel(l + 1) - RewardService.expNeededForLevel(l);
            assertEquals(8L * (2L * l + 1), increment, "increment for level " + l + "→" + (l + 1));
        }
    }

    @Test
    void aSingleThresholdCrossingGainsOneLevel() {
        // Level 1, 0 exp, +24 exp → exactly the level-2 threshold.
        assertEquals(2, RewardService.levelAfter(1, 24L));
        assertEquals(1, RewardService.levelAfter(1, 23L), "one short of the threshold does not level");
    }

    @Test
    void oneBattleCanCrossMultipleThresholds() {
        // 0 → 64 exp crosses both level 2 (24) and level 3 (64) in one delta.
        assertEquals(3, RewardService.levelAfter(1, 64L));
    }

    @Test
    void neverAdvancesPastTheLevelCap() {
        assertEquals(RewardService.LEVEL_CAP, RewardService.levelAfter(RewardService.LEVEL_CAP, Long.MAX_VALUE));
        assertEquals(RewardService.LEVEL_CAP, RewardService.levelAfter(49, Long.MAX_VALUE),
            "a huge exp delta stops at the cap, not past it");
    }

    @Test
    void milestoneRollOnlyFiresOnMultiplesOfTen() {
        Random random = new Random(1L);
        // Crossing 1→9 (no multiple of 10) never rolls, regardless of the RNG.
        assertTrue(RewardService.rollMilestoneBonus(1, 9, random).isEmpty(),
            "a level-up that crosses no multiple of 10 grants nothing");
        // Crossing into level 10 is a milestone; a forced-pass RNG grants exactly one weapon.
        List<Weapon> granted = RewardService.rollMilestoneBonus(9, 10, forcedPass());
        assertEquals(1, granted.size(), "one milestone crossed with a passing roll → one weapon");
    }

    @Test
    void aMultiLevelJumpRollsEachMilestoneItCrosses() {
        // 9 → 21 crosses two milestones (10 and 20); a forced-pass RNG grants one weapon per milestone.
        assertEquals(2, RewardService.rollMilestoneBonus(9, 21, forcedPass()).size());
    }

    @Test
    void theRollFiresAtTheConfiguredTenPercentRate() {
        // Each single-milestone crossing (level 9 → 10) is one independent 10% roll. Over many trials with
        // a seeded RNG the empirical rate sits close to BONUS_ROLL_CHANCE — the roll is neither always-on
        // nor always-off.
        Random random = new Random(12345L);
        int trials = 100_000;
        int fired = 0;
        for (int i = 0; i < trials; i++) {
            if (!RewardService.rollMilestoneBonus(9, 10, random).isEmpty()) {
                fired++;
            }
        }
        double rate = (double) fired / trials;
        assertTrue(Math.abs(rate - RewardService.BONUS_ROLL_CHANCE) < 0.01,
            "empirical roll rate " + rate + " should be within 1% of " + RewardService.BONUS_ROLL_CHANCE);
    }

    private static Random forcedPass() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
