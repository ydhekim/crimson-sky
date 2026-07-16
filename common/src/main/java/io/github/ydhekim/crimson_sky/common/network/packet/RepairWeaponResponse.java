package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Weapon;

/**
 * Server → client outcome of a {@link RepairWeaponRequest} (system design §18). On success,
 * {@code repairedWeapon} is the stored weapon at full durability and the two balances are what the
 * character has left afterwards — both are returned whichever path paid, so the client never has to
 * infer one from the other. On failure ({@code success == false}), {@code message} carries the
 * {@code MessageCode} name explaining why ({@code SHOP_ITEM_NOT_FOUND}, {@code SHOP_NOTHING_TO_REPAIR},
 * {@code SHOP_GOLD_INSUFFICIENT}, {@code SHOP_TOKEN_INSUFFICIENT}), {@code repairedWeapon} is
 * {@code null}, and the balances are {@code 0}.
 */
public record RepairWeaponResponse(boolean success, String message, Weapon repairedWeapon,
                                   long remainingGold, int remainingRepairTokens) {
}
