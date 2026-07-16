package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Weapon;

/**
 * Pure, stateless numeric formulas shared across the combat resolvers (system design §4.1–§4.4).
 * Kept free of Ashley/GDX and RNG so every constant lives in exactly one place and is trivially
 * unit-testable. RNG-driven pass/fail and damage draws live in {@link ActionResolver}/
 * {@link PetResolver}/{@link DamageCalculator}; only the deterministic scaling lives here.
 */
public final class CombatMath {

    /** d100 threshold / frequency granularity (every {@value #FREQUENCY_STEP} stat points → +1 repeat). */
    static final int ROLL_BOUND = 100;
    static final int FREQUENCY_STEP = 30;

    /** effectiveStrength floor (§4.3): a wildly-mismatched weapon keeps ~5% draw chance, never 0. */
    static final int EFFECTIVE_STRENGTH_FLOOR = 5;

    private CombatMath() {
    }

    /**
     * Number of action repeats derived from the governing frequency stat (DEX for weapons,
     * effectiveWis for skills, effectiveInsight for pets). {@code 1 + stat/30}, floored at 1.
     */
    public static int frequency(int frequencyStat) {
        return 1 + Math.max(0, frequencyStat) / FREQUENCY_STEP;
    }

    /**
     * Weapon-draw stat after the weight soft-penalty (§4.3): a too-heavy weapon is harder to draw
     * reliably, but never hard-blocked (floored at {@value #EFFECTIVE_STRENGTH_FLOOR}). Frequency is
     * unaffected — only the draw roll reads this.
     */
    public static int effectiveStrength(int strength, float weaponWeight) {
        float comfortableWeight = strength / 2.0f;
        float overage = Math.max(0f, weaponWeight - comfortableWeight);
        int penalized = strength - Math.round(overage * 10f);
        return Math.max(EFFECTIVE_STRENGTH_FLOOR, penalized);
    }

    /** WIS after the skill's Difficulty modifier (§4.3), used for both the cast roll and frequency. */
    public static int effectiveWis(int wisdom, Difficulty difficulty) {
        return wisdom + difficultyModifier(difficulty);
    }

    /** Insight after the pet's Tameness modifier (§4.3), used for both the pet-aid roll and frequency. */
    public static int effectiveInsight(int insight, Pet pet) {
        return insight + tamenessModifier(pet.tameness());
    }

    /** §4.3 Difficulty → WIS modifier table. */
    public static int difficultyModifier(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 0;
            case MEDIUM -> -10;
            case HARD -> -20;
            case MYTHIC -> -35;
        };
    }

    /** §4.3 Tameness → Insight modifier table. */
    public static int tamenessModifier(io.github.ydhekim.crimson_sky.common.model.Tameness tameness) {
        return switch (tameness) {
            case WILD -> -10;
            case STUBBORN -> 0;
            case TRACEABLE -> 10;
            case LOYAL -> 20;
        };
    }

    /** Range midpoint used as an item's inherent power tier for mitigation (§4.2), excluding stat bonus. */
    public static int itemPower(int minAttack, int maxAttack) {
        return (minAttack + maxAttack) / 2;
    }

    /** Flat damage bonus a wielder's path stat (STR/INT) adds to both ends of the range (§4.2). */
    public static int statBonus(int pathStatValue) {
        return pathStatValue / 2; // floor(pathStat * 0.5)
    }

    /** Skill-branch convenience: does the character currently have the mana to cast this skill? */
    public static boolean isAffordable(Skill skill, int currentMana) {
        return currentMana >= skill.manaCost();
    }

    /**
     * Weapon-branch convenience: can this weapon still be drawn? Two independent ways to answer no, both
     * expressed as plain unaffordability so the pouch walk needs no "is it broken" branch of its own
     * (§17): a broken weapon ({@code currentDurability == 0}) is skipped exactly like one there's no
     * Stamina left for, rotating to the next weapon or falling through to punch.
     */
    public static boolean isAffordable(Weapon weapon, int remainingStamina) {
        return weapon.currentDurability() > 0 && remainingStamina >= weapon.staminaCost();
    }

    /**
     * Pet-branch convenience: has this pet got any health left to act with? Mirrors the durability half of
     * {@link #isAffordable(Weapon, int)} — a worn-out pet ({@code currentHealth == 0}) is skipped for the
     * battle exactly as if the character brought no pet at all, never a block on attacking (§18).
     */
    public static boolean isPetUsable(Pet pet) {
        return pet.currentHealth() > 0;
    }
}
