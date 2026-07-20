package io.github.ydhekim.crimson_sky.common.model;

import java.time.Instant;

/**
 * One row of a character's match-history list (system design §22). {@code rankedEloDelta} is {@code null}
 * for a {@code NORMAL} battle (mirrors {@code battle_history.ranked_elo_delta}'s own nullability).
 * {@code opponentName} is never {@code null} on the wire — a bot opponent (or a since-deleted character)
 * carries a display-safe placeholder instead, so a bot stays indistinguishable from a real opponent.
 */
public record RecentMatch(boolean won, String battleMode, int eloDelta, Integer rankedEloDelta,
                          int turnCount, String opponentName, Instant occurredAt) {
}
