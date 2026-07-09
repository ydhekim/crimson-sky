package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Pet;

/**
 * The single pet a character brings into a battle (system design §3). {@code currentHealth} is the
 * pet's battle-scoped HP, kept separate from the immutable {@link Pet} record so damage to the pet
 * never mutates persisted data. Absent/{@code null} {@code equipped} means a pet-less build. Read by
 * {@link io.github.ydhekim.crimson_sky.ecs.system.PetResolutionSystem}.
 */
public class PetSlotComponent implements Component, Poolable {
    public Pet equipped;
    public int currentHealth;

    @Override
    public void reset() {
        equipped = null;
        currentHealth = 0;
    }
}
