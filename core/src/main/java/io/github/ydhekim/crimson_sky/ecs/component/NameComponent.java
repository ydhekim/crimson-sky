package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

public class NameComponent implements Component, Poolable {
    public String name;

    @Override
    public void reset() {
        name = null;
    }
}
