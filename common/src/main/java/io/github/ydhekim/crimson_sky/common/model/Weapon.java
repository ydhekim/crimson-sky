package io.github.ydhekim.crimson_sky.common.model;

/**
 * A physical-path weapon. Damage is a {@code minAttack}..{@code maxAttack} range (the weapon's
 * "feel", independent of who wields it — the wielder's STR bonus is added on top at hit time,
 * system design §4.2). {@code weight} soft-penalizes the weapon-draw roll when it exceeds the
 * wielder's comfortable weight (§4.3); {@code staminaCost} is drawn from the character's Stamina
 * pool each time the weapon is used, mirroring {@link Skill#manaCost()} (§4.4).
 */
public record Weapon(
    long id,
    String name,
    String description,
    Rarity rarity,
    float weight,
    int minAttack,
    int maxAttack,
    int staminaCost
) {
}
