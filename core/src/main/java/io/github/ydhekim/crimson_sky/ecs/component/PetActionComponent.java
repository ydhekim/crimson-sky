package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;

/**
 * Holds the pet's resolved action for the current turn (GDD §3 Step 2), written by
 * {@link io.github.ydhekim.crimson_sky.ecs.system.PetResolutionSystem} — the mirror of
 * {@link CharacterActionComponent} for the pet column. {@code action} is {@code null} when the pet's
 * independent Insight check failed this turn (or the entity has no pet), meaning nothing is appended
 * to the Result Set. The {@link io.github.ydhekim.crimson_sky.ecs.system.ResultCompilationSystem}
 * reads this alongside {@link CharacterActionComponent} to build the ordered Result Set.
 */
public class PetActionComponent implements Component, Poolable {
    public ResolvedAction action;

    @Override
    public void reset() {
        action = null;
    }
}
