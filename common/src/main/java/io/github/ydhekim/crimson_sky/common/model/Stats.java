package io.github.ydhekim.crimson_sky.common.model;

public record Stats(
    // Physical Path
    int strength,     // Damage Floor / Weapon Weight
    int dexterity,    // Physical Action Frequency
    int vitality,     // HP Pool / Resistances

    // Magical Path
    int intelligence, // Damage Floor / Skill Weight
    int wisdom,       // Skill Action Frequency / Success
    int spirit,       // Mana Pool / Resistances

    // Neutral Multipliers
    int speed,        // Dodge & Priority
    int insight       // Pet Frequency & Success
) {
}
