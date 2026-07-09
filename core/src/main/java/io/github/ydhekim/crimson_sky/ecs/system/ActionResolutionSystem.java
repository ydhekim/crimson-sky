package io.github.ydhekim.crimson_sky.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.combat.ActionResolver;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.ecs.component.CharacterActionComponent;
import io.github.ydhekim.crimson_sky.ecs.component.ManaComponent;
import io.github.ydhekim.crimson_sky.ecs.component.SkillSlotComponent;
import io.github.ydhekim.crimson_sky.ecs.component.StaminaComponent;
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
    private final ComponentMapper<StaminaComponent> staminaMapper = ComponentMapper.getFor(StaminaComponent.class);
    private final ComponentMapper<WeaponSlotComponent> weaponMapper = ComponentMapper.getFor(WeaponSlotComponent.class);
    private final ComponentMapper<SkillSlotComponent> skillMapper = ComponentMapper.getFor(SkillSlotComponent.class);
    private final ComponentMapper<CharacterActionComponent> actionMapper = ComponentMapper.getFor(CharacterActionComponent.class);

    /** Empty fallback pouches so an entity with no weapon/skill slot resolves to the punch branch. */
    private static final Array<Weapon> NO_WEAPONS = new Array<>(0);
    private static final Array<Skill> NO_SKILLS = new Array<>(0);

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
        Array<Weapon> weapons = weaponSlot != null ? weaponSlot.equipped : NO_WEAPONS;

        SkillSlotComponent skillSlot = skillMapper.get(entity);
        Array<Skill> skills = skillSlot != null ? skillSlot.equipped : NO_SKILLS;

        // No StaminaComponent ⇒ treat stamina as unlimited, so weapon selection is gated only by the
        // draw roll (the single-cascade decision path used by DeterminismTest; full pools live in a battle).
        StaminaComponent stamina = staminaMapper.get(entity);
        int remainingStamina = stamina != null ? stamina.currentStamina : Integer.MAX_VALUE;

        ResolvedAction resolved = ActionResolver.resolveCharacterAction(
            stats.stats, weapons, skills, mana.currentMana, remainingStamina, rng);

        CharacterActionComponent actionComponent = actionMapper.get(entity);
        if (actionComponent == null) {
            actionComponent = getEngine().createComponent(CharacterActionComponent.class);
            entity.add(actionComponent);
        }
        actionComponent.action = resolved;
    }
}
