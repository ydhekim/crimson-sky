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

    /** Flat multiplier a critical hit applies to a hit's final, post-mitigation damage (§14). */
    static final float CRIT_MULTIPLIER = 1.5f;

    /** The top-of-range fraction a natural crit fires above: the top 20% of the item's range (§14). */
    static final double CRIT_RANGE_THRESHOLD = 0.8;

    private DamageCalculator() {
    }

    /**
     * True if the defender evades this hit (§4.2/§14): {@code dodgeChance = min(75, round(speed*0.75)
     * + dodgeChanceBonus)}, d100. {@code dodgeChanceBonus} is the defender's aggregate
     * {@code DODGE_CHANCE_BONUS} passive total (0 when none equipped) — added before the 75% ceiling so
     * the global cap stays meaningful (§14).
     */
    public static boolean rollDodge(int defenderSpeed, int dodgeChanceBonus, SplittableRandom rng) {
        int dodgeChance = Math.min(MAX_DODGE_CHANCE, Math.round(defenderSpeed * 0.75f) + dodgeChanceBonus);
        return rng.nextInt(CombatMath.ROLL_BOUND) < dodgeChance;
    }

    /**
     * Final post-mitigation damage of one landed hit (§4.2/§14):
     * {@code raw = randomInt(min,max) + floor(pathStat*0.5)}, then
     * {@code round(raw * itemPower/(itemPower + defenderBaseDef))} where {@code itemPower = (min+max)/2},
     * finally ×{@value #CRIT_MULTIPLIER} if the hit crits. {@code pathStatValue} is 0 for pet hits
     * (self-contained, no stat bonus).
     *
     * <p>A hit crits if it {@linkplain #isNaturalCrit naturally} lands in the top band of the range, or
     * — only when the attacker's aggregate {@code critChanceBonus} is positive — an extra d100 forces
     * one (Crimson's faction skill, §14). The forced-crit roll is <b>skipped entirely</b> when
     * {@code critChanceBonus == 0}, so a fight with no crit passive consumes RNG identically to before
     * this mechanic existed.
     */
    public static int rollHitDamage(int minAttack, int maxAttack, int pathStatValue,
                                    int defenderBaseDef, int critChanceBonus, SplittableRandom rng) {
        int rawRoll = randomInt(minAttack, maxAttack, rng);
        int rawDamage = rawRoll + CombatMath.statBonus(pathStatValue);
        int itemPower = CombatMath.itemPower(minAttack, maxAttack);
        double mitigationFactor = (double) itemPower / (itemPower + defenderBaseDef);
        int finalDamage = (int) Math.round(rawDamage * mitigationFactor);

        boolean crit = isNaturalCrit(rawRoll, minAttack, maxAttack)
            || (critChanceBonus > 0 && rng.nextInt(CombatMath.ROLL_BOUND) < critChanceBonus);
        return crit ? Math.round(finalDamage * CRIT_MULTIPLIER) : finalDamage;
    }

    /**
     * True when {@code rawRoll} lands in the top {@code (1 - 0.8) = 20%} band of {@code [min, max]} (§14).
     * Explicitly guarded to never fire on a zero-width range ({@code max <= min}): a flat-damage
     * weapon/skill has no "top band", and without this guard every flat-damage fixture in the suite
     * would crit on every hit.
     */
    static boolean isNaturalCrit(int rawRoll, int minAttack, int maxAttack) {
        if (maxAttack <= minAttack) {
            return false; // a flat range has no "top band" — see the guard note above
        }
        double threshold = minAttack + CRIT_RANGE_THRESHOLD * (maxAttack - minAttack);
        return rawRoll >= threshold;
    }

    /**
     * Inclusive uniform draw in {@code [min, max]}. When {@code min == max} it draws {@code nextInt(1)}
     * (always 0) and returns {@code min}, keeping the per-hit RNG draw count uniform across all hits.
     */
    static int randomInt(int min, int max, SplittableRandom rng) {
        return min + rng.nextInt(max - min + 1);
    }
}
