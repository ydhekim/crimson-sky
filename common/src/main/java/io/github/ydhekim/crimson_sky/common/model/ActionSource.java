package io.github.ydhekim.crimson_sky.common.model;

/**
 * Origin of a {@link ResolvedAction} in the Mizan Combat Engine Result Set (GDD §3).
 * WEAPON/SKILL/PUNCH come from the character-action cascade; PET is appended by the
 * independent Insight check (GDD §3 Step 2).
 */
public enum ActionSource {
    WEAPON, SKILL, PUNCH, PET
}
