package io.github.ydhekim.crimson_sky.common.model;

/**
 * A magical-path skill. Like {@link Weapon}, damage is a {@code minAttack}..{@code maxAttack} range
 * (system design §4.2) — the wielder's INT bonus is added on top at hit time. {@code manaCost} is
 * drawn from the Mana pool per cast and {@code difficultyToAct} soft-penalizes the skill-cast roll
 * and its frequency (§4.3).
 */
public record Skill(
    long id,
    String name,
    String description,
    SkillType type,
    int manaCost,
    Difficulty difficultyToAct,
    int minAttack,
    int maxAttack
) {
}
