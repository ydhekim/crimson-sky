package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Loadout;

/**
 * Client → server request to replace a character's equipped {@link Loadout} (system design §4.4/§16).
 * The client submits the whole new loadout each time. The server validates ownership of
 * {@code characterId}, that every weapon/skill/pet id referenced actually exists in the character's
 * current inventory, and that the combined skill slots (ACTIVE + PASSIVE) do not exceed the shared cap
 * of 5, then writes it unconditionally.
 */
public record SaveLoadoutRequest(long characterId, Loadout loadout) {
}
