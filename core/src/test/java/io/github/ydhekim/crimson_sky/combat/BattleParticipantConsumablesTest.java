package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.PassiveEffectType;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.ResourceType;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.StatName;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * System design §18 — <b>{@code Inventory} is the single source of truth for potion charges</b>, the third
 * item state to follow the rule §17 set for durability and O3 mirrored for pet health. The sibling of
 * {@code BattleParticipantDurabilityTest}/{@code BattleParticipantPetHealthTest}, and for the same reason:
 * {@code Loadout} and {@code Inventory} hold independent copies of the same {@link Skill} record,
 * {@code saveLoadout} lets a client submit its own {@code Loadout} copy at any time, and the post-battle
 * decrement only ever writes {@code Inventory} — so if combat read charges off the equipped copy, a client
 * could hand back a full flask the database knows is empty and drink from it forever.
 *
 * <p>Every cross-reference case below makes the two copies disagree on purpose; if they agreed, nothing
 * here would be proving anything.
 */
class BattleParticipantConsumablesTest {

    private static final Stats BASE = new Stats(10, 10, 10, 10, 10, 10, 10, 10);

    private static Skill potion(long id, String name, int charges) {
        return new Skill(id, name, "", SkillType.CONSUMABLE, 0, Difficulty.EASY, 0, 0,
            null, 0, null, ResourceType.HEALTH, 50, 100, charges);
    }

    private static Skill activeSkill(long id) {
        return new Skill(id, "Spark", "", SkillType.ACTIVE, 12, Difficulty.EASY, 20, 40,
            null, 0, null, null, 0, 0, 0);
    }

    private static Skill passiveSkill(long id) {
        return new Skill(id, "Iron Grip", "", SkillType.PASSIVE, 0, Difficulty.EASY, 0, 0,
            PassiveEffectType.STAT_BONUS, 2, StatName.STRENGTH, null, 0, 0, 0);
    }

    private static Character character(Array<Skill> owned, Array<Skill> equipped) {
        return new Character(1L, 1L, "Ayla", Faction.A, 1, 0, 100, 100, 100, 0, 0, BASE,
            new Inventory(new Array<>(), owned, new Array<>(), new HashMap<>()),
            new Loadout(new Array<Weapon>(), equipped, new Array<Pet>()),
            new HashMap<>());
    }

    private static BattleParticipant participant(Character character) {
        return BattleParticipant.fromCharacter(new Engine(), character);
    }

    @Test
    void aSpentInventoryCopyBeatsAFullLoadoutCopy() {
        // The whole point: Loadout claims 5 charges, Inventory knows the flask is empty.
        BattleParticipant p = participant(character(
            Array.with(potion(100L, "Small Health Potion", 0)),
            Array.with(potion(100L, "Small Health Potion", 5))));

        assertEquals(0, p.consumables().equipped.first().charges(),
            "combat reads the Inventory copy's charges, never the (client-submittable) Loadout copy");
        assertEquals(0, p.consumables().remainingCharges.get(0),
            "and the battle-scoped count is seeded from it, not from the equipped copy");
        assertNull(ConsumableResolver.chooseConsumable(1, 100, 100, 100, 100, 100,
                p.consumables().equipped, p.consumables().remainingCharges),
            "and the consequence lands: 1 HP of 100, but a spent potion never triggers (§18)");
    }

    @Test
    void aPartlyUsedInventoryCopyIsCarriedThroughExactly() {
        // Not just "0 wins" — the real value transfers, so a flask two drinks in starts the fight at 1.
        BattleParticipant p = participant(character(
            Array.with(potion(100L, "Small Health Potion", 1)),
            Array.with(potion(100L, "Small Health Potion", 3))));

        assertEquals(1, p.consumables().equipped.first().charges());
        assertEquals(1, p.consumables().remainingCharges.get(0));
        assertNotNull(ConsumableResolver.chooseConsumable(1, 100, 100, 100, 100, 100,
                p.consumables().equipped, p.consumables().remainingCharges),
            "still has a charge left → still triggers");
    }

    @Test
    void anEquippedPotionMissingFromInventoryKeepsItsOwnCharges() {
        // The documented fallback, and the case a BotFactory bot would hit: a synthesized opponent has a
        // loadout and an empty inventory, and must not be handed an empty flask by default.
        BattleParticipant p = participant(character(
            new Array<>(), Array.with(potion(100L, "Small Health Potion", 2))));

        assertEquals(2, p.consumables().equipped.first().charges(),
            "no inventory entry to cross-reference → keep own value");
        assertEquals(2, p.consumables().remainingCharges.get(0));
    }

    @Test
    void thePouchKeepsTheLoadoutsPriorityOrder() {
        // Index is priority (§18/§4.4), so assembly must not reorder — and each slot's charge count must
        // stay aligned with its own potion, which distinct values are what actually prove.
        Array<Skill> equipped = Array.with(
            potion(100L, "Small Health Potion", 5), potion(101L, "Large Health Potion", 5));
        Array<Skill> owned = Array.with(
            potion(101L, "Large Health Potion", 1), potion(100L, "Small Health Potion", 3));

        BattleParticipant p = participant(character(owned, equipped));

        assertEquals(2, p.consumables().equipped.size);
        assertEquals("Small Health Potion", p.consumables().equipped.get(0).name());
        assertEquals("Large Health Potion", p.consumables().equipped.get(1).name());
        assertEquals(3, p.consumables().remainingCharges.get(0), "matched by id, not by position in inventory");
        assertEquals(1, p.consumables().remainingCharges.get(1));
    }

    @Test
    void eachSkillTypeLandsInExactlyOnePouch() {
        // The routing §4.4/§18 specify: ACTIVE into the priority pouch, CONSUMABLE into its own, PASSIVE
        // into neither (it is folded into stats instead). A potion leaking into the skill pouch would be
        // cast as an attack for 0 damage; an active leaking into the potion pouch would NPE on a null
        // restoresResource the first time a threshold was checked.
        BattleParticipant p = participant(character(
            new Array<>(), Array.with(activeSkill(1L), potion(100L, "Small Health Potion", 2), passiveSkill(2L))));

        assertEquals(1, p.skills().equipped.size, "the ACTIVE skill, and only it");
        assertEquals("Spark", p.skills().equipped.first().name());
        assertEquals(1, p.consumables().equipped.size, "the CONSUMABLE, and only it");
        assertEquals("Small Health Potion", p.consumables().equipped.first().name());
        assertEquals(12, p.statsComponent().stats.strength(), "and the PASSIVE folded into stats: 10 + 2");
    }

    @Test
    void aPotionlessCharacterGetsAnEmptyPouchNotAMissingComponent() {
        // The component is always added (the same convention weaponSlot/skillSlot/petSlot follow), so
        // BattleEngine's potion check needs no null guard — which is the case every existing test hits.
        BattleParticipant p = participant(character(new Array<>(), new Array<>()));

        assertNotNull(p.consumables(), "always added, possibly empty");
        assertEquals(0, p.consumables().equipped.size);
        assertEquals(0, p.consumables().remainingCharges.size);
    }
}
