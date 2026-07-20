package io.github.ydhekim.crimson_sky.common.network.packet;

public record SetEquippedTitleResponse(boolean success, String message, String equippedTitle) {
}
