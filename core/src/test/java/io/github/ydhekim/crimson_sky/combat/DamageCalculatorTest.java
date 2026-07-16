package io.github.ydhekim.crimson_sky.combat;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crit mechanic (system design §14), built this pass. Covers the natural-crit range rule and its
 * zero-width guard, and Crimson's forced-crit roll — including the load-bearing property that a zero crit
 * bonus consumes <b>no</b> extra RNG, so every pre-existing seeded test sees identical RNG consumption.
 */
class DamageCalculatorTest {

    private static final long SEED = 987_654_321L;

    // --- Natural crit (isNaturalCrit) --------------------------------------------------------------

    @Test
    void flatRangeNeverNaturallyCrits() {
        // The guard that keeps every flat-damage fixture (100/100) from critting on every hit.
        for (int roll = 0; roll <= 200; roll++) {
            assertFalse(DamageCalculator.isNaturalCrit(roll, 100, 100),
                "a zero-width range has no top band — it can never naturally crit");
        }
    }

    @Test
    void naturalCritFiresAtOrAboveTheEightyPercentThreshold() {
        // Range 0..100 → threshold = 0 + 0.8 * 100 = 80. At threshold crits; one below does not.
        assertTrue(DamageCalculator.isNaturalCrit(80, 0, 100), "exactly at the threshold is a crit");
        assertFalse(DamageCalculator.isNaturalCrit(79, 0, 100), "one below the threshold is not a crit");
        assertTrue(DamageCalculator.isNaturalCrit(100, 0, 100), "the top of the range crits");
        assertFalse(DamageCalculator.isNaturalCrit(0, 0, 100), "the bottom of the range does not");
    }

    @Test
    void naturalCritRateOverARealRangeLandsNearTwentyPercent() {
        SplittableRandom rng = new SplittableRandom(SEED);
        int trials = 200_000;
        int crits = 0;
        for (int i = 0; i < trials; i++) {
            int rawRoll = DamageCalculator.randomInt(0, 100, rng);
            if (DamageCalculator.isNaturalCrit(rawRoll, 0, 100)) {
                crits++;
            }
        }
        double rate = (double) crits / trials;
        // 0..100 is 101 values; the top band 80..100 is 21 of them ≈ 0.208.
        assertTrue(rate > 0.18 && rate < 0.24, "empirical natural-crit rate should sit near 20%, was " + rate);
    }

    // --- Forced crit (Crimson's skill), and its zero-cost when unequipped --------------------------

    @Test
    void zeroCritBonusConsumesNoForcedRoll() {
        // Two identically seeded RNGs: one goes through rollHitDamage with a 0 crit bonus, the other
        // consumes only the damage draw by hand. If their next draw agrees, rollHitDamage drew nothing
        // extra — the property every pre-crit seeded test depends on.
        SplittableRandom viaHitDamage = new SplittableRandom(SEED);
        SplittableRandom byHand = new SplittableRandom(SEED);

        DamageCalculator.rollHitDamage(100, 100, 0, 0, 0 /* no crit bonus */, viaHitDamage);
        DamageCalculator.randomInt(100, 100, byHand); // the single damage draw a flat hit consumes

        assertEquals(byHand.nextLong(), viaHitDamage.nextLong(),
            "a zero crit bonus must not consume the forced-crit roll");
    }

    @Test
    void aPositiveCritBonusForcesCritsAtRoughlyItsConfiguredRate() {
        SplittableRandom rng = new SplittableRandom(SEED);
        int trials = 200_000;
        int critBonus = 20;
        int crits = 0;
        for (int i = 0; i < trials; i++) {
            // Flat 100/100 range → no natural crit possible, def 0 / stat 0 → base damage 100, crit 150.
            int damage = DamageCalculator.rollHitDamage(100, 100, 0, 0, critBonus, rng);
            if (damage == 150) {
                crits++;
            } else {
                assertEquals(100, damage, "a non-crit flat hit deals its base damage");
            }
        }
        double rate = (double) crits / trials;
        assertTrue(rate > 0.17 && rate < 0.23,
            "forced-crit rate should sit near the configured " + critBonus + "%, was " + rate);
    }

    @Test
    void aCritAppliesTheOneAndAHalfMultiplierToFinalDamage() {
        // A range whose whole span is inside the crit band, so the natural crit is certain regardless of
        // seed: min == threshold boundary forced by a 1-wide top. Simpler: assert the multiplier directly
        // via a forced crit (bonus 100 → always fires) on a flat 100 hit → 150.
        SplittableRandom rng = new SplittableRandom(SEED);
        int damage = DamageCalculator.rollHitDamage(100, 100, 0, 0, 100 /* always forces a crit */, rng);
        assertEquals(150, damage, "×1.5 of the base 100 damage");
    }
}
