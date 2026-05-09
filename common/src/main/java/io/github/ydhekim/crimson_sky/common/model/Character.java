package io.github.ydhekim.crimson_sky.common.model;

import com.badlogic.gdx.utils.Array;

public record Character(
    long id,
    String name,
    int level,
    long experience,
    int maxHealth,
    int maxMana,
    int baseDefence,
    int baseAttackPower,
    Stats stats,
    Array<Weapon> weapons,
    Array<Skill> skills,
    Array<Pet> pets,
    Loadout loadout
) {
    /**
     * Creates a copy of the character with a new experience value.
     * Useful for server-side logic when leveling up.
     */
    public Character withExperience(long newExperience) {
        return new Character(id, name, level, newExperience, maxHealth, maxMana, baseDefence, baseAttackPower, stats, weapons, skills, pets, loadout);
    }
}
