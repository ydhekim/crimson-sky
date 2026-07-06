package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Skill;

/**
 * The single skill a character has equipped for a battle — bridges a {@code Loadout.skills()}
 * selection into ECS land (system design §3). Read by
 * {@link io.github.ydhekim.crimson_sky.ecs.system.ActionResolutionSystem}. May be {@code null} if
 * the character is running a weapon-only build.
 */
public class SkillSlotComponent implements Component, Poolable {
    public Skill equipped;

    @Override
    public void reset() {
        equipped = null;
    }
}
