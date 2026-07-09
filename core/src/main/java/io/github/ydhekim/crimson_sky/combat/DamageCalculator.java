package io.github.ydhekim.crimson_sky.combat;

import java.util.SplittableRandom;

/**
 * Pure per-hit damage and evasion math (system design §4.2), drawing from the battle's seeded RNG.
 * Deliberately per-hit (not per-entry) because dodge is rolled independently for each individual hit
 * within a frequency count and the ≤0-HP win condition is checked after every hit; {@link BattleEngine}
 * loops over an entry's frequency calling these.
 *
 * <p><b>Fixed RNG order per hit (for reproducibility):</b> {@link #rollDodge} is drawn first; the
 * damage draw in {@link #rollHitDamage} is only consumed when the hit was not dodged.
 */
public final class DamageCalculator {

    /** Dodge is capped so no build is literally unhittable (§4.2). */
    static final int MAX_DODGE_CHANCE = 75;

    private DamageCalculator() {
    }

    /** True if the defender evades this hit: {@code dodgeChance = min(75, round(speed*0.75))}, d100. */
    public static boolean rollDodge(int defenderSpeed, SplittableRandom rng) {
        int dodgeChance = Math.min(MAX_DODGE_CHANCE, Math.round(defenderSpeed * 0.75f));
        return rng.nextInt(CombatMath.ROLL_BOUND) < dodgeChance;
    }

    /**
     * Final post-mitigation damage of one landed hit (§4.2):
     * {@code raw = randomInt(min,max) + floor(pathStat*0.5)}, then
     * {@code round(raw * itemPower/(itemPower + defenderBaseDef))} where {@code itemPower = (min+max)/2}.
     * {@code pathStatValue} is 0 for pet hits (self-contained, no stat bonus).
     */
    public static int rollHitDamage(int minAttack, int maxAttack, int pathStatValue,
                                    int defenderBaseDef, SplittableRandom rng) {
        int rawDamage = randomInt(minAttack, maxAttack, rng) + CombatMath.statBonus(pathStatValue);
        int itemPower = CombatMath.itemPower(minAttack, maxAttack);
        double mitigationFactor = (double) itemPower / (itemPower + defenderBaseDef);
        return (int) Math.round(rawDamage * mitigationFactor);
    }

    /**
     * Inclusive uniform draw in {@code [min, max]}. When {@code min == max} it draws {@code nextInt(1)}
     * (always 0) and returns {@code min}, keeping the per-hit RNG draw count uniform across all hits.
     */
    static int randomInt(int min, int max, SplittableRandom rng) {
        return min + rng.nextInt(max - min + 1);
    }
}
