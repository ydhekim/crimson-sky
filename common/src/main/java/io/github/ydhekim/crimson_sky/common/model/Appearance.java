package io.github.ydhekim.crimson_sky.common.model;

import java.util.List;

/**
 * A character's purely cosmetic appearance (system design §23) — gender, hair type/color, skin tone. No
 * stat, no combat interaction, not consumed by rendering yet (M4 is still placeholder-rendering; M5's real
 * art pipeline is what eventually reads this). Set once at character creation; no edit endpoint in v1.0.
 *
 * <p>v1.0 ships a small curated set, not open free text (same "curated now, data-driven once real content
 * exists" precedent as starter weapons/skills/pets and the achievement seed data, §22) — the four constant
 * lists below are the single source of truth for what's allowed, read by both
 * {@code CharacterCreationScreen}'s selection buttons and {@link #isValid()}'s server-side check, so the two
 * can never drift apart.
 */
public record Appearance(String gender, String hairType, String hairColor, String skinColor) {

    public static final List<String> GENDERS = List.of("MALE", "FEMALE");
    public static final List<String> HAIR_TYPES = List.of("SHORT", "LONG", "BALD");
    public static final List<String> HAIR_COLORS = List.of("BLACK", "BROWN", "BLONDE", "RED");
    public static final List<String> SKIN_COLORS = List.of("PALE", "LIGHT", "TAN", "DARK");

    /** True only when every component is a member of its own curated list (system design §23). */
    public boolean isValid() {
        return gender != null && GENDERS.contains(gender)
            && hairType != null && HAIR_TYPES.contains(hairType)
            && hairColor != null && HAIR_COLORS.contains(hairColor)
            && skinColor != null && SKIN_COLORS.contains(skinColor);
    }
}
