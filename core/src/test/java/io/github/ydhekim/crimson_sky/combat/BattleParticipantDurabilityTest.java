package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System design §17 — <b>{@code Inventory} is the single source of truth for durability; {@code Loadout}
 * only means "this item id is equipped".</b>
 *
 * <p>This is the test for a decision, not just a code path. {@code Loadout} and {@code Inventory} hold
 * independent copies of the same weapon record, and {@code saveLoadout} lets a client submit its own
 * {@code Loadout} copy at any time — so if combat read durability off the equipped copy, a client could
 * hand back a pristine {@code currentDurability = 20} for a weapon the database knows is broken, and the
 * post-battle decrement (which only ever writes {@code Inventory}) would never catch up. Every case below
 * makes the two copies disagree on purpose; if they agreed, nothing here would be proving anything.
 */
class BattleParticipantDurabilityTest {

    private static Weapon hammer(int currentDurability) {
        return new Weapon(1L, "Hammer", "", Rarity.COMMON, 2f, 10, 20, 5, 20, currentDurability);
    }

    /** STR 100 → the weapon-draw gate always passes, so affordability is the only thing left to decide. */
    private static Character character(Array<Weapon> owned, Array<Weapon> equipped) {
        return new Character(1L, 1L, "Ayla", Faction.A, 1, 0, 100, 100, 100, 0, 0,
            new Stats(100, 0, 10, 10, 10, 10, 0, 0),
            new Inventory(owned, new Array<>(), new Array<>()),
            new Loadout(equipped, new Array<Skill>(), new Array<Pet>()),
            new HashMap<>());
    }

    private static Weapon pouchWeaponOf(Character character) {
        return BattleParticipant.fromCharacter(new Engine(), character).weapons().equipped.first();
    }

    @Test
    void aBrokenInventoryCopyBeatsAPristineLoadoutCopy() {
        // The whole point of §17's resolution: Loadout says 20/20, Inventory says the weapon is broken.
        Weapon pouched = pouchWeaponOf(character(Array.with(hammer(0)), Array.with(hammer(20))));

        assertEquals(0, pouched.currentDurability(),
            "combat reads the Inventory copy's durability, never the (client-submittable) Loadout copy");
        assertFalse(CombatMath.isAffordable(pouched, 100),
            "and the consequence lands: ample Stamina, but a broken weapon can't be drawn (§17)");
    }

    @Test
    void aWornInventoryCopyIsCarriedThroughExactly() {
        // Not just "0 wins" — the real value transfers, so a weapon 14 battles in fights at 6, not 20.
        Weapon pouched = pouchWeaponOf(character(Array.with(hammer(6)), Array.with(hammer(20))));

        assertEquals(6, pouched.currentDurability());
        assertEquals(20, pouched.maxDurability(), "max durability is the item's spec, not its wear");
        assertTrue(CombatMath.isAffordable(pouched, 100), "still has durability left → still drawable");
    }

    @Test
    void anEquippedWeaponMissingFromInventoryKeepsItsOwnDurability() {
        // The documented fallback. saveLoadout already rejects an unowned item, so for a real character
        // this shouldn't happen — but a BotFactory bot legitimately has a loadout and an empty inventory,
        // and it must not be handed a 0-durability weapon by default.
        Weapon pouched = pouchWeaponOf(character(new Array<>(), Array.with(hammer(20))));

        assertEquals(20, pouched.currentDurability(), "no inventory entry to cross-reference → keep own value");
        assertTrue(CombatMath.isAffordable(pouched, 100), "a bot never starts a fight holding a broken weapon");
    }

    @Test
    void aBrokenWeaponIsSkippedForTheNextAffordableOneInThePouch() {
        // End to end through the cascade, not just the component: the participant assembled from storage
        // rotates past its broken top-priority weapon exactly like an unaffordable one (§4.4/§17).
        Weapon spare = new Weapon(2L, "Spare", "", Rarity.COMMON, 2f, 10, 20, 5, 20, 20);
        Character c = character(Array.with(hammer(0), spare), Array.with(hammer(20), spare));

        BattleParticipant p = BattleParticipant.fromCharacter(new Engine(), c);
        var action = ActionResolver.resolveCharacterAction(
            p.statsComponent().stats, p.weapons().equipped, new Array<>(), 0, 100,
            new BattleSession(42L).rng());

        assertEquals("Spare", action.label(), "the broken slot-0 weapon is skipped, the pouch rotates");
        assertEquals(2L, action.itemId());
    }
}
