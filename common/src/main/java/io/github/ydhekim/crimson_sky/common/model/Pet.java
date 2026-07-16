package io.github.ydhekim.crimson_sky.common.model;

/**
 * A battle pet. Damage is a {@code minAttack}..{@code maxAttack} range like {@link Weapon}/{@link
 * Skill}, but pet hits get <b>no</b> wielder stat bonus (self-contained, system design §4.2) — a
 * pet's Insight already governs whether/how-often it acts (§4.3). {@code tameness} modifies the
 * pet-aid roll and frequency.
 *
 * <p><b>Health (§18)</b> mirrors {@link Weapon}'s durability exactly, on purpose: {@code healthPoint} is
 * the pet's max, {@code currentHealth} drops by 1 per battle the pet actually <i>acts</i> in (not per hit),
 * and at {@code 0} the pet's bonus action is simply skipped ({@code CombatMath.isPetUsable}) — soft, never
 * a block on the character attacking. Restoring it is a shop action (Epic O), gold-priced or token-redeemed.
 *
 * <p>This is wear from being used, <b>not</b> damage taken from an opponent — so {@code defence} stays as
 * vestigial as it was; §18 leaves "pets can be targeted and hurt" as a separate, bigger question.
 *
 * <p><b>Where health actually lives:</b> same answer as durability — a character's {@code Inventory} and
 * {@code Loadout} each hold their own copy of a pet record, so the two could drift. {@code Inventory} is the
 * single source of truth; combat cross-references the equipped id against inventory
 * ({@code BattleParticipant.fromCharacter}), and the post-battle decrement writes inventory only.
 */
public record Pet(
    long id,
    String name,
    String description,
    Tameness tameness,
    int healthPoint,
    int defence,
    int minAttack,
    int maxAttack,
    int currentHealth
) {

    /** A copy with {@code currentHealth} reduced by one, floored at 0 — one battle's wear (§18). */
    public Pet worn() {
        return new Pet(id, name, description, tameness, healthPoint, defence, minAttack, maxAttack,
            Math.max(0, currentHealth - 1));
    }

    /** A copy carrying {@code health} as its current value — the combat-time Inventory cross-reference. */
    public Pet withCurrentHealth(int health) {
        return new Pet(id, name, description, tameness, healthPoint, defence, minAttack, maxAttack, health);
    }

    /** A copy fully restored to {@code healthPoint} — the shop repair action (§18). */
    public Pet repaired() {
        return withCurrentHealth(healthPoint);
    }
}
