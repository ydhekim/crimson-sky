package io.github.ydhekim.crimson_sky.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System design §18 — the potion trigger's one piece of arithmetic: has a pool dropped to or below a
 * percentage of its max? Worth its own test because the two things most likely to go wrong here are
 * silent: an off-by-one at the boundary (a 50% potion that won't fire at exactly half), and rounding
 * (a float ratio makes "is 1 of 3 below 33%" a coin toss).
 */
class CombatMathThresholdTest {

    @Test
    void theBoundaryItselfTriggers() {
        // At-or-below, not strictly below: a player who sets 50% means "at half, drink".
        assertTrue(CombatMath.isBelowThreshold(50, 100, 50), "exactly 50% of max → triggers");
        assertFalse(CombatMath.isBelowThreshold(51, 100, 50), "one point above → does not");
        assertTrue(CombatMath.isBelowThreshold(49, 100, 50));
    }

    @Test
    void anEmptyPoolAlwaysTriggersAndAFullOneNeverDoes() {
        assertTrue(CombatMath.isBelowThreshold(0, 500, 1), "0 is below every threshold above 0%");
        assertFalse(CombatMath.isBelowThreshold(500, 500, 99), "full is below nothing short of 100%");
        assertTrue(CombatMath.isBelowThreshold(500, 500, 100), "…but 100% means always, by construction");
    }

    @Test
    void aMaxOfZeroNeverTriggers() {
        // The guard: no pool means nothing to be low on. Without it the cross-multiplied comparison would
        // read 0 <= 0 and fire a potion into a resource the character doesn't have.
        assertFalse(CombatMath.isBelowThreshold(0, 0, 50));
        assertFalse(CombatMath.isBelowThreshold(0, 0, 100), "not even at 100%");
    }

    @Test
    void aThresholdOfZeroNeverTriggersOnAPoolWithAnythingLeft() {
        assertFalse(CombatMath.isBelowThreshold(1, 100, 0), "0% is off, effectively");
        assertTrue(CombatMath.isBelowThreshold(0, 100, 0), "except at a genuinely empty pool: 0 <= 0");
    }

    @Test
    void oddPoolsCompareExactlyRatherThanApproximately() {
        // Integer cross-multiplication, not float division (§18): 1/3 is 33.3…%, which is above a 33%
        // threshold, and 3/9 is exactly 33.3…% too. A float ratio would decide these by rounding luck.
        assertFalse(CombatMath.isBelowThreshold(1, 3, 33), "33.3% is not at or below 33%");
        assertTrue(CombatMath.isBelowThreshold(1, 3, 34));
        assertTrue(CombatMath.isBelowThreshold(1, 3, 100));
        assertFalse(CombatMath.isBelowThreshold(3, 7, 42), "42.8% > 42%");
        assertTrue(CombatMath.isBelowThreshold(3, 7, 43));
    }

    @Test
    void aHugePoolDoesNotOverflowTheComparison() {
        // The reason both sides are widened to long: current × 100 on an int would wrap well below
        // Integer.MAX_VALUE and flip the answer.
        assertFalse(CombatMath.isBelowThreshold(Integer.MAX_VALUE, Integer.MAX_VALUE, 50),
            "a full pool is a full pool, however big");
        assertTrue(CombatMath.isBelowThreshold(1, Integer.MAX_VALUE, 1));
    }
}
