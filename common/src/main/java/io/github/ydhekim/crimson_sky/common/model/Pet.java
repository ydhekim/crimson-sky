package io.github.ydhekim.crimson_sky.common.model;

public record Pet(
    long id,
    String name,
    String description,
    Tameness tameness,
    int healthPoint,
    int defence,
    int attackPower
) {
}
