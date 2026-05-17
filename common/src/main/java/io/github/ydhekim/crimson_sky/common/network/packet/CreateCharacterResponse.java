package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Character;

public record CreateCharacterResponse(
    boolean success,
    String message,
    Character character
) {
}
