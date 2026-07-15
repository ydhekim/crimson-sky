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

    /**
     * The real per-stat lifetime cap (system design §15). Shared here in {@code common} so both the
     * client's creation screen and the server's stat-point spend validation reference one number and
     * can never drift apart. This replaces {@code CharacterCreationScreen}'s old UI-only placeholder of
     * {@code 20}, which was never an intended lifetime ceiling.
     */
    public static final int MAX_STAT_VALUE = 60;

    /** Component-wise sum of all eight stats — the total point cost of allocating {@code this} as a delta. */
    public int total() {
        return strength + dexterity + vitality + intelligence + wisdom + spirit + speed + insight;
    }

    /** Component-wise addition, used to merge a spend delta onto a character's current stats. */
    public Stats plus(Stats other) {
        return new Stats(
            strength + other.strength,
            dexterity + other.dexterity,
            vitality + other.vitality,
            intelligence + other.intelligence,
            wisdom + other.wisdom,
            spirit + other.spirit,
            speed + other.speed,
            insight + other.insight);
    }

    /** True when any single component is negative — a spend delta must only ever add points. */
    public boolean hasNegativeComponent() {
        return strength < 0 || dexterity < 0 || vitality < 0 || intelligence < 0
            || wisdom < 0 || spirit < 0 || speed < 0 || insight < 0;
    }

    /** The largest of the eight components — used to check no merged stat exceeds {@link #MAX_STAT_VALUE}. */
    public int max() {
        return Math.max(Math.max(Math.max(strength, dexterity), Math.max(vitality, intelligence)),
            Math.max(Math.max(wisdom, spirit), Math.max(speed, insight)));
    }
}
