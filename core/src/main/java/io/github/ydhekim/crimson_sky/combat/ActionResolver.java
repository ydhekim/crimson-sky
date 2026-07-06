package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;

import java.util.SplittableRandom;

/**
 * Pure implementation of the character-action half of the GDD §3 cascade (story A1). Kept free of
 * Ashley and any LibGDX rendering type so it is a plain function of (stats, loadout, seeded RNG) and
 * can be unit-tested headlessly (system design §9). {@link io.github.ydhekim.crimson_sky.ecs.system.ActionResolutionSystem}
 * is the thin ECS wrapper that feeds this from components.
 *
 * <h2>Cascade (GDD §3 Step 1)</h2>
 * <ol>
 *   <li><b>Weapon Draw</b> — roll vs STR. On success, draw the equipped weapon.</li>
 *   <li><b>Skill Cast</b> — if the weapon roll fails, roll vs WIS. On success, validate mana: if
 *       {@code currentMana < skill.manaCost()} the turn is <b>Burned</b>
 *       ({@code failed = true}); otherwise the skill resolves.</li>
 *   <li><b>Fallback</b> — if both rolls fail (or no weapon/skill is equipped for that branch),
 *       the action is a Punch.</li>
 * </ol>
 *
 * <h2>Concrete rules (GDD leaves these open; fixed here, reflected in system design §4)</h2>
 * <ul>
 *   <li><b>Roll model:</b> {@code rng.nextInt(100) < stat} succeeds — a d100 against a 0–100 stat.
 *       RNG governs only pass/fail; frequency is deterministic.</li>
 *   <li><b>Frequency:</b> {@code 1 + stat/30} (integer division), DEX for weapons, WIS for skills.
 *       Reproduces the GDD scenarios: DEX 60 → 3, WIS 80 → 3. Punch is a single fallback strike (1).</li>
 * </ul>
 */
public final class ActionResolver {

    /** d100 threshold: the stat is a success percentage against a roll in [0, 100). */
    private static final int ROLL_BOUND = 100;

    /** Frequency granularity: every {@value} points of the governing stat adds one repeat. */
    private static final int FREQUENCY_STEP = 30;

    /** Label shown for a Burned cast (GDD Scenario 3 renders this in place of the skill). */
    public static final String FAILED_CAST_LABEL = "FAILED_CAST";

    private ActionResolver() {
    }

    /**
     * Resolves the character's action for one turn following the GDD cascade.
     *
     * @param stats       the character's eight-stat block (STR/DEX/WIS drive this cascade)
     * @param weapon      the equipped weapon, or {@code null} if none is equipped
     * @param skill       the equipped skill, or {@code null} if none is equipped
     * @param currentMana the character's current mana pool, checked against the skill's cost
     * @param rng         the battle's seeded RNG (see {@link BattleSession#rng()})
     * @return the resolved character action; never {@code null}
     */
    public static ResolvedAction resolveCharacterAction(Stats stats, Weapon weapon, Skill skill,
                                                        int currentMana, SplittableRandom rng) {
        // Step 1a — Weapon Draw: roll vs STR.
        if (weapon != null && roll(rng) < stats.strength()) {
            return new ResolvedAction(ActionSource.WEAPON, weapon.name(), frequency(stats.dexterity()), false);
        }

        // Step 1b — Skill Cast: on weapon failure, roll vs WIS, then validate mana.
        if (skill != null && roll(rng) < stats.wisdom()) {
            if (currentMana < skill.manaCost()) {
                // Mana validation fails → Burned (GDD Scenario 3). A burned cast is a single event.
                return new ResolvedAction(ActionSource.SKILL, FAILED_CAST_LABEL, 1, true);
            }
            return new ResolvedAction(ActionSource.SKILL, skill.name(), frequency(stats.wisdom()), false);
        }

        // Step 1c — Fallback: all checks failed → Punch.
        return new ResolvedAction(ActionSource.PUNCH, "Punch", 1, false);
    }

    /** A single d100 draw in [0, 100). Extracted so the roll model lives in exactly one place. */
    private static int roll(SplittableRandom rng) {
        return rng.nextInt(ROLL_BOUND);
    }

    /** Number of action repeats derived from the governing frequency stat (DEX or WIS). */
    private static int frequency(int frequencyStat) {
        return 1 + frequencyStat / FREQUENCY_STEP;
    }
}
