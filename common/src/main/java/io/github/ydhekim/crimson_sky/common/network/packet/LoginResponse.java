package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.AccountSettings;

public record LoginResponse(
    boolean success,
    String message,
    long accountId,
    int maxSlots,
    AccountSettings settings
) {
}
