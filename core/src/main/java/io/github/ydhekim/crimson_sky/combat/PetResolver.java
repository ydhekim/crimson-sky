package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Stats;

import java.util.SplittableRandom;

/**
 * Pure implementation of the pet's independent Insight check (GDD §3 Step 2, story A2). Runs
 * <b>regardless</b> of the character-action outcome — a pet still acts even when the character's cast
 * was Burned (GDD Scenario 3) — so {@link BattleEngine} always calls this after the character action,
 * consuming exactly one gate roll when a pet is present (system design §4.3).
 *
 * <p>One d100 gate roll vs {@code effectiveInsight = insight + tamenessModifier(pet)}; on success the
 * pet contributes {@code 1 + effectiveInsight/30} hits, each dealing {@code randomInt(minAttack,
 * maxAttack)} with <b>no</b> stat bonus (self-contained, §4.2). Returns {@code null} when the pet
 * does not act (roll failed, or no pet) — nothing is appended to the Result Set.
 */
public final class PetResolver {

    private PetResolver() {
    }

    /** Story-level API: the pet's {@link ResolvedAction} for this turn, or {@code null} if it didn't act. */
    public static ResolvedAction resolvePetAction(Stats stats, Pet pet, SplittableRandom rng) {
        PetActionResolution resolution = choosePetAction(stats, pet, rng);
        return resolution == null ? null : resolution.action();
    }

    /**
     * Decision layer used by {@link BattleEngine} (carries the pet's damage range). {@code null} when
     * the pet does not act. No gate roll is consumed when {@code pet == null} (no pet, nothing to roll).
     */
    static PetActionResolution choosePetAction(Stats stats, Pet pet, SplittableRandom rng) {
        if (pet == null) {
            return null;
        }
        int effectiveInsight = CombatMath.effectiveInsight(stats.insight(), pet);
        if (rng.nextInt(CombatMath.ROLL_BOUND) < effectiveInsight) {
            ResolvedAction action = new ResolvedAction(
                ActionSource.PET, pet.name(), CombatMath.frequency(effectiveInsight), false);
            return new PetActionResolution(action, pet.minAttack(), pet.maxAttack());
        }
        return null;
    }
}
