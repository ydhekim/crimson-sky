package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.ResourceType;

/**
 * Internal decision-layer result of the potion check (system design §18): the potion
 * {@link ResolvedAction}, which pool it refills and by how much, plus {@code equippedIndex} — where in the
 * pouch it sits, so {@link BattleEngine} can burn the right charge. {@code null} is used in place of this
 * record when no potion triggers; package-private for the same reason as {@link CharacterActionResolution}.
 *
 * <p>Unlike its two siblings this carries no {@code min}/{@code max} range: a potion restores a flat
 * amount, known the moment it is chosen (§18), so there is nothing for the apply step to roll.
 */
record ConsumableActionResolution(
    int equippedIndex,
    ResolvedAction action,
    ResourceType resource,
    int restoreAmount
) {
}
