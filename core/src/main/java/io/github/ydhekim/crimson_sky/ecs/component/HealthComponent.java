package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

public class HealthComponent implements Component, Poolable {
    public int maxHealth;
    public int currentHealth;

    @Override
    public void reset() {
        maxHealth = 0;
        currentHealth = 0;
    }
}
