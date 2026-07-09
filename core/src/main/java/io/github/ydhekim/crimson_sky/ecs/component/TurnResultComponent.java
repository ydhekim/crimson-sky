package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;

/**
 * The compiled Result Set for one combatant for the current turn (GDD §3 Step 3): the ordered
 * {@code [character hits..., pet hits...]} array plus the turn it belongs to. Written by
 * {@link io.github.ydhekim.crimson_sky.ecs.system.ResultCompilationSystem} (and by the battle turn
 * resolution, which additionally fills each entry's {@code damage}), consumed by the packet layer
 * into {@code CombatActionResponse.actions()} and then cleared for the next turn (system design §3).
 */
public class TurnResultComponent implements Component, Poolable {
    public final Array<ResolvedAction> actions = new Array<>();
    public long turnNumber;

    @Override
    public void reset() {
        actions.clear();
        turnNumber = 0;
    }
}
