package io.github.ydhekim.crimson_sky.server.database.entity;

import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
import org.jdbi.v3.json.Json;

import java.time.Instant;

public record Account(
    long id,
    long userId,
    int maxSlots,
    long globalCurrency,
    @Json AccountSettings settings,
    Instant createdAt
) {
}
