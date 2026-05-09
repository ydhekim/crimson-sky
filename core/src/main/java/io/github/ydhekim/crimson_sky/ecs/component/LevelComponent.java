package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

public class LevelComponent implements Component, Poolable {
    public int level;
    public long experience;

    @Override
    public void reset() {
        level = 1;
        experience = 0;
    }
}
