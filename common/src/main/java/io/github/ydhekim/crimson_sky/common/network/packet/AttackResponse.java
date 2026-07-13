package io.github.ydhekim.crimson_sky.common.network.packet;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;

/**
 * Server → client outcome of a whole battle, resolved synchronously in one call (system design §5/§7,
 * story B4). {@code turns} is the requesting character's own Result Set per turn, in order — the
 * opponent's turn log is not sent, because there is no opposing client to send it to. The M4 combat
 * screen plays the array back turn by turn.
 *
 * <p><b>Protocol-level guarantee (§7, non-negotiable):</b> the opponent is identified only by
 * {@code opponentDisplayName}, never by a {@code characterId} the client could resolve, and no field
 * here reveals whether that opponent was a real persisted character or a server-synthesized bot.
 * That distinction is recorded server-side only. Do not add an opponent id or a bot flag to this
 * record.
 *
 * <p>{@code battleId} is a per-battle correlation id for logs/telemetry; it addresses nothing the
 * client can look up, since no battle outlives the request that created it.
 *
 * <p>The three deltas (story C1) are what this battle actually paid the attacker — already applied and
 * committed server-side by the time this packet is sent, so they are a report, not a promise the client
 * has to redeem. They reveal nothing about the opponent's nature: a bot fight's Elo delta is computed
 * against the attacker's own rating (system design §8.1), which is exactly what an even real matchup
 * produces. {@code eloDelta} is negative on a loss.
 */
public record AttackResponse(
    long battleId,
    String opponentDisplayName,
    boolean won,
    Array<Array<ResolvedAction>> turns,
    int goldDelta,
    long expDelta,
    int eloDelta
) {
}
