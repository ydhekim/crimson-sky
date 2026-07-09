package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;

import java.util.SplittableRandom;

/**
 * Pure implementation of the character-action half of the GDD §3 cascade (stories A1/A7). Kept free
 * of Ashley and any LibGDX rendering type so it is a plain function of (stats, pouches, seeded RNG)
 * and can be unit-tested headlessly (system design §9). The ECS
 * {@link io.github.ydhekim.crimson_sky.ecs.system.ActionResolutionSystem} feeds this from components;
 * {@link BattleEngine} uses {@link #chooseCharacterAction} directly to also apply damage.
 *
 * <h2>Cascade with priority-ordered pouches (system design §4.1/§4.3/§4.4)</h2>
 * <ol>
 *   <li><b>Weapon branch</b> — if the weapon pouch is non-empty, one d100 gate roll vs
 *       {@code effectiveStrength(str, pouch[0].weight)} (slot 0's weight always gates, §4.4). On
 *       success, walk the pouch by <b>Stamina affordability only</b> ({@code remainingStamina >=
 *       staminaCost}, no re-rolling) and use the first affordable weapon; frequency is
 *       {@code 1 + dexterity/30} (raw DEX). If none affordable, fall through to the skill branch.</li>
 *   <li><b>Skill branch</b> — same shape: one gate roll vs slot 0's {@code effectiveWis}, then walk
 *       by <b>Mana affordability</b> ({@code currentMana >= manaCost}). Frequency uses the
 *       <i>selected</i> skill's {@code effectiveWis}. If none affordable → <b>Burned</b> cast on
 *       slot 0 ({@code failed = true}).</li>
 *   <li><b>Punch fallback</b> — both branches failed or both pouches empty. Punch costs 0 Mana and
 *       0 Stamina and is never blocked by a resource check (§4.2).</li>
 * </ol>
 *
 * <p>RNG governs only the pass/fail of each single gate roll ({@code rng.nextInt(100) < stat}); the
 * pouch walk and frequency are deterministic, so RNG consumption is one roll per attempted branch —
 * unchanged from A1, keeping the existing determinism tests valid.
 */
public final class ActionResolver {

    /** Label shown for a Burned cast (GDD Scenario 3 renders this in place of the skill). */
    public static final String FAILED_CAST_LABEL = "FAILED_CAST";

    /** Punch damage range (§4.2) — the only source with no backing record. */
    static final int PUNCH_MIN = 1;
    static final int PUNCH_MAX = 5;

    private ActionResolver() {
    }

    // --- Public story-level API (returns the plain ResolvedAction) ---------------------------------

    /**
     * Pouch-based resolution (A7). See the class doc for the cascade.
     *
     * @param stats            the character's eight-stat block (STR/DEX/WIS drive this cascade)
     * @param weapons          the priority-ordered weapon pouch (index 0 = tried first); may be empty
     * @param skills           the priority-ordered ACTIVE-skill pouch; may be empty
     * @param currentMana      mana available now, checked per-skill against {@code manaCost()}
     * @param remainingStamina stamina available now, checked per-weapon against {@code staminaCost()}
     * @param rng              the battle's seeded RNG (see {@link BattleSession#rng()})
     */
    public static ResolvedAction resolveCharacterAction(Stats stats, Array<Weapon> weapons,
                                                        Array<Skill> skills, int currentMana,
                                                        int remainingStamina, SplittableRandom rng) {
        return chooseCharacterAction(stats, weapons, skills, currentMana, remainingStamina, rng).action();
    }

