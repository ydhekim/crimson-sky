package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Weapon;

/**
 * The ordered weapon "pouch" a character carries into a battle — bridges a {@code Loadout.weapons()}
 * selection into ECS land (system design §3/§4.4). <b>Array index is priority</b>: index 0 is tried
 * first (its weight drives the single weapon-draw gate roll, §4.4), and on a successful roll the
 * resolver walks the pouch by Stamina affordability. Empty (or absent component) means a skill-only
 * build. Read by {@link io.github.ydhekim.crimson_sky.ecs.system.ActionResolutionSystem}.
 */
public class WeaponSlotComponent implements Component, Poolable {
    public final Array<Weapon> equipped = new Array<>();

    @Override
    public void reset() {
        equipped.clear();
    }
}
