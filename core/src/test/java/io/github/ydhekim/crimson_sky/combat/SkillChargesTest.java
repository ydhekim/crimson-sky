package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.ResourceType;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * System design §18 — the two record copies a potion's persisted {@code charges} travels through:
 * {@link Skill#withCharges(int)} on the way into a battle (the Inventory cross-reference) and
 * {@link Skill#consumed(int)} on the way out (the post-battle tally). The record lives in {@code common},
 * but both copies exist purely to serve combat's charge path, and {@code core} is where the module's test
 * source set is — so it is tested alongside the rest of §18 rather than in a source set of its own.
 */
class SkillChargesTest {

    private static Skill potion(int charges) {
        return new Skill(100L, "Small Health Potion", "", SkillType.CONSUMABLE, 0, Difficulty.EASY, 0, 0,
            null, 0, null, ResourceType.HEALTH, 50, 100, charges);
    }

    @Test
    void consumedSubtractsTheTriggerTally() {
        // A tally, not a flag (§18): unlike durability, three triggers in one long fight really cost three.
        assertEquals(1, potion(3).consumed(2).charges());
        assertEquals(3, potion(3).consumed(0).charges(), "a potion that never fired is untouched");
        assertEquals(0, potion(3).consumed(3).charges());
    }

    @Test
    void consumedFloorsAtZeroAndNeverGoesNegative() {
        // The floor that keeps `charges > 0` a meaningful "can this still fire" check forever — the same
        // guarantee Weapon.worn()/Pet.worn() give their own counts.
        assertEquals(0, potion(3).consumed(5).charges());
        assertEquals(0, potion(0).consumed(1).charges(), "an already-spent potion stays at 0");
    }

    @Test
    void bothCopiesChangeNothingButTheChargeCount() {
        Skill original = potion(3);
        Skill consumed = original.consumed(1);
        Skill recharged = original.withCharges(9);

        assertEquals(original, consumed.withCharges(3), "consumed() differs from the original in charges alone");
        assertEquals(original, recharged.withCharges(3), "and so does withCharges()");
        assertEquals(100, consumed.restoreAmount(), "potency is the potion's own, unaffected by drinking it");
        assertEquals(50, consumed.thresholdPercent());
        assertEquals(ResourceType.HEALTH, consumed.restoresResource());
    }

    @Test
    void withChargesCarriesTheInventoryValueVerbatim() {
        // The battle-setup half: whatever Inventory says is what combat gets, including 0 (a spent potion
        // whose Loadout copy still claims 5) and including a value above the equipped copy's.
        assertEquals(0, potion(5).withCharges(0).charges());
        assertEquals(7, potion(5).withCharges(7).charges());
    }
}
