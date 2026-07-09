package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.gdx.utils.Array;

import java.util.SplittableRandom;

/**
 * Server-authoritative state for one battle. Every roll in the Mizan Combat Engine draws from a
 * single per-battle {@link SplittableRandom} seeded here, so a battle's outcome is fully
 * reproducible for debugging and unit tests (story A4, system design §4).
 *
 * <p>{@code SplittableRandom} is chosen over {@code java.util.Random} for its better statistical
 * distribution over long sequences while remaining seedable/reproducible.
 *
 * <p>Participants are modelled as an {@code Array<BattleParticipant>} rather than two hardcoded
 * {@code characterA}/{@code characterB} fields (story B2, system design §7): only two are ever
 * populated at launch, but N-participant content (raids) then needs no rewrite of the battle model.
 * The turn cascade itself lives in {@link BattleEngine}, which reads this session.
 */
public class BattleSession {

    private final long seed;
    private final SplittableRandom rng;
    private final Array<BattleParticipant> participants = new Array<>();

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

    /** Adds a combatant to the battle (two at launch; the array leaves room for raids, §7). */
    public void addParticipant(BattleParticipant participant) {
        participants.add(participant);
    }

    /** The combatants in this battle, in insertion order (priority is decided by {@link BattleEngine}). */
    public Array<BattleParticipant> participants() {
        return participants;
    }

    /**
     * Ends the battle and releases its combatants (story B2). The ephemeral per-battle ECS state
     * ({@link io.github.ydhekim.crimson_sky.ecs.component.BattleStateComponent}, HP/mana/stamina
     * pools) is never persisted, so cleanup is just dropping the participant references — the whole
     * throwaway battle {@code Engine} and its entities are then garbage-collected (system design §3/§8).
     */
    public void end() {
        participants.clear();
    }
}
