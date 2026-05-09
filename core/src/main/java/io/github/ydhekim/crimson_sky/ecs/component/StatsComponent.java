package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Stats;

public class StatsComponent implements Component, Poolable {
    public Stats stats;

    @Override
    public void reset() {
        stats = null;
    }
}
