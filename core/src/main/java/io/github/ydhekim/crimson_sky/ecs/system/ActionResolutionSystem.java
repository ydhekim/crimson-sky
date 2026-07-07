package io.github.ydhekim.crimson_sky.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.github.ydhekim.crimson_sky.combat.ActionResolver;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.ecs.component.CharacterActionComponent;
import io.github.ydhekim.crimson_sky.ecs.component.ManaComponent;
import io.github.ydhekim.crimson_sky.ecs.component.SkillSlotComponent;
import io.github.ydhekim.crimson_sky.ecs.component.StatsComponent;
import io.github.ydhekim.crimson_sky.ecs.component.WeaponSlotComponent;

import java.util.SplittableRandom;

/**
 * Runs the GDD §3 Step 1 character-action cascade for each combatant, storing the outcome on a
 * {@link CharacterActionComponent} (story A1). The cascade logic itself lives in the pure
 * {@link ActionResolver}; this system only bridges ECS components in and out.
 *
 * <p><b>Discrete, not frame-driven.</b> Combat is turn-based — one Result Set per turn — so the
 * owning battle ticks the engine exactly once per turn ({@code engine.update(...)}), not every
 * render frame. The {@code deltaTime} argument is unused; simulation logic never reads render delta
 * (per the fixed-timestep rule in CLAUDE.md).
 *
 * <p><b>RNG.</b> The battle's seeded {@link SplittableRandom} is injected at construction (from
 * {@link io.github.ydhekim.crimson_sky.combat.BattleSession#rng()}), keeping every roll for a battle
 * on one reproducible sequence (story A4).
 */
public class ActionResolutionSystem extends IteratingSystem {

    private final SplittableRandom rng;

    private final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private final ComponentMapper<ManaComponent> manaMapper = ComponentMapper.getFor(ManaComponent.class);
    private final ComponentMapper<WeaponSlotComponent> weaponMapper = ComponentMapper.getFor(WeaponSlotComponent.class);
    private final ComponentMapper<SkillSlotComponent> skillMapper = ComponentMapper.getFor(SkillSlotComponent.class);
    private final ComponentMapper<CharacterActionComponent> actionMapper = ComponentMapper.getFor(CharacterActionComponent.class);

    public ActionResolutionSystem(SplittableRandom rng) {
        // Stats + mana are the minimum the cascade needs; weapon/skill slots are optional per build.
        super(Family.all(StatsComponent.class, ManaComponent.class).get());
        this.rng = rng;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StatsComponent stats = statsMapper.get(entity);
        ManaComponent mana = manaMapper.get(entity);

        WeaponSlotComponent weaponSlot = weaponMapper.get(entity);
        Weapon weapon = weaponSlot != null ? weaponSlot.equipped : null;

        SkillSlotComponent skillSlot = skillMapper.get(entity);
        Skill skill = skillSlot != null ? skillSlot.equipped : null;

        ResolvedAction resolved = ActionResolver.resolveCharacterAction(
            stats.stats, weapon, skill, mana.currentMana, rng);

        CharacterActionComponent actionComponent = actionMapper.get(entity);
        if (actionComponent == null) {
            actionComponent = getEngine().createComponent(CharacterActionComponent.class);
            entity.add(actionComponent);
        }
        actionComponent.action = resolved;
    }
}
