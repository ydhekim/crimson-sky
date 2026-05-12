package io.github.ydhekim.crimson_sky.server.database.entity;

import java.time.Instant;

public record User(
    long id,
    String platformType,
    String identityToken,
    Instant createdAt
) {}
