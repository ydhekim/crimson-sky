package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to fully repair one owned weapon back to its {@code maxDurability} (system
 * design §18). A targeted service against an item the character already owns, not a catalog purchase.
 *
 * <p>{@code useToken} picks the payment path, the two §18 locks in from the start: {@code false} pays
 * {@code 5 gold × missingDurability} from the account wallet; {@code true} redeems one Repair Token from
 * the character's consumables instead, at no gold cost. The server validates ownership of
 * {@code characterId}, that {@code weaponId} is in its inventory, that the weapon is actually damaged, and
 * that the chosen currency covers it — the client's word on none of that is trusted.
 */
public record RepairWeaponRequest(long characterId, long weaponId, boolean useToken) {
}
