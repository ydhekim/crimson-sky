package io.github.ydhekim.crimson_sky.common.model;

/**
 * The battle-scoped pool a {@code CONSUMABLE} {@link Skill} restores (system design §18). HP/Mana/Stamina
 * all reset between battles, so a potion's purpose is entirely within one fight.
 */
public enum ResourceType {
    HEALTH, MANA, STAMINA
}
