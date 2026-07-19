package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Skill;

/**
 * The ordered active-skill "pouch" a character carries into a battle — bridges a
 * {@code Loadout.skills()} selection into ECS land (system design §3/§4.4). <b>Array index is
 * priority</b>: index 0 drives the single skill-cast gate roll; on success the resolver walks the
 * pouch by Mana affordability. Only {@code ACTIVE} skills belong here — {@code PASSIVE} skills sit
 * outside the priority pouch entirely (§4.4), and {@code CONSUMABLE} skills have a pouch of their own
 * ({@link ConsumableSlotComponent}, §18); both must be filtered out before populating this. Read
 * by {@link io.github.ydhekim.crimson_sky.ecs.system.ActionResolutionSystem}.
 */
public class SkillSlotComponent implements Component, Poolable {
    public final Array<Skill> equipped = new Array<>();

    @Override
    public void reset() {
        equipped.clear();
    }
}
