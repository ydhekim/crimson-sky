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
 * <p>The four-arg constructor is a convenience for the decision layer, which knows the action but
 * not yet its damage; it defaults {@code damage} to {@code 0}.
 *
 * <p>Registered in {@code KryoConfig} (after the existing entries) because it rides inside
 * {@code CombatActionResponse.actions()} once combat packets are exchanged (system design §5/§6).
 */
public record ResolvedAction(
    ActionSource source,
    String label,
    int frequency,
    boolean failed,
    int damage
) {
    /** Decision-layer convenience: an action whose damage has not been applied yet ({@code 0}). */
    public ResolvedAction(ActionSource source, String label, int frequency, boolean failed) {
        this(source, label, frequency, failed, 0);
    }
}
