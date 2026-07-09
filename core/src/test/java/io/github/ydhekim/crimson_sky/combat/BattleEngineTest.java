package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.ecs.component.TurnResultComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story B2 / system design §4.2: a full two-participant turn resolved through {@link BattleEngine},
 * proving the priority rule — the higher-{@code speed} combatant's Result Set resolves first, and if
 * it kills, the lower-priority combatant's Result Set for that turn <b>never runs</b> (no counter-hit).
 * Headless (no LibGDX application/GL context), seeded for reproducibility.
 */
class BattleEngineTest {

    private static final long SEED = 42L;

    /** Stats(str, dex, vit, int, wis, spi, spd, ins). */
    private static Stats stats(int str, int speed) {
        return new Stats(str, 0 /* dex 0 → frequency 1, one decisive hit */, 50, 20, 20, 50, speed, 0);
    }

    private static Character character(String name, int maxHp, int speed, Array<Weapon> weapons) {
        return new Character(
            0, 0, name, Faction.A, 1, 0,
            maxHp, 100 /* maxMp */, 100 /* maxStamina */, 0 /* baseDef → full damage lands */, 0,
            stats(80 /* STR → weapon draw succeeds */, speed),
            new Inventory(new Array<>(), new Array<>(), new Array<>()),
            new Loadout(weapons, new Array<Skill>(), new Array<>()));
    }

    @Test
    void higherSpeedKillsFirst_lowerPriorityNeverCounters() {
        Engine engine = new Engine();
        BattleSession session = new BattleSession(SEED);

        // A one-shot weapon: 100–100 base + STR bonus, vs the defender's 0 defence and 10 HP.
        Weapon crusher = new Weapon(1L, "Crusher", "", Rarity.RARE, 2f /* light → no STR penalty */,
            100, 100, 5);

        // Attacker is faster (90 vs 0) → acts first. Defender is frail (10 HP) and slow (speed 0 →
        // 0% dodge), so the attacker's single weapon hit is lethal within its own Result Set.
        BattleParticipant attacker = BattleParticipant.fromCharacter(
            engine, character("Attacker", 500, 90, Array.with(crusher)));
        BattleParticipant defender = BattleParticipant.fromCharacter(
            engine, character("Defender", 10, 0, new Array<>()));
        session.addParticipant(attacker);
        session.addParticipant(defender);

        int attackerStartHp = attacker.health().currentHealth;

        BattleEngine battleEngine = new BattleEngine(engine, session);
        BattleParticipant winner = battleEngine.resolveTurn();

        assertTrue(battleEngine.isOver(), "a lethal first Result Set must end the battle");
        assertSame(attacker, winner, "the higher-priority combatant wins");
        assertSame(attacker, battleEngine.priorityOrder().get(0), "higher speed acts first");
        assertTrue(defender.isDefeated(), "defender's HP must be driven to ≤ 0");
        assertEquals(attackerStartHp, attacker.health().currentHealth,
            "defender never acted, so the attacker takes no counter damage");
        assertEquals(1, battleEngine.turnNumber(), "the battle ended on turn 1");

        // The attacker's compiled Result Set records the weapon entry with its applied damage.
        TurnResultComponent result = attacker.turnResult();
        assertEquals(1, result.actions.size, "no pet → a single-entry Result Set");
        assertFalse(result.actions.first().failed());
        assertTrue(result.actions.first().damage() > 0, "the lethal hit's damage is recorded on the entry");

        // Defender never resolved a turn, so it has no Result Set of its own.
        assertEquals(null, defender.turnResult(), "the lower-priority combatant's set never ran");
    }
}
