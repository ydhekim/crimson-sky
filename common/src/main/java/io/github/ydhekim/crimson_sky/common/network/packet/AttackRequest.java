package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.BattleMode;

/**
 * Client → server request to attack an opponent with one of the account's characters (system design
 * §7, story B4). The whole battle resolves inside the one request that carries this packet — there is
 * no queue, no waiting room, and no live opposing client.
 *
 * <p>Deliberately carries no opponent or battle id: the server picks the opponent itself, so there is
 * nothing client-supplied to misuse beyond {@code characterId}, which is ownership-checked against
 * {@code connection.account} (§6/§13).
 *
 * <p>{@code mode} selects the Elo track and matchmaking pool (system design §21): {@code RANKED} is
 * opt-in and level-25-gated server-side — a sub-25 request is rejected before any battle resolves.
 */
public record AttackRequest(
    long characterId,
    BattleMode mode
) {
}
