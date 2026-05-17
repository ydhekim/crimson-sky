package io.github.ydhekim.crimson_sky.common.network.packet;

public record DeleteCharacterResponse(
    boolean success,
    String message,
    String deletedCharacterName
) {
}
