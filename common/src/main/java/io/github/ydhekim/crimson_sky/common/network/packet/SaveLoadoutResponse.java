package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Loadout;

/**
 * Server → client outcome of a {@link SaveLoadoutRequest} (system design §4.4/§16). On success,
 * {@code savedLoadout} echoes back the persisted loadout. On failure ({@code success == false}),
 * {@code message} carries the {@code MessageCode} name ({@code LOADOUT_ITEM_NOT_OWNED} or
 * {@code LOADOUT_SKILL_SLOTS_EXCEEDED}) and {@code savedLoadout} is {@code null}.
 */
public record SaveLoadoutResponse(boolean success, String message, Loadout savedLoadout) {
}
