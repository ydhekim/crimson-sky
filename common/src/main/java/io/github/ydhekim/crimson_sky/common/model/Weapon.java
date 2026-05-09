package io.github.ydhekim.crimson_sky.common.model;

public record Weapon(
    long id,
    String name,
    String description,
    Rarity rarity,
    float weight,
    int attackPower
) {
}
