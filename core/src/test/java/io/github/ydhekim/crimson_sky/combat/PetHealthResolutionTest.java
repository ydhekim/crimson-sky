package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Tameness;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System design §18 — a pet worn out to 0 health <b>simply doesn't act</b>, the same soft, non-blocking
 * treatment §17 gives a broken weapon. Deliberately mirrors {@code CombatMathAffordabilityTest}'s shape,
 * because the mechanic mirrors durability's.
 *
 * <p>The statistical-independence half matters as much as the "it doesn't act" half: a worn-out pet must
 * consume <b>no gate roll</b>, exactly like a build carrying no pet at all. If it burned a roll, every
 * subsequent draw from the battle's shared, seeded RNG would shift, and two otherwise-identical characters
 * would diverge on the strength of a pet that never appeared in the log.
 */
class PetHealthResolutionTest {

    private static final long SEED = 42L;

    /** LOYAL (+20) with Insight 90 → effectiveInsight 110 > 100: a healthy pet here always acts. */
    private static final Stats HIGH_INSIGHT = new Stats(10, 10, 10, 10, 10, 10, 10, 90);

    private static Pet bear(int currentHealth) {
        return new Pet(1L, "Bear", "", Tameness.LOYAL, 80, 15, 20, 36, currentHealth);
    }

    @Test
    void aPetWithHealthLeftIsUsableAndOneAtZeroIsNot() {
        assertTrue(CombatMath.isPetUsable(bear(80)));
        // The boundary that decides whether a pet gets its last battle: the check is > 0, not > 1, so a pet
        // at 1 fights this battle and only then wears to 0.
        assertTrue(CombatMath.isPetUsable(bear(1)));
        assertFalse(CombatMath.isPetUsable(bear(0)), "0 health → worn out (§18)");
    }

    @Test
    void aWornOutPetNeverActsHoweverHighTheInsight() {
        // Insight 110 would otherwise make acting a certainty — so if health didn't gate, this would act.
        assertNull(PetResolver.resolvePetAction(HIGH_INSIGHT, bear(0), new SplittableRandom(SEED)),
            "a pet at 0 health is skipped for the battle, not rolled for (§18)");
    }

    @Test
    void aWornOutPetConsumesNoGateRoll() {
        // Two RNGs from one seed: one asked about a worn-out pet, one never asked at all. If the health
        // check consumed a roll, the next draw off the two streams would differ.
        SplittableRandom askedAboutAWornOutPet = new SplittableRandom(SEED);
        SplittableRandom neverAsked = new SplittableRandom(SEED);

        PetResolver.resolvePetAction(HIGH_INSIGHT, bear(0), askedAboutAWornOutPet);

        assertEquals(neverAsked.nextInt(CombatMath.ROLL_BOUND), askedAboutAWornOutPet.nextInt(CombatMath.ROLL_BOUND),
            "a worn-out pet must perturb the shared RNG stream exactly as much as no pet does: not at all");
    }

    @Test
    void aPetWithHealthLeftBehavesExactlyAsBefore() {
        // The regression half: health gates nothing while there is any, so §4.3's roll is untouched.
        ResolvedAction action = PetResolver.resolvePetAction(HIGH_INSIGHT, bear(1), new SplittableRandom(SEED));

        assertNotNull(action, "one point of health is enough to act");
        assertEquals("Bear", action.label());
        assertEquals(CombatMath.frequency(110), action.frequency(),
            "and its frequency still comes from effectiveInsight alone — health is not a damage dial");
    }
}
