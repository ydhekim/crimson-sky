package io.github.ydhekim.crimson_sky.server.database.entity;

import java.time.Instant;

/**
 * One {@code battle_history} row projected for a character's match-history list (system design §22).
 * {@code opponentName} is {@code null} for a bot opponent (no {@code characters} row to join) or a
 * since-deleted real character — {@code CharacterPageService} is what turns that {@code null} into a
 * display-safe placeholder, not the DAO, so this row type carries the raw (possibly null) fact.
 */
public record RecentMatchRow(boolean won, String battleMode, int eloDelta, Integer rankedEloDelta,
                             int turnCount, Instant occurredAt, String opponentName) {
}
