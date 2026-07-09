package io.github.ydhekim.crimson_sky.combat;

import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;

/**
 * Internal decision-layer result of the pet's independent Insight check: the pet {@link ResolvedAction}
 * plus its {@code minAttack}/{@code maxAttack} range ({@link BattleEngine} applies pet hits with
 * <b>no</b> stat bonus, system design §4.2). {@code null} is used in place of this record when the
 * pet does not act; package-private for the same reason as {@link CharacterActionResolution}.
 */
record PetActionResolution(
    ResolvedAction action,
    int minAttack,
    int maxAttack
) {
}
