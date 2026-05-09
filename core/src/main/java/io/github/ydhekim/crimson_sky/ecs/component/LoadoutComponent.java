package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Loadout;

public class LoadoutComponent implements Component, Poolable {
    public Loadout loadout;

    @Override
    public void reset() {
        loadout = null;
    }
}
