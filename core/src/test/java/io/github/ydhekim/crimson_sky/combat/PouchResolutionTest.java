package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Tameness;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story A7: priority-ordered weapon/skill pouches drawn from by Stamina/Mana affordability, plus the
 * A2 pet check firing independently of a Burned cast. All fixtures use the exact
 * {@code docs/planning/04-starter-content.md} numbers and a fixed seed (42, first d100 draw = 31) so
 * every case is reproducible.
 *
 * <p>Pouches are deliberately ordered <b>heaviest/most-expensive first</b> so that lowering the
 * available pool walks the selection down the list — the observable "pouch rotates as the resource
 * depletes" behavior (§4.4). The single gate roll always reads slot 0 (§4.4), so the top item's
 * reliability decides whether the branch fires; STR/WIS are set high enough that slot 0's gate
 * succeeds under seed 42, isolating the affordability walk as the thing under test.
 */
class PouchResolutionTest {

    private static final long SEED = 42L;

    // --- starter-content fixtures (04-starter-content.md) --------------------------------------

    // Durability is full (20/20) throughout: these cases isolate the *Stamina* walk, so no weapon may be
    // skipped for being broken (§17). The one durability-driven case sets it explicitly, below.
    private static Weapon twinDaggers() {
        return new Weapon(1L, "Twin Daggers", "", Rarity.COMMON, 2f, 8, 18, 8, 20, 20);
    }

    private static Weapon steelLongsword() {
        return new Weapon(2L, "Steel Longsword", "", Rarity.UNCOMMON, 15f, 12, 28, 15, 20, 20);
    }

    private static Weapon warhammer() {
        return new Weapon(3L, "Warhammer", "", Rarity.RARE, 40f, 15, 45, 25, 20, 20);
    }

    private static Skill spark() {
        return new Skill(1L, "Spark", "", SkillType.ACTIVE, 12, Difficulty.EASY, 20, 40, null, 0, null);
    }

    private static Skill lightningBolt() {
        return new Skill(2L, "Lightning Bolt", "", SkillType.ACTIVE, 28, Difficulty.MEDIUM, 30, 60, null, 0, null);
    }

    private static Skill fireball() {
        return new Skill(3L, "Fireball", "", SkillType.ACTIVE, 45, Difficulty.HARD, 45, 75, null, 0, null);
    }

    private static Skill meteor() {
        return new Skill(4L, "Meteor", "", SkillType.ACTIVE, 70, Difficulty.MYTHIC, 70, 110, null, 0, null);
    }

    /** Stats(str, dex, vit, int, wis, spi, spd, ins). */
    private static Stats stats(int str, int wis, int ins) {
        return new Stats(str, 30, 50, 50, wis, 50, 50, ins);
    }

    private static SplittableRandom rng() {
        return new BattleSession(SEED).rng();
    }

    // --- weapon pouch: Stamina-driven priority walk (heaviest-first) ---------------------------

    private static Array<Weapon> heavyFirstPouch() {
        return Array.with(warhammer(), steelLongsword(), twinDaggers()); // sc 25 / 15 / 8
    }

    @Test
    void weaponPouch_usesTopPriorityWeaponWhenAffordable() {
        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(80, 20, 10), heavyFirstPouch(), new Array<>(), 0, 100, rng());

