package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

public class IdComponent implements Component, Poolable {
    public long id;

    @Override
    public void reset() {
        id = 0;
    }
}
