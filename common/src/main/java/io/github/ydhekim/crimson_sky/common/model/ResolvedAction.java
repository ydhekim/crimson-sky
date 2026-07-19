package io.github.ydhekim.crimson_sky.common.model;

/**
 * A single entry in a turn's Result Set (GDD §3). {@code frequency} is the repeat count the
 * client visualizes (e.g. {@code 3x Hammer}); {@code failed} marks a Burned cast — a skill that
 * was chosen but could not resolve because mana validation failed (GDD Scenario 3), rendered as
 * {@code FAILED_CAST} rather than a no-op. {@code damage} is the final post-mitigation damage this
 * entry dealt — the sum of every landed (non-dodged) hit in the entry after mitigation (system
 * design §4.2). It is {@code 0} until a battle turn actually applies the entry: the pure
 * {@link io.github.ydhekim.crimson_sky.combat.ActionResolver decision layer} produces entries with
 * {@code damage = 0}, and the battle turn resolution fills in the real total.
 *
 * <p><b>{@code CONSUMABLE} reads {@code damage} differently</b> (system design §18): a potion deals none,
 * so the field carries the amount <i>restored to the actor</i> instead. Its decision layer
 * ({@link io.github.ydhekim.crimson_sky.combat.ConsumableResolver}) fills the value in immediately rather
 * than leaving it at {@code 0} — a potion's restore amount is flat, with no mitigation math to wait for.
 *
 * <p>{@code itemId} identifies <b>which</b> record the entry acted with — {@code source}'s category
 * alone can't, once a pouch holds several weapons or skills (system design §17). It carries the
 * {@code Weapon}/{@code Skill}/{@code Pet} id for those sources, and {@code 0} for {@code PUNCH} (no
 * backing record, see {@link ActionSource}) and for a Burned cast. Durability bookkeeping
 * ({@code RewardService}, §17) is its first consumer — it reads back which weapons fired — and Epic O's
 * consumable-charge tracking is the next.
 *
 * <p>The five-arg constructor is a convenience for the decision layer, which knows the action but
 * not yet its damage; it defaults {@code damage} to {@code 0}.
 *
 * <p>Registered in {@code KryoConfig} (after the existing entries) because it rides inside
 * {@code AttackResponse.turns()} once combat packets are exchanged (system design §5/§6).
 */
public record ResolvedAction(
    ActionSource source,
    String label,
    int frequency,
    boolean failed,
    int damage,
    long itemId
) {
    /** Decision-layer convenience: an action whose damage has not been applied yet ({@code 0}). */
    public ResolvedAction(ActionSource source, String label, int frequency, boolean failed, long itemId) {
        this(source, label, frequency, failed, 0, itemId);
    }
}
