package io.github.ydhekim.crimson_sky.combat;

import java.util.SplittableRandom;

/**
 * Server-authoritative state for one battle. Every roll in the Mizan Combat Engine draws from a
 * single per-battle {@link SplittableRandom} seeded here, so a battle's outcome is fully
 * reproducible for debugging and unit tests (story A4, system design §4).
 *
 * <p>{@code SplittableRandom} is chosen over {@code java.util.Random} for its better statistical
 * distribution over long sequences while remaining seedable/reproducible.
 *
 * <p>Minimal on purpose: this holds only the seed and RNG needed by M2's resolution logic. The
 * participant list ({@code Array<BattleParticipant>}) and per-participant battle state arrive with
 * matchmaking/session work (story B2, system design §7) — this class is the extension point for it.
 */
public class BattleSession {

    private final long seed;
    private final SplittableRandom rng;

    public BattleSession(long seed) {
        this.seed = seed;
        this.rng = new SplittableRandom(seed);
    }

    /** The seed this session's RNG was created from — reproduces the exact roll sequence. */
    public long seed() {
        return seed;
    }

    /** The battle's shared RNG source. All combat rolls for this battle must draw from this. */
    public SplittableRandom rng() {
        return rng;
    }
}
