package io.github.ydhekim.crimson_sky.common.network.packet;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;

/**
 * Server → client result of one resolved combat turn: the ordered Result Set for {@code turnNumber}
 * (system design §5/§6). {@code actions} is the {@code [character hits..., pet hits...]} array a
 * {@code ResultCompilationSystem}/battle turn produced (A3), consumed by the M4 combat screen.
 */
public record CombatActionResponse(
    long battleId,
    long turnNumber,
    Array<ResolvedAction> actions
) {
}
