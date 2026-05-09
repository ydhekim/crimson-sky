package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

public class BaseStatsComponent implements Component, Poolable {
    public int baseDefence;
    public int baseAttackPower;

    @Override
    public void reset() {
        baseDefence = 0;
        baseAttackPower = 0;
    }
}
