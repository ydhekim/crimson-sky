package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.AccountSettings;

public record SaveAccountSettingsRequest(
    AccountSettings accountSettings
) {
}
