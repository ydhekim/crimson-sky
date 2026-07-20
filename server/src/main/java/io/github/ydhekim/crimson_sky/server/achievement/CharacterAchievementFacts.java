package io.github.ydhekim.crimson_sky.server.achievement;

import io.github.ydhekim.crimson_sky.common.model.Rarity;

import java.util.List;

/**
 * Everything a character-scoped criterion might read, gathered once per evaluation call (system design §22)
 * so {@link AchievementEvaluator} stays a pure function of already-loaded facts rather than reaching back
 * into the database per definition.
 *
 * @param totalWins               all-time win count
 * @param currentWinStreak        leading run of wins in the character's newest-first history
 * @param fastestWinTurns         fewest turns in any win, or {@code null} if the character has never won
 *                                (which can never satisfy {@code FASTEST_WIN_TURNS})
 * @param characterLevel          the character's level after this battle
 * @param justGrantedItemRarities rarities of items granted <i>by this battle's milestone bonus</i> only,
 *                                possibly empty — {@code ITEM_ACQUIRED} fires on the moment of acquisition,
 *                                not on standing inventory
 */
public record CharacterAchievementFacts(
    int totalWins,
    int currentWinStreak,
    Integer fastestWinTurns,
    int characterLevel,
    List<Rarity> justGrantedItemRarities
) {
}
