package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Server → client notification that a queued character has been paired (system design §7, story B1).
 * Sent to <b>both</b> matched connections, each receiving the <i>other</i> side's character id.
 * {@code battleId} identifies the live {@code BattleSession} that subsequent
 * {@link CombatActionRequest}s must reference.
 */
public record MatchmakingFoundResponse(
    long battleId,
    long opponentCharacterId
) {
}
