package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to attack an opponent with one of the account's characters (system design
 * §7, story B4). The whole battle resolves inside the one request that carries this packet — there is
 * no queue, no waiting room, and no live opposing client.
 *
 * <p>Deliberately carries no opponent or battle id: the server picks the opponent itself, so there is
 * nothing client-supplied to misuse beyond {@code characterId}, which is ownership-checked against
 * {@code connection.account} (§6/§13).
 */
public record AttackRequest(
    long characterId
) {
}
