package io.github.ydhekim.crimson_sky.server.combat;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.network.packet.AttackResponse;

/**
 * The server-internal outcome of one attack (story B4) — strictly richer than the
 * {@link AttackResponse} sent to the client.
 *
 * <p>This is the seam story C1 (reward persistence) is meant to wrap: it carries everything a
 * {@code battle_history} row and an Elo/Gold/Exp application needs — who fought, who won, whether the
 * opponent was synthesized, and the resolved turns — so C1 can add persistence around a call to
 * {@code AttackService} without reaching back into the battle.
 *
 * <p><b>{@code opponentIsBot} and {@code opponentCharacterId} must never cross the wire</b> (system
 * design §7): {@link #toResponse()} is the only place this object narrows to a packet, and it drops
 * both fields deliberately. {@code opponentCharacterId} is {@code null} exactly when the opponent was
 * a bot, mirroring {@code battle_history.opponent_character_id}'s nullability (§8).
 */
public record AttackResult(
    long battleId,
    long characterId,
    Long opponentCharacterId,
    String opponentDisplayName,
    boolean opponentIsBot,
    boolean won,
    Array<Array<ResolvedAction>> turns
) {

    /** Narrows to the client-visible packet, dropping the bot flag and the opponent's real id. */
    public AttackResponse toResponse() {
        return new AttackResponse(battleId, opponentDisplayName, won, turns);
    }
}
