package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;

/**
 * Server-side combat entry point (system design §6). For this pass its only responsibility is the
 * ownership guardrail (story B3): confirming a combat request references a character the connection's
 * account actually owns, never trusting a client-supplied {@code characterId} beyond that check —
 * the same pattern {@link CharacterService} already applies to character list/delete.
 *
 * <p>The actual per-turn engine tick (running the Ashley {@code BattleEngine} for a matched
 * {@code BattleSession} and returning a {@code CombatActionResponse}, system design §6) is
 * intentionally not wired here yet: it depends on matchmaking (story B1) to produce a live session
 * and on {@code server} taking a dependency on {@code core}, neither of which is in this pass's scope.
 */
public class CombatService {
    private static final Logger log = new Logger("CombatService", Logger.DEBUG);
    private final CharacterDao characterDao;

    public CombatService(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    /**
     * Ownership guardrail: true only when {@code characterId} belongs to {@code accountId}.
     * Fails closed — any DB error is logged and treated as "not owned" so a lookup failure can never
     * accidentally authorize a combat action.
     */
    public boolean isCharacterOwnedBy(long accountId, long characterId) {
        try {
            return characterDao.isOwnedByAccount(accountId, characterId);
        } catch (Exception e) {
            log.error("Ownership check failed for character " + characterId + " / account " + accountId, e);
            return false;
        }
    }
}
