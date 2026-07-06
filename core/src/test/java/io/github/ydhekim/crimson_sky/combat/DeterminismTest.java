package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.ecs.component.CharacterActionComponent;
import io.github.ydhekim.crimson_sky.ecs.component.ManaComponent;
import io.github.ydhekim.crimson_sky.ecs.component.StatsComponent;
import io.github.ydhekim.crimson_sky.ecs.component.WeaponSlotComponent;
import io.github.ydhekim.crimson_sky.ecs.system.ActionResolutionSystem;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Proves story A4: a battle seeded from {@link BattleSession} reproduces the same outcome for the
 * same inputs. Covers both the pure {@link ActionResolver} and the full Ashley
 * {@link ActionResolutionSystem} wired into an {@link Engine} (headless — no LibGDX application or GL
 * context is created).
 */
class DeterminismTest {

    private static final long SEED = 123_456_789L;

    private static Stats sampleStats() {
        // STR 65 → the weapon draw is a genuine gate, so the seed actually drives the outcome
        // (rather than the branch being forced), making reproducibility a meaningful assertion.
        return new Stats(65, 60, 20, 20, 40, 50, 50, 30);
    }

    private static Weapon sampleWeapon() {
        return new Weapon(1L, "Hammer", "A heavy hammer", Rarity.COMMON, 5.0f, 40);
    }

    private static Skill sampleSkill() {
        return new Skill(1L, "Fireball", "A ball of fire", SkillType.ACTIVE, 20, Difficulty.MEDIUM);
    }

    @Test
    void resolver_sameSeedAndInputs_producesIdenticalResults() {
        ResolvedAction first = ActionResolver.resolveCharacterAction(
            sampleStats(), sampleWeapon(), sampleSkill(), 100, new BattleSession(SEED).rng());
        ResolvedAction second = ActionResolver.resolveCharacterAction(
            sampleStats(), sampleWeapon(), sampleSkill(), 100, new BattleSession(SEED).rng());

        assertEquals(first, second, "same seed + same inputs must yield the same resolved action");
    }

    @Test
    void system_sameSeed_producesIdenticalResults() {
        ResolvedAction first = resolveThroughEngine(SEED);
        ResolvedAction second = resolveThroughEngine(SEED);

        assertEquals(first, second, "the ECS system must be reproducible for a given battle seed");
    }

    @Test
    void differentSeeds_canDivergeButRemainStablePerSeed() {
        // Each seed is internally reproducible; re-running a seed always returns its own result.
        assertEquals(resolveThroughEngine(1L), resolveThroughEngine(1L));
        assertEquals(resolveThroughEngine(2L), resolveThroughEngine(2L));
    }

    /**
     * Builds a fresh engine with one combatant and the {@link ActionResolutionSystem} seeded from a
     * {@link BattleSession}, ticks it once (one turn), and returns the resolved character action.
     */
    private static ResolvedAction resolveThroughEngine(long seed) {
        Engine engine = new Engine();
        SplittableRandom rng = new BattleSession(seed).rng();
        engine.addSystem(new ActionResolutionSystem(rng));

        Entity entity = engine.createEntity();

        StatsComponent statsComponent = engine.createComponent(StatsComponent.class);
        statsComponent.stats = sampleStats();
        entity.add(statsComponent);

        ManaComponent manaComponent = engine.createComponent(ManaComponent.class);
        manaComponent.maxMana = 100;
        manaComponent.currentMana = 100;
        entity.add(manaComponent);

        WeaponSlotComponent weaponSlot = engine.createComponent(WeaponSlotComponent.class);
        weaponSlot.equipped = sampleWeapon();
        entity.add(weaponSlot);

        engine.addEntity(entity);
        engine.update(1f / 60f); // delta is ignored by the discrete, turn-based system

        CharacterActionComponent result = entity.getComponent(CharacterActionComponent.class);
        // The system must always write a concrete action (weapon, skill, or punch) for a turn.
        assertNotNull(result, "the system must attach a CharacterActionComponent");
        assertNotNull(result.action, "one turn must always resolve to a concrete action");
        return result.action;
    }
}