        // STR 80 vs Warhammer weight 40 → no penalty; roll 31 < 80 draws; 25 stamina ≤ 100 → slot 0.
        assertEquals(ActionSource.WEAPON, action.source());
        assertEquals("Warhammer", action.label());
    }

    @Test
    void weaponPouch_fallsThroughToNextAffordableWeaponOnStamina() {
        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(80, 20, 10), heavyFirstPouch(), new Array<>(), 0, 20, rng());

        // Warhammer (25) unaffordable at 20 stamina → walk to Steel Longsword (15), no re-roll.
        assertEquals(ActionSource.WEAPON, action.source());
        assertEquals("Steel Longsword", action.label());
    }

    @Test
    void weaponPouch_usesCheapestWeaponWhenOnlyItIsAffordable() {
        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(80, 20, 10), heavyFirstPouch(), new Array<>(), 0, 10, rng());

        // Only Twin Daggers (8) affordable at 10 stamina.
        assertEquals(ActionSource.WEAPON, action.source());
        assertEquals("Twin Daggers", action.label());
    }

    @Test
    void weaponPouch_nothingAffordable_fallsBackToPunch() {
        // 5 stamina affords no weapon; empty skill pouch → cascade bottoms out at Punch (0 cost).
        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(80, 20, 10), heavyFirstPouch(), new Array<>(), 0, 5, rng());

        assertEquals(new ResolvedAction(ActionSource.PUNCH, "Punch", 1, false, 0L), action);
    }

    // --- weapon pouch: durability rotates it exactly like Stamina does (§17) --------------------

    @Test
    void weaponPouch_skipsABrokenWeaponAndRotatesToTheNext() {
        // Ample Stamina for all three, so Stamina cannot explain the choice: the Warhammer is skipped
        // purely because it is broken (0 durability), and slot 0's gate roll still governs (§4.4).
        Array<Weapon> pouch = Array.with(
            warhammer().withCurrentDurability(0), steelLongsword(), twinDaggers());

        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(80, 20, 10), pouch, new Array<>(), 0, 100, rng());

        assertEquals(ActionSource.WEAPON, action.source());
        assertEquals("Steel Longsword", action.label(), "a broken weapon is skipped like an unaffordable one");
        assertEquals(2L, action.itemId(), "the entry names which weapon actually fired (§17)");
    }

    @Test
    void weaponPouch_allWeaponsBroken_fallsBackToPunch() {
        // Every weapon broken, Stamina untouched at 100 → the branch falls through exactly as it does
        // when nothing is affordable, with no separate "does nothing" outcome (§17).
        Array<Weapon> pouch = Array.with(
            warhammer().withCurrentDurability(0),
            steelLongsword().withCurrentDurability(0),
            twinDaggers().withCurrentDurability(0));

        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(80, 20, 10), pouch, new Array<>(), 0, 100, rng());

        assertEquals(new ResolvedAction(ActionSource.PUNCH, "Punch", 1, false, 0L), action);
    }

    // --- skill pouch: Mana-driven priority walk (most-expensive-first) -------------------------

    private static Array<Skill> expensiveFirstPouch() {
        return Array.with(meteor(), fireball(), lightningBolt(), spark()); // mc 70 / 45 / 28 / 12
    }

    @Test
    void skillPouch_usesTopPrioritySkillWhenAffordable() {
        // No weapons → weapon branch skipped (no roll). WIS 100 vs Meteor MYTHIC (−35) → roll 31 < 65.
        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(5, 100, 10), new Array<>(), expensiveFirstPouch(), 200, Integer.MAX_VALUE, rng());

        assertEquals(ActionSource.SKILL, action.source());
        assertEquals("Meteor", action.label());
        assertFalse(action.failed());
    }

    @Test
    void skillPouch_fallsThroughToNextAffordableSkillOnMana() {
        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(5, 100, 10), new Array<>(), expensiveFirstPouch(), 50, Integer.MAX_VALUE, rng());

        // Meteor (70) unaffordable at 50 mana → walk to Fireball (45), no re-roll.
        assertEquals(ActionSource.SKILL, action.source());
        assertEquals("Fireball", action.label());
    }

    @Test
    void skillPouch_usesCheapestSkillWhenOnlyItIsAffordable() {
        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(5, 100, 10), new Array<>(), expensiveFirstPouch(), 15, Integer.MAX_VALUE, rng());

        // Only Spark (12) affordable at 15 mana.
        assertEquals(ActionSource.SKILL, action.source());
        assertEquals("Spark", action.label());
    }

    @Test
    void skillPouch_nothingAffordable_burnsCastOnSlotZero() {
        // 5 mana affords nothing; gate roll on slot 0 still succeeded → Burned cast (§4.4).
        ResolvedAction action = ActionResolver.resolveCharacterAction(
            stats(5, 100, 10), new Array<>(), expensiveFirstPouch(), 5, Integer.MAX_VALUE, rng());

        assertEquals(
            new ResolvedAction(ActionSource.SKILL, ActionResolver.FAILED_CAST_LABEL, 1, true, 0L), action);
        assertTrue(action.failed());
    }

    // --- pet check fires independently of a Burned cast (A2, GDD Scenario 3) --------------------

    @Test
    void petActs_evenWhenCharacterCastIsBurned() {
        // One shared RNG, in BattleEngine's decision order: character action first, then the pet.
        SplittableRandom rng = rng();
        Stats stats = stats(5, 80, 90); // WIS 80 → Spark EASY gate succeeds (31 < 80); mana 0 → Burned

        ResolvedAction character = ActionResolver.resolveCharacterAction(
            stats, new Array<>(), Array.with(spark()), 0, Integer.MAX_VALUE, rng);
        assertTrue(character.failed(), "empty mana must Burn the cast");
        assertEquals(ActionSource.SKILL, character.source());

        // LOYAL Bear (+20) with Insight 90 → effectiveInsight 110 > 100 → the pet always acts,
        // proving the Insight check runs regardless of the character's Burned outcome.
        Pet bear = new Pet(1L, "Bear", "", Tameness.LOYAL, 80, 15, 20, 36);
        ResolvedAction pet = PetResolver.resolvePetAction(stats, bear, rng);

        assertNotNull(pet, "pet must still act after a Burned cast");
        assertEquals(ActionSource.PET, pet.source());
        assertEquals("Bear", pet.label());
    }
}
