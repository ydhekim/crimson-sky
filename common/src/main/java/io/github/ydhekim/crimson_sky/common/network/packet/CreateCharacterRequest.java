package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Appearance;
import io.github.ydhekim.crimson_sky.common.model.Character;

public record CreateCharacterRequest(
    Character character,
    Appearance appearance
) {
}
