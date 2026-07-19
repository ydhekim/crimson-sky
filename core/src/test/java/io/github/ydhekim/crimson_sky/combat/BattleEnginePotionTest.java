package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.ResourceType;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Tameness;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story O2 / system design §18 — the potion check inside a real turn: a potion below its threshold fires
 * <b>in place of</b> the whole weapon/skill cascade, heals a flat amount, burns exactly one charge, and
 * leaves the pet's independent action alone.
 *
 * <p>Every fixture here is built so the outcome is a fact of the fixture and not of the seed: STR 100 makes
 * the weapon draw certain (so "the weapon did not fire" can only mean the potion pre-empted it), DEX 0 and
 * SPD 0 make each hit single and undodgeable, and a LOYAL pet at Insight 90 gates at 110 (so "the pet still
 * acted" is likewise not luck).
 */
class BattleEnginePotionTest {

    private static final long SEED = 42L;
    private static final long POTION_ID = 100L;

    /** 100–100 flat damage, weight 2 (no STR penalty), full durability — it fires unless something stops it. */
    private static Weapon hammer() {
        return new Weapon(1L, "Hammer", "", Rarity.COMMON, 2f, 100, 100, 5, 20, 20);
    }

    private static Skill healthPotion(int thresholdPercent, int restoreAmount, int charges) {
        return potion(POTION_ID, "Small Health Potion", ResourceType.HEALTH,
            thresholdPercent, restoreAmount, charges);
    }

    private static Skill potion(long id, String name, ResourceType resource, int thresholdPercent,
                                int restoreAmount, int charges) {
        return new Skill(id, name, "", SkillType.CONSUMABLE, 0, Difficulty.EASY, 0, 0,
            null, 0, null, resource, thresholdPercent, restoreAmount, charges);
    }

    /** STR 100 → the weapon always draws; DEX 0 → one hit; SPD 0 → no dodge; INS 90 → a LOYAL pet always acts. */
    private static Stats attackerStats() {
        return new Stats(100, 0, 50, 20, 20, 50, 0, 90);
    }

    /**
     * The attacker: 500 max HP, and a {@code baseDef} high enough that the sparring partner below cannot
     * move its HP at all. Every assertion here is about what the <i>potion</i> did to the attacker's pools,
     * and both combatants resolve a Result Set every turn — so incoming damage would otherwise land in the
     * same numbers the potion is being measured by.
     */
    private static Character attacker(Array<Skill> potions, Array<Pet> pets) {
        return new Character(0, 0, "Attacker", Faction.A, 1, 0,
            500 /* maxHp */, 100 /* maxMp */, 100 /* maxStamina */, HARMLESS_AGAINST_DEF, 0,
            attackerStats(),
            // The potions are owned as well as equipped — the only shape a real character can have, and
            // what makes the charges combat reads the Inventory copy's (§18). Separate Array instances
            // holding equal records, because that is what two independently deserialized JSONB columns are.
            new Inventory(new Array<>(), new Array<>(potions), new Array<>(), new HashMap<>()),
            new Loadout(Array.with(hammer()), new Array<>(potions), pets),
            new HashMap<>());
    }

    /**
     * The attacker's {@code baseDef}. Mitigation is {@code itemPower / (itemPower + baseDef)}, so against
     * the sparring partner's bare fists ({@code itemPower} 3, STR 0 → no stat bonus) even a top-of-range 5
     * mitigates to {@code round(5 × 3/1003) = 0}. Harmless by arithmetic, not by a seed that happened to
     * miss — and it costs the defender no RNG draws, so it perturbs nothing.
     */
    private static final int HARMLESS_AGAINST_DEF = 1000;

    /**
     * The sparring partner: 5000 HP so no turn is ever cut short by a kill, no weapon and STR 0 so it can
     * only punch, and {@code baseDef} 0 so the attacker's own hits (and its pet's) land in full.
     */
    private static Character sparringPartner() {
        return new Character(0, 0, "Defender", Faction.A, 1, 0,
            5000 /* maxHp */, 100, 100, 0 /* baseDef → the attacker's hits land in full */, 0,
            new Stats(0, 0, 50, 0, 0, 50, 0, 0),
            new Inventory(new Array<>(), new Array<>(), new Array<>(), new HashMap<>()),
            new Loadout(new Array<Weapon>(), new Array<Skill>(), new Array<Pet>()),
            new HashMap<>());
    }

    /** An engine whose attacker carries {@code potions} and starts at {@code startHp} of 500. */
    private static Fixture fixture(Array<Skill> potions, Array<Pet> pets, int startHp) {
        Engine engine = new Engine();
        BattleSession session = new BattleSession(SEED);

        BattleParticipant attacker = BattleParticipant.fromCharacter(engine, attacker(potions, pets));
        BattleParticipant defender = BattleParticipant.fromCharacter(engine, sparringPartner());
        session.addParticipant(attacker);
        session.addParticipant(defender);
        attacker.health().currentHealth = startHp;

        return new Fixture(new BattleEngine(engine, session), attacker, defender);
    }

    private record Fixture(BattleEngine engine, BattleParticipant attacker, BattleParticipant defender) {
    }

    private static ResolvedAction only(Array<ResolvedAction> turn, ActionSource source) {
        ResolvedAction found = null;
        for (ResolvedAction action : turn) {
            if (action.source() == source) {
                found = action;
            }
        }
        return found;
    }

    // --- the potion fires in place of the cascade -------------------------------------------------

    @Test
    void aPotionBelowItsThresholdReplacesTheWholeCascade() {
        // 100 HP of 500 is below the 50% threshold, so the potion drinks instead of the (certain) weapon
        // swing. The defender taking no damage is the sharpest form of "the cascade did not run".
        Fixture f = fixture(Array.with(healthPotion(50, 100, 3)), new Array<>(), 100);
        int defenderStartHp = f.defender().health().currentHealth;

        f.engine().resolveTurn();
        Array<ResolvedAction> turn = f.engine().turnHistoryOf(f.attacker()).first();

        assertEquals(1, turn.size, "no pet equipped → the potion is the turn's entire Result Set");
        assertEquals(ActionSource.CONSUMABLE, turn.first().source());
        assertEquals("Small Health Potion", turn.first().label());
        assertEquals(POTION_ID, turn.first().itemId(), "the entry names the potion, for the post-battle tally");
        assertNull(only(turn, ActionSource.WEAPON), "the weapon did not fire, despite STR 100");
        assertNull(only(turn, ActionSource.SKILL));
        assertNull(only(turn, ActionSource.PUNCH), "and it did not fall through to punch either");
        assertEquals(defenderStartHp, f.defender().health().currentHealth,
            "a potion turn deals no damage at all");
    }

    @Test
    void thePotionHealsByExactlyItsRestoreAmount() {
        // Flat, not a roll and not scaled by any stat (§18): 100 HP + a 100 potion = exactly 200.
        Fixture f = fixture(Array.with(healthPotion(50, 100, 3)), new Array<>(), 100);

        f.engine().resolveTurn();

        assertEquals(200, f.attacker().health().currentHealth);
        assertEquals(100, f.engine().turnHistoryOf(f.attacker()).first().first().damage(),
            "the entry's `damage` carries what was restored, for the client to render (§18)");
    }

    @Test
    void healingIsCappedAtMaxHealth() {
        // A 300 potion drunk at 250/500 would overheal to 550. Overhealing is wasted, never banked.
        Fixture f = fixture(
            Array.with(potion(POTION_ID, "Large Health Potion", ResourceType.HEALTH, 50, 300, 3)),
            new Array<>(), 250);

        f.engine().resolveTurn();

        assertEquals(500, f.attacker().health().currentHealth, "capped at max, not 550");
    }

    @Test
    void aPotionAtZeroChargesNeverTriggersHoweverLowTheResource() {
        // 1 HP of 500 — as low as it gets without being dead — and the potion still sits it out, so the
        // certain weapon swing happens instead. There is no repair for a spent potion (§18).
        Fixture f = fixture(Array.with(healthPotion(50, 100, 0)), new Array<>(), 1);

        f.engine().resolveTurn();
        Array<ResolvedAction> turn = f.engine().turnHistoryOf(f.attacker()).first();

        assertEquals(1, f.attacker().health().currentHealth, "no charge → no heal");
        assertNotNull(only(turn, ActionSource.WEAPON), "the cascade ran normally instead");
        assertNull(only(turn, ActionSource.CONSUMABLE));
    }

    // --- charges deplete per trigger, within the battle -------------------------------------------

    @Test
    void eachTriggerBurnsExactlyOneChargeAndTheLastOneRunsOut() {
        // §0/§18's whole reason for a battle-scoped count: a 2-charge potion drinks on turns 1 and 2 and is
        // spent by turn 3, within one fight. A threshold of 100% makes every turn a trigger, so the only
        // thing that can stop the third is the charge count itself.
        Fixture f = fixture(Array.with(healthPotion(100, 1, 2)), new Array<>(), 100);

        f.engine().resolveTurn();
        assertEquals(1, f.attacker().consumables().remainingCharges.get(0), "2 → 1 after the first drink");
        f.engine().resolveTurn();
        assertEquals(0, f.attacker().consumables().remainingCharges.get(0), "1 → 0 after the second");
        f.engine().resolveTurn();
        assertEquals(0, f.attacker().consumables().remainingCharges.get(0), "and it stops at 0, never −1");

        Array<Array<ResolvedAction>> history = f.engine().turnHistoryOf(f.attacker());
        assertEquals(ActionSource.CONSUMABLE, history.get(0).first().source());
        assertEquals(ActionSource.CONSUMABLE, history.get(1).first().source());
        assertEquals(ActionSource.WEAPON, history.get(2).first().source(),
            "turn 3's potion is spent, so the cascade takes the turn back");
    }

    @Test
    void aPotionThatHealsAboveItsThresholdStopsTriggering() {
        // The reactive half: the potion is not on a timer, it watches the pool. One drink at 100/500 lifts
        // HP to 400 — above the 50% threshold — so turn 2 swings the weapon and the charge is untouched.
        Fixture f = fixture(Array.with(healthPotion(50, 300, 3)), new Array<>(), 100);

        f.engine().resolveTurn();
        assertEquals(400, f.attacker().health().currentHealth);
        assertEquals(2, f.attacker().consumables().remainingCharges.get(0), "one drink, one charge");

        f.engine().resolveTurn();
        assertEquals(ActionSource.WEAPON, f.engine().turnHistoryOf(f.attacker()).get(1).first().source(),
            "back above the threshold → the cascade resumes");
        assertEquals(2, f.attacker().consumables().remainingCharges.get(0), "and nothing further is spent");
    }

    // --- the pet is unaffected --------------------------------------------------------------------

    @Test
    void thePetStillActsOnAPotionTurn() {
        // The pet's Step 2 decision has always run independent of the character's outcome — it acts even
        // after a Burned cast — and a potion turn is no different (§18).
        Pet bear = new Pet(7L, "Bear", "", Tameness.LOYAL, 80, 15, 20, 36, 80);
        Fixture f = fixture(Array.with(healthPotion(50, 100, 3)), Array.with(bear), 100);
        int defenderStartHp = f.defender().health().currentHealth;

        f.engine().resolveTurn();
        Array<ResolvedAction> turn = f.engine().turnHistoryOf(f.attacker()).first();

        assertEquals(2, turn.size, "the potion entry plus the pet's");
        assertNotNull(only(turn, ActionSource.CONSUMABLE));
        ResolvedAction pet = only(turn, ActionSource.PET);
        assertNotNull(pet, "Insight 90 + LOYAL (+20) → the pet always acts, potion turn or not");
        assertEquals("Bear", pet.label());
        assertTrue(pet.damage() > 0, "and its hits land for real");
        assertTrue(f.defender().health().currentHealth < defenderStartHp,
            "so a potion turn is not necessarily a damage-free turn — only the character's half is");
        assertEquals(200, f.attacker().health().currentHealth, "while the potion still healed");
    }

    // --- the no-potion case is byte-for-byte unchanged ---------------------------------------------

    @Test
    void aBuildCarryingNoPotionResolvesExactlyAsItDidBeforeO2() {
        // The regression this whole branch has to earn: with no CONSUMABLE equipped the check returns null
        // without touching the RNG, so the cascade's draws are the same ones in the same order. Asserted
        // against a full battle rather than a turn, so any divergence anywhere compounds into the log.
        Array<Array<ResolvedAction>> withoutPouch = runToCompletion(new Array<>());

        assertFalse(withoutPouch.isEmpty(), "precondition: the battle really resolved");
        assertEquals(ActionSource.WEAPON, withoutPouch.first().first().source());
    }

    @Test
    void carryingAPotionThatNeverTriggersChangesNothingAboutTheBattle() {
        // The stronger version, and the one that pins "a potion consumes no RNG" behaviorally: an equipped
        // potion whose threshold is never crossed must produce a battle identical to carrying none at all —
        // same turns, same labels, same damage. A single stray rng draw in the potion path would desync
        // every roll after it and this would fail.
        Array<Array<ResolvedAction>> withoutPotion = runToCompletion(new Array<>());
        Array<Array<ResolvedAction>> withIdlePotion = runToCompletion(
            Array.with(healthPotion(0 /* threshold 0% → never fires above an empty pool */, 100, 3)));

        assertEquals(withoutPotion.size, withIdlePotion.size, "the same number of turns");
        for (int turn = 0; turn < withoutPotion.size; turn++) {
            assertEquals(withoutPotion.get(turn).size, withIdlePotion.get(turn).size);
            for (int i = 0; i < withoutPotion.get(turn).size; i++) {
                assertEquals(withoutPotion.get(turn).get(i), withIdlePotion.get(turn).get(i),
                    "turn " + turn + " entry " + i + " diverged — the potion path perturbed the RNG stream");
            }
        }
    }

    /** A full battle under the fixed seed, returning the attacker's whole turn history. */
    private static Array<Array<ResolvedAction>> runToCompletion(Array<Skill> potions) {
        Fixture f = fixture(potions, new Array<>(), 500);
        f.engine().runToCompletion();
        return f.engine().turnHistoryOf(f.attacker());
    }
}