    /**
     * Back-compat single-item overload (the A1 degenerate case). Wraps the one weapon/skill into
     * single-element pouches and treats Stamina as unlimited, so the original A1/A6 scenario tests
     * keep the same RNG consumption and outcomes. A {@code null} weapon/skill becomes an empty pouch
     * (that branch is skipped, no roll consumed) — identical to A1's short-circuit behavior.
     */
    public static ResolvedAction resolveCharacterAction(Stats stats, Weapon weapon, Skill skill,
                                                        int currentMana, SplittableRandom rng) {
        Array<Weapon> weapons = new Array<>();
        if (weapon != null) {
            weapons.add(weapon);
        }
        Array<Skill> skills = new Array<>();
        if (skill != null) {
            skills.add(skill);
        }
        return resolveCharacterAction(stats, weapons, skills, currentMana, Integer.MAX_VALUE, rng);
    }

    // --- Decision layer used by BattleEngine (carries damage inputs) -------------------------------

    /**
     * The full cascade, returning the chosen action plus the damage inputs {@link BattleEngine} needs.
     * Package-private: only the same-package battle orchestration consumes the extra fields.
     */
    static CharacterActionResolution chooseCharacterAction(Stats stats, Array<Weapon> weapons,
                                                           Array<Skill> skills, int currentMana,
                                                           int remainingStamina, SplittableRandom rng) {
        // Step 1 — Weapon branch: one gate roll vs slot 0's effectiveStrength.
        if (weapons != null && weapons.size > 0
            && roll(rng) < CombatMath.effectiveStrength(stats.strength(), weapons.get(0).weight())) {
            Weapon chosen = firstAffordableWeapon(weapons, remainingStamina);
            if (chosen != null) {
                ResolvedAction action = new ResolvedAction(
                    ActionSource.WEAPON, chosen.name(), CombatMath.frequency(stats.dexterity()), false);
                return new CharacterActionResolution(
                    action, chosen.minAttack(), chosen.maxAttack(), stats.strength(), chosen.staminaCost());
            }
            // Roll succeeded but nothing affordable → fall through exactly like an empty weapon pouch.
        }

        // Step 2 — Skill branch: one gate roll vs slot 0's effectiveWis.
        if (skills != null && skills.size > 0
            && roll(rng) < CombatMath.effectiveWis(stats.wisdom(), skills.get(0).difficultyToAct())) {
            Skill chosen = firstAffordableSkill(skills, currentMana);
            if (chosen != null) {
                int freq = CombatMath.frequency(
                    CombatMath.effectiveWis(stats.wisdom(), chosen.difficultyToAct()));
                ResolvedAction action = new ResolvedAction(ActionSource.SKILL, chosen.name(), freq, false);
                return new CharacterActionResolution(
                    action, chosen.minAttack(), chosen.maxAttack(), stats.intelligence(), chosen.manaCost());
            }
            // None affordable → Burned cast on slot 0 (single event, no damage applied, nothing spent).
            ResolvedAction burned = new ResolvedAction(ActionSource.SKILL, FAILED_CAST_LABEL, 1, true);
            return new CharacterActionResolution(burned, 0, 0, 0, 0);
        }

        // Step 3 — Punch fallback: always available, 0 cost.
        ResolvedAction punch = new ResolvedAction(ActionSource.PUNCH, "Punch", 1, false);
        return new CharacterActionResolution(punch, PUNCH_MIN, PUNCH_MAX, stats.strength(), 0);
    }

    /** First weapon in priority order the character can still afford from Stamina, or {@code null}. */
    private static Weapon firstAffordableWeapon(Array<Weapon> weapons, int remainingStamina) {
        for (Weapon weapon : weapons) {
            if (CombatMath.isAffordable(weapon, remainingStamina)) {
                return weapon;
            }
        }
        return null;
    }

    /** First skill in priority order the character can currently afford from Mana, or {@code null}. */
    private static Skill firstAffordableSkill(Array<Skill> skills, int currentMana) {
        for (Skill skill : skills) {
            if (CombatMath.isAffordable(skill, currentMana)) {
                return skill;
            }
        }
        return null;
    }

    /** A single d100 draw in [0, 100). Extracted so the roll model lives in exactly one place. */
    private static int roll(SplittableRandom rng) {
        return rng.nextInt(CombatMath.ROLL_BOUND);
    }
}
