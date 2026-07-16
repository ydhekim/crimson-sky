package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to fully restore one owned pet back to its {@code healthPoint} (system design
 * §18) — the pet-side mirror of {@link RepairWeaponRequest}, deliberately identical in shape because the
 * underlying wear mechanic is.
 *
 * <p>{@code useToken == false} pays {@code 5 gold × missingPetHealth}; {@code true} redeems one Pet Care
 * Kit instead, at no gold cost.
 */
public record RepairPetRequest(long characterId, long petId, boolean useToken) {
}
