package io.github.ydhekim.crimson_sky.common.model;

/**
 * What flavor of {@link Skill} this is. Appended to only — Kryo registers enums positionally
 * (system design §5), so {@code CONSUMABLE} (§18) sits after the two originals rather than in any
 * tidier order.
 */
public enum SkillType {
    ACTIVE, PASSIVE, CONSUMABLE
}
