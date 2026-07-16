package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Pet;

/**
 * Server → client outcome of a {@link RepairPetRequest} (system design §18). On success,
 * {@code repairedPet} is the stored pet at full health and the two balances are what the character has
 * left afterwards. On failure ({@code success == false}), {@code message} carries the {@code MessageCode}
 * name explaining why, {@code repairedPet} is {@code null}, and the balances are {@code 0}.
 */
public record RepairPetResponse(boolean success, String message, Pet repairedPet,
                                long remainingGold, int remainingPetCareKits) {
}
