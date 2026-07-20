package io.github.ydhekim.crimson_sky.common.network.packet;

/** Client → server request for a character's full page (system design §22): stats, history, achievements. */
public record CharacterPageRequest(long characterId) {
}
