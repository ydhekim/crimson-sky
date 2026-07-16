package io.github.ydhekim.crimson_sky.common.model;

/**
 * A physical-path weapon. Damage is a {@code minAttack}..{@code maxAttack} range (the weapon's
 * "feel", independent of who wields it — the wielder's STR bonus is added on top at hit time,
 * system design §4.2). {@code weight} soft-penalizes the weapon-draw roll when it exceeds the
 * wielder's comfortable weight (§4.3); {@code staminaCost} is drawn from the character's Stamina
 * pool each time the weapon is used, mirroring {@link Skill#manaCost()} (§4.4).
 *
 * <p><b>Durability (§17)</b> is the weapon's wear across battles: {@code currentDurability} drops by 1
 * per battle the weapon fires in, and at {@code 0} the weapon reads as unaffordable in the pouch
 * cascade ({@code CombatMath.isAffordable}) — skipped exactly like a weapon there's no Stamina for,
 * never a hard block on attacking. Repair (a shop action, Epic O) resets it to {@code maxDurability},
 * which is a flat 20 for all content this pass, pending the real content-authoring pass (E1/M5).
 *
 * <p><b>Where durability actually lives:</b> a character's {@code Inventory} and {@code Loadout} each
 * hold their own copy of a weapon record, so the two could drift. {@code Inventory} is the single
 * source of truth (§17) — {@code Loadout} only means "this item id is equipped". Combat reads current
 * durability by cross-referencing the equipped id against inventory
 * ({@code BattleParticipant.fromCharacter}), and the post-battle decrement writes inventory only.
 */
public record Weapon(
    long id,
    String name,
    String description,
    Rarity rarity,
    float weight,
    int minAttack,
    int maxAttack,
    int staminaCost,
    int maxDurability,
    int currentDurability
) {

    /** A copy with {@code currentDurability} reduced by one, floored at 0 — one battle's wear (§17). */
    public Weapon worn() {
        return new Weapon(id, name, description, rarity, weight, minAttack, maxAttack, staminaCost,
            maxDurability, Math.max(0, currentDurability - 1));
    }

    /** A copy carrying {@code durability} as its current value — the combat-time Inventory cross-reference. */
    public Weapon withCurrentDurability(int durability) {
        return new Weapon(id, name, description, rarity, weight, minAttack, maxAttack, staminaCost,
            maxDurability, durability);
    }
}
