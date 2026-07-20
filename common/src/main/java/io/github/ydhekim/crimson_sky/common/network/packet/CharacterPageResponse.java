package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.CharacterPage;

public record CharacterPageResponse(boolean success, String message, CharacterPage page) {
}
