package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Inventory;

public class InventoryComponent implements Component, Poolable {
    public Inventory inventory;

    @Override
    public void reset() {
        inventory = null;
    }
}
