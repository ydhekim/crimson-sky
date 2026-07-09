package io.github.ydhekim.crimson_sky.server.combat;

import io.github.ydhekim.crimson_sky.combat.BattleEngine;
import io.github.ydhekim.crimson_sky.combat.BattleParticipant;
import io.github.ydhekim.crimson_sky.combat.BattleSession;

import java.util.Collections;
import java.util.Map;

/**
 * Everything the server needs to keep resolving turns for one live battle: the {@link BattleEngine}
 * that ticks it, the {@link BattleSession} it ticks over, and a character-id → {@link BattleParticipant}
 * lookup so an incoming {@code CombatActionRequest} can find "which side is this request for".
 *
 * <p>Created by matchmaking, owned by {@link BattleSessionRegistry}, discarded when the battle ends.
 */
public class ActiveBattle {

    private final long battleId;
    private final BattleSession session;
    private final BattleEngine engine;
    private final Map<Long, BattleParticipant> participantsByCharacterId;

    ActiveBattle(long battleId, BattleSession session, BattleEngine engine,
                 Map<Long, BattleParticipant> participantsByCharacterId) {
        this.battleId = battleId;
        this.session = session;
        this.engine = engine;
        this.participantsByCharacterId = Collections.unmodifiableMap(participantsByCharacterId);
    }

    public long battleId() {
        return battleId;
    }

    public BattleSession session() {
        return session;
    }

    public BattleEngine engine() {
        return engine;
    }

    /** The participant fighting on behalf of {@code characterId}, or {@code null} if it isn't in this battle. */
    public BattleParticipant participantFor(long characterId) {
        return participantsByCharacterId.get(characterId);
    }

    /**
     * Participation guardrail (story B1): true only when {@code characterId} is actually a combatant
     * here. Ownership alone is not enough — an owned character must not be able to submit actions
     * into a battle it is not part of.
     */
    public boolean hasParticipant(long characterId) {
        return participantsByCharacterId.containsKey(characterId);
    }

    /** The other side's character id, from the perspective of {@code characterId}. */
    public long opponentCharacterIdOf(long characterId) {
        for (long id : participantsByCharacterId.keySet()) {
            if (id != characterId) {
                return id;
            }
        }
        throw new IllegalStateException("Battle " + battleId + " has no opponent for character " + characterId);
    }
}
