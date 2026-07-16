package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Tameness;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System design §18 — <b>{@code Inventory} is the single source of truth for pet health</b>, exactly as
 * §17 already settled for weapon durability. The sibling of {@code BattleParticipantDurabilityTest}, and
 * for the same reason: {@code Loadout} and {@code Inventory} hold independent copies of the same pet
 * record, {@code saveLoadout} lets a client submit its own {@code Loadout} copy at any time, and the
 * post-battle decrement only ever writes {@code Inventory} — so if combat read health off the equipped
 * copy, a client could hand back a pristine pet the database knows is worn out.
 *
 * <p>Every case below makes the two copies disagree on purpose; if they agreed, nothing here would be
 * proving anything.
 */
class BattleParticipantPetHealthTest {

    /** Insight 90 + LOYAL (+20) → effectiveInsight 110: a healthy pet always acts, so only health decides. */
    private static final Stats HIGH_INSIGHT = new Stats(10, 10, 10, 10, 10, 10, 10, 90);

    private static Pet bear(int currentHealth) {
        return new Pet(1L, "Bear", "", Tameness.LOYAL, 80, 15, 20, 36, currentHealth);
    }

    private static Character character(Array<Pet> owned, Array<Pet> equipped) {
        return new Character(1L, 1L, "Ayla", Faction.A, 1, 0, 100, 100, 100, 0, 0,
            HIGH_INSIGHT,
            new Inventory(new Array<>(), new Array<>(), owned, new HashMap<>()),
            new Loadout(new Array<Weapon>(), new Array<Skill>(), equipped),
            new HashMap<>());
    }

    private static BattleParticipant participant(Character character) {
        return BattleParticipant.fromCharacter(new Engine(), character);
    }

    @Test
    void aWornOutInventoryCopyBeatsAFullHealthLoadoutCopy() {
        // The whole point: Loadout says 80/80, Inventory says the pet is spent.
        BattleParticipant p = participant(character(Array.with(bear(0)), Array.with(bear(80))));

        assertEquals(0, p.pet().equipped.currentHealth(),
            "combat reads the Inventory copy's health, never the (client-submittable) Loadout copy");
        assertEquals(0, p.pet().currentHealth, "and the battle-scoped copy is seeded from it, not from healthPoint()");
        assertFalse(CombatMath.isPetUsable(p.pet().equipped));
        assertNull(PetResolver.resolvePetAction(p.statsComponent().stats, p.pet().equipped, new SplittableRandom(42L)),
            "and the consequence lands: Insight 110, but a worn-out pet never acts (§18)");
    }

    @Test
    void aWornInventoryCopyIsCarriedThroughExactly() {
        // Not just "0 wins" — the real value transfers, so a pet 74 battles in fights at 6, not 80.
        BattleParticipant p = participant(character(Array.with(bear(6)), Array.with(bear(80))));

        assertEquals(6, p.pet().equipped.currentHealth());
        assertEquals(6, p.pet().currentHealth);
        assertEquals(80, p.pet().equipped.healthPoint(), "max health is the pet's spec, not its wear");
        assertNotNull(PetResolver.resolvePetAction(p.statsComponent().stats, p.pet().equipped, new SplittableRandom(42L)),
            "still has health left → still acts");
    }

    @Test
    void anEquippedPetMissingFromInventoryKeepsItsOwnHealth() {
        // The documented fallback, and the case a BotFactory bot legitimately hits: a synthesized opponent
        // has a loadout and an empty inventory, and must not be handed a 0-health pet by default.
        BattleParticipant p = participant(character(new Array<>(), Array.with(bear(80))));

        assertEquals(80, p.pet().equipped.currentHealth(), "no inventory entry to cross-reference → keep own value");
        assertTrue(CombatMath.isPetUsable(p.pet().equipped), "a bot never starts a fight with a worn-out pet");
    }
}
