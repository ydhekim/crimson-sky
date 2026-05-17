package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Character;

import java.util.List;

public record CharacterListResponse(
    boolean success,
    String message,
    List<Character> characters,
    int maxCharacterSlots
) {
}
