package io.github.ydhekim.crimson_sky.common.model;

/**
 * Addresses one of the eight {@link Stats} components as data (system design §16). {@code Stats} only
 * names its stats as record components; a {@code STAT_BONUS} passive needs to say <i>which</i> stat it
 * raises as a value, which is what this closed enum provides. Order mirrors {@link Stats}' component
 * order (STR/DEX/VIT/INT/WIS/SPI/SPD/INS).
 */
public enum StatName {
    STRENGTH, DEXTERITY, VITALITY, INTELLIGENCE, WISDOM, SPIRIT, SPEED, INSIGHT
}
