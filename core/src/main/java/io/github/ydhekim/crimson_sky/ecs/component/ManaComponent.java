package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

public class ManaComponent implements Component, Poolable {
    public int maxMana;
    public int currentMana;

    @Override
    public void reset() {
        maxMana = 0;
        currentMana = 0;
    }
}
