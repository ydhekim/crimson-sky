package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.PassiveEffectType;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Stats;

/**
 * What a character's equipped {@code PASSIVE} skills add up to (system design §16), as pure functions of
 * a {@link Loadout}. Free-standing and Ashley-free in the style of {@link CombatMath}/
 * {@link DamageCalculator}, because the two callers sit on opposite sides of a battle:
 *
 * <ul>
 *   <li>{@code BattleParticipant.fromCharacter} folds these into the battle entity's components once
 *       per battle — the translation boundary §16 specifies;</li>
 *   <li>{@code CharacterService.saveLoadout} needs {@link #totalWeightCapacityBonus} at loadout-save
 *       time (§17's weight gate), a plain server-side call with no Ashley {@code Engine} to build a
 *       participant against.</li>
 * </ul>
 *
 * <p>Extracted rather than duplicated deliberately: the weight-capacity bonus that <i>admits</i> a
 * loadout at save time and the one combat <i>plays</i> must be the same number, and the only way to
 * guarantee that is one formula with one implementation. The same reasoning covers the other three —
 * they are aggregated here even where only one caller reads them today.
 *
 * <p>{@code passiveMagnitude} is already the full rank-scaled value (see {@link Skill}), so every method
 * here is a plain sum with no rank arithmetic. {@code ACTIVE} skills are ignored throughout, as is a
 * malformed passive with no {@code passiveEffect}.
 */
public final class PassiveEffects {

    private PassiveEffects() {
    }

    /** Flat weapon-carry-capacity bonus from equipped passives (§17's {@code maxCarryWeight} term). */
    public static int totalWeightCapacityBonus(Loadout loadout) {
        return sumOf(loadout, PassiveEffectType.WEIGHT_CAPACITY_BONUS);
    }

    /** Flat dodge-chance bonus from equipped passives, added to the defender's evasion roll (§14/§16). */
    public static int totalDodgeChanceBonus(Loadout loadout) {
        return sumOf(loadout, PassiveEffectType.DODGE_CHANCE_BONUS);
    }

    /** Flat crit-chance bonus from equipped passives, added to the attacker's damage roll (§14/§16). */
    public static int totalCritChanceBonus(Loadout loadout) {
        return sumOf(loadout, PassiveEffectType.CRIT_CHANCE_BONUS);
    }

    /** Flat resource-cost reduction from equipped passives. No v1.0 tree node grants it yet (§16). */
    public static int totalResourceCostReduction(Loadout loadout) {
        return sumOf(loadout, PassiveEffectType.RESOURCE_COST_REDUCTION);
    }

    /**
     * {@code base} with every equipped {@code STAT_BONUS} passive folded into its named stat (§16).
     * Unlike the flat knobs above, these land on the stat block itself, so the whole cascade reads them
     * without knowing passives exist. A passive naming no stat is skipped.
     */
    public static Stats applyStatBonuses(Stats base, Loadout loadout) {
        if (base == null || loadout == null || loadout.skills() == null) {
            return base;
        }
        Stats stats = base;
        for (Skill skill : loadout.skills()) {
            if (isPassiveWithEffect(skill, PassiveEffectType.STAT_BONUS) && skill.passiveTargetStat() != null) {
                stats = stats.plus(skill.passiveTargetStat(), skill.passiveMagnitude());
            }
        }
        return stats;
    }

    /** Summed {@code passiveMagnitude} of every equipped passive carrying {@code effect}. */
    private static int sumOf(Loadout loadout, PassiveEffectType effect) {
        if (loadout == null || loadout.skills() == null) {
            return 0;
        }
        int total = 0;
        for (Skill skill : loadout.skills()) {
            if (isPassiveWithEffect(skill, effect)) {
                total += skill.passiveMagnitude();
            }
        }
        return total;
    }

    private static boolean isPassiveWithEffect(Skill skill, PassiveEffectType effect) {
        return skill != null && skill.type() == SkillType.PASSIVE && skill.passiveEffect() == effect;
    }
}
