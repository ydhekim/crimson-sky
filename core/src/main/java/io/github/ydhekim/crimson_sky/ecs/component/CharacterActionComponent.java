package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;

/**
 * Holds the character's resolved action for the current turn, written by
 * {@link io.github.ydhekim.crimson_sky.ecs.system.ActionResolutionSystem} (GDD §3 Step 1). The
 * Result Set compilation step (story A3, {@code ResultCompilationSystem}) will read this alongside
 * the pet action to build the final ordered {@code TurnResultComponent}.
 */
public class CharacterActionComponent implements Component, Poolable {
    public ResolvedAction action;

    @Override
    public void reset() {
        action = null;
    }
}
