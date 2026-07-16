package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System design §17 — a broken weapon is <b>unaffordable</b>, reusing the mechanism Stamina-insufficiency
 * already uses rather than adding a "does nothing" branch to the cascade. The pouch walk, the punch
 * fallback, and every existing determinism test therefore need no change: a weapon at 0 durability is
 * skipped by exactly the code that already skips one there's no Stamina for.
 *
 * <p>The pairs below are deliberate — each isolates one of the two reasons to say no, holding the other
 * satisfied, so neither can pass by masking the other.
 */
class CombatMathAffordabilityTest {

    private static Weapon weapon(int staminaCost, int currentDurability) {
        return new Weapon(1L, "Hammer", "", Rarity.COMMON, 2f, 10, 20, staminaCost, 20, currentDurability);
    }

    @Test
    void aBrokenWeaponIsUnaffordableNoMatterHowMuchStaminaIsLeft() {
        assertFalse(CombatMath.isAffordable(weapon(5, 0), 100), "0 durability → unusable (§17)");
        assertFalse(CombatMath.isAffordable(weapon(0, 0), Integer.MAX_VALUE),
            "not even a free weapon and an untouched pool can draw a broken one");
    }

    @Test
    void oneRemainingDurabilityIsStillAffordable() {
        // The boundary that decides whether a weapon gets its last swing: the check is > 0, not > 1, so a
        // weapon at 1 fights this battle and only then wears to 0.
        assertTrue(CombatMath.isAffordable(weapon(5, 1), 100));
    }

    @Test
    void durabilityDoesNotRescueAWeaponWithNoStaminaForIt() {
        // Regression: the pre-§17 Stamina rule is unchanged, including its `>=` boundary.
        assertFalse(CombatMath.isAffordable(weapon(25, 20), 20), "Stamina still gates a fully repaired weapon");
        assertTrue(CombatMath.isAffordable(weapon(25, 20), 25), "stamina exactly equal to cost still affords");
        assertTrue(CombatMath.isAffordable(weapon(25, 20), 26));
    }
}
