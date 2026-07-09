package io.github.ydhekim.crimson_sky.common.model;

/**
 * A battle pet. Damage is a {@code minAttack}..{@code maxAttack} range like {@link Weapon}/{@link
 * Skill}, but pet hits get <b>no</b> wielder stat bonus (self-contained, system design §4.2) — a
 * pet's Insight already governs whether/how-often it acts (§4.3). {@code tameness} modifies the
 * pet-aid roll and frequency. {@code healthPoint}/{@code defence} are not yet referenced by any
 * combat formula (see 04-starter-content.md open item) — carried for forward compatibility.
 */
public record Pet(
    long id,
    String name,
    String description,
    Tameness tameness,
    int healthPoint,
    int defence,
    int minAttack,
    int maxAttack
) {
}
