package io.github.ydhekim.crimson_sky.server.combat;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.combat.BattleEngine;
import io.github.ydhekim.crimson_sky.combat.BattleParticipant;
import io.github.ydhekim.crimson_sky.combat.BattleSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the lifecycle of every live battle (system design §7), the same way {@code ServiceRegistry}
 * owns services and {@code ScreenRouter} owns cached screens — a plain map keyed by an id, handed to
 * collaborators by constructor injection rather than reached through a singleton.
 *
 * <p>Battle ids are process-local and monotonically increasing; they are never persisted (nothing
 * outside a running server's memory refers to a battle). A finished battle is dropped from the map
 * as soon as {@link BattleEngine#isOver()} — {@link #end} calls {@link BattleSession#end()} so the
 * throwaway Ashley engine and its per-battle components become garbage, per §3/§8.
 */
public class BattleSessionRegistry {

    private static final Logger log = new Logger("BattleSessionRegistry", Logger.DEBUG);

    private final AtomicLong nextBattleId = new AtomicLong(1);
    private final Map<Long, ActiveBattle> battles = new ConcurrentHashMap<>();

    /**
     * Registers a freshly matched battle under a new id.
     *
     * @param participantsByCharacterId every combatant, keyed by the character id it fights for
     * @return the registered battle, whose {@link ActiveBattle#battleId()} the clients are told
     */
    public ActiveBattle register(BattleSession session, BattleEngine engine,
                                 Map<Long, BattleParticipant> participantsByCharacterId) {
        long battleId = nextBattleId.getAndIncrement();
        ActiveBattle battle = new ActiveBattle(battleId, session, engine,
            new LinkedHashMap<>(participantsByCharacterId));
        battles.put(battleId, battle);
        log.info("Registered battle " + battleId + " with " + participantsByCharacterId.size() + " participants.");
        return battle;
    }

    /** The live battle for {@code battleId}, or {@code null} if it never existed or has ended. */
    public ActiveBattle get(long battleId) {
        return battles.get(battleId);
    }

    /** Ends and evicts a battle. Idempotent — ending an unknown/already-ended battle is a no-op. */
    public void end(long battleId) {
        ActiveBattle battle = battles.remove(battleId);
        if (battle == null) {
            return;
        }
        battle.session().end();
        log.info("Ended battle " + battleId + " after " + battle.engine().turnNumber() + " turns.");
    }

    /** Number of battles currently in progress. */
    public int activeCount() {
        return battles.size();
    }
}
