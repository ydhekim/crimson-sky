package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Pet;

/**
 * The single pet a character brings into a battle (system design §3). {@code currentHealth} is the
 * pet's battle-scoped HP, kept separate from the immutable {@link Pet} record so damage to the pet
 * never mutates persisted data. Absent/{@code null} {@code equipped} means a pet-less build. Read by
 * {@link io.github.ydhekim.crimson_sky.ecs.system.PetResolutionSystem}.
 *
 * <p><b>Seeded from persisted health since §18</b>, not from {@code healthPoint()}: a pet now wears by 1
 * per battle it acts in, and {@code Inventory} holds that count. Nothing in combat decrements this copy —
 * a pets-can-be-hurt-by-the-opponent mechanic remains explicitly out of scope (§18) — so what changed is
 * only where the starting value comes from, never who writes it mid-battle.
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
