package io.github.ydhekim.crimson_sky.common.model;

public record Skill(
    long id,
    String name,
    String description,
    SkillType type,
    int manaCost,
    Difficulty difficultyToAct
) {
}
