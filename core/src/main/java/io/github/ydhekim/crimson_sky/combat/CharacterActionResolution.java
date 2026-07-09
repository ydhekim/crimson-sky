package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;

/**
 * Internal decision-layer result of the character-action cascade: the {@link ResolvedAction} the
 * client sees plus the damage inputs {@link BattleEngine} needs to apply hits ({@code minAttack}/
 * {@code maxAttack} of the chosen source and the path stat value — STR for weapon/punch, INT for
 * skill). Package-private on purpose — only {@link ActionResolver} produces it and only
 * {@link BattleEngine} (same package) consumes it; the ECS systems use the plain {@code ResolvedAction}.
 *
 * <p>For a Burned cast (failed skill) and any punch fallback the damage inputs are still populated
 * (punch = 1..5, STR), but a Burned entry is marked {@code failed} and never applies damage.
 *
 * <p>{@code resourceCost} is what {@link BattleEngine} draws from the pool once the action is
 * committed: the chosen weapon's {@code staminaCost} (WEAPON), the chosen skill's {@code manaCost}
 * (a successful SKILL), or {@code 0} (Punch and Burned — a Burned cast could not afford its skill,
 * so nothing is spent).
 */
record CharacterActionResolution(
    ResolvedAction action,
    int minAttack,
    int maxAttack,
    int pathStatValue,
    int resourceCost
) {
}
