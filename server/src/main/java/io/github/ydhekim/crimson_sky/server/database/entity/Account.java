package io.github.ydhekim.crimson_sky.server.database.entity;

import org.jdbi.v3.json.Json;

import java.time.Instant;

public record Account(
    long id,
    long userId,
    int maxSlots,
    long globalCurrency,
    @Json io.github.ydhekim.crimson_sky.common.model.AccountSettings settings,
    Instant createdAt
) {
}
