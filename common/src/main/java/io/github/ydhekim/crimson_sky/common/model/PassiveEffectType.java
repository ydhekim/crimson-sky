package io.github.ydhekim.crimson_sky.common.model;

/**
 * What a {@code PASSIVE}-type {@link Skill} does when equipped (system design §16). A deliberately
 * bounded, closed set rather than a generic modifier system — every value maps onto a formula the
 * combat engine already has, so a passive is auditable and read once at
 * {@code BattleParticipant.fromCharacter()}, not via scattered live lookups.
 *
 * <p>{@link #STAT_BONUS} folds its magnitude into the named {@link StatName} of the character's
 * {@code Stats} block; {@link #DODGE_CHANCE_BONUS}/{@link #CRIT_CHANCE_BONUS} accumulate into the
 * flat evasion/crit knobs consumed by {@code DamageCalculator} (§4.2/§14).
 * {@link #RESOURCE_COST_REDUCTION} and {@link #WEIGHT_CAPACITY_BONUS} are valid values reserved for
 * future content (Epic N / resource economy) — no v1.0 tree node grants them and no combat code reads
 * them yet.
 */
public enum PassiveEffectType {
    STAT_BONUS, DODGE_CHANCE_BONUS, CRIT_CHANCE_BONUS, RESOURCE_COST_REDUCTION, WEIGHT_CAPACITY_BONUS
}
