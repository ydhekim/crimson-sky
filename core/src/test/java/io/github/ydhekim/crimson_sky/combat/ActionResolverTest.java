package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encodes the three worked scenarios from Mizan_Combat_Engine_GDD_v4.pdf §5 as fixed-seed unit
 * tests (story A6), plus cascade edge cases. These assert only the <b>character action</b> (A1's
 * {@link ResolvedAction}); the pet column of each scenario (e.g. {@code 2x Wolf}) is story A2 and is
 * intentionally not asserted here.
 *
 * <p>All three scenarios use seed {@code 42}, whose first {@code nextInt(100)} draw is {@code 31} —
 * below STR 70 (S1 weapon draw) and below WIS 80 (S2/S3 skill cast), reproducing each narrative's
 * decisive roll deterministically.
 */
class ActionResolverTest {

    /** Shared scenario seed: first d100 roll is 31 (see class doc). */
    private static final long SCENARIO_SEED = 42L;

    // --- fixtures -----------------------------------------------------------------------------

    /** Stats(strength, dexterity, vitality, intelligence, wisdom, spirit, speed, insight). */
    private static Stats stats(int str, int dex, int intel, int wis, int ins) {
        return new Stats(str, dex, 50, intel, wis, 50, 50, ins);
    }

    private static Weapon weapon(String name) {
        return new Weapon(1L, name, name + " description", Rarity.COMMON, 5.0f, 40);
    }

    private static Skill skill(String name, int manaCost) {
        return new Skill(1L, name, name + " description", SkillType.ACTIVE, manaCost, Difficulty.MEDIUM);
    }

    // --- GDD §5 scenarios ---------------------------------------------------------------------

    @Test
    void scenario1_beastmastersMight_drawsWeaponThreeTimes() {
        // Stats: 70 STR, 60 DEX, 80 INS. Loadout: Hammer (+ Wolf pet, out of scope here).
        Stats stats = stats(70, 60, 20, 20, 80);
        SplittableRandom rng = new BattleSession(SCENARIO_SEED).rng();

        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats, weapon("Hammer"), null, 100, rng);

        // Weapon draw succeeds (roll 31 < STR 70); frequency 1 + 60/30 = 3.
        assertEquals(new ResolvedAction(ActionSource.WEAPON, "Hammer", 3, false), action);
    }

    @Test
    void scenario2_arcaneBurst_castsSkillThreeTimes() {
        // Stats: 90 INT, 80 WIS, 10 INS, no weapon. Loadout: Lightning skill. Ample mana.
        Stats stats = stats(5, 20, 90, 80, 10);
        SplittableRandom rng = new BattleSession(SCENARIO_SEED).rng();

        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats, null, skill("Lightning", 20), 100, rng);

        // No weapon → skill cast; roll 31 < WIS 80, mana ok; frequency 1 + 80/30 = 3.
        assertEquals(new ResolvedAction(ActionSource.SKILL, "Lightning", 3, false), action);
    }

    @Test
    void scenario3_stalledWizard_burnsCastOnEmptyMana() {
        // Stats: high WIS, 90 INS, no weapon. Loadout: Fireball. Mana pool is empty (0).
        Stats stats = stats(5, 20, 20, 80, 90);
        SplittableRandom rng = new BattleSession(SCENARIO_SEED).rng();

        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats, null, skill("Fireball", 30), 0, rng);

        // Skill cast succeeds (roll 31 < WIS 80) but mana validation fails → Burned.
        assertEquals(
            new ResolvedAction(ActionSource.SKILL, ActionResolver.FAILED_CAST_LABEL, 1, true), action);
        assertTrue(action.failed(), "a Burned cast must read as a failure, not a no-op");
    }

    // --- cascade edge cases -------------------------------------------------------------------

    @Test
    void allRollsFail_fallsBackToPunch() {
        // STR 0 and WIS 0 make both rolls impossible to pass regardless of seed → Punch fallback.
        Stats stats = stats(0, 40, 40, 0, 40);
        SplittableRandom rng = new BattleSession(SCENARIO_SEED).rng();

        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats, weapon("Hammer"), skill("Fireball", 10), 100, rng);

        assertEquals(new ResolvedAction(ActionSource.PUNCH, "Punch", 1, false), action);
    }

    @Test
    void manaExactlyEqualToCost_isNotBurned() {
        // WIS 100 makes the skill roll always succeed; mana == cost is sufficient (strict <), so it resolves.
        Stats stats = stats(0, 20, 20, 100, 20);
        SplittableRandom rng = new BattleSession(SCENARIO_SEED).rng();

        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats, null, skill("Heal", 30), 30, rng);

        assertFalse(action.failed(), "mana equal to cost must not burn the cast");
        assertEquals(new ResolvedAction(ActionSource.SKILL, "Heal", 4, false), action);
    }
}
