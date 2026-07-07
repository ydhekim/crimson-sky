package io.github.ydhekim.crimson_sky.common.model;

/**
 * A single entry in a turn's Result Set (GDD §3). {@code frequency} is the repeat count the
 * client visualizes (e.g. {@code 3x Hammer}); {@code failed} marks a Burned cast — a skill that
 * was chosen but could not resolve because mana validation failed (GDD Scenario 3), rendered as
 * {@code FAILED_CAST} rather than a no-op.
 *
 * <p>Not registered in {@code KryoConfig} yet: these are produced and consumed server-side by the
 * combat engine and are not sent over the wire until the combat packets land (system design §5/§6).
 */
public record ResolvedAction(
    ActionSource source,
    String label,
    int frequency,
    boolean failed
) {
}
