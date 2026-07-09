package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.combat.BattleParticipant;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.network.packet.CombatActionResponse;
import io.github.ydhekim.crimson_sky.ecs.component.TurnResultComponent;
import io.github.ydhekim.crimson_sky.server.combat.ActiveBattle;
import io.github.ydhekim.crimson_sky.server.combat.BattleSessionRegistry;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;

import java.util.Optional;

/**
 * Server-side combat entry point (system design §6): the ownership guardrail (story B3) plus the
 * per-turn engine tick over a live {@code BattleSession} produced by matchmaking (story B1).
 *
 * <p><b>A turn is one call for both sides.</b> {@code BattleEngine.resolveTurn()} resolves *both*
 * combatants' Result Sets — priority order, per-hit dodge/damage, win condition — in a single call.
 * A {@code CombatActionRequest} is therefore an "advance this battle by one turn" signal, not "play
 * my move": the resolution algorithm (§4.1–§4.4) is fully probabilistic off stats and pouch order,
 * with no player-chosen action anywhere in it. The packet's nullable {@code skillId} field predates
 * that design and is deliberately ignored — whether it should be removed outright is a design call,
 * flagged for planning close-out rather than silently answered here.
 *
 * <p><b>Open design question (deliberately not answered here).</b> Because one call resolves both
 * sides, the response below only naturally carries the <i>requester's own</i> Result Set. How the
 * opposing client learns what happened on that same turn is undecided — a server-pushed packet to
 * the other connection, a cached result the other client fetches with its own request, or something
 * else. Rather than inventing a networking pattern, this returns the requester's side only. Whichever
 * shape is chosen also settles what should happen when both clients each send a request for "the same"
 * turn, which today advances the battle twice.
 */
public class CombatService {

    private static final Logger log = new Logger("CombatService", Logger.DEBUG);

    private final CharacterDao characterDao;
    private final BattleSessionRegistry battleRegistry;

    public CombatService(CharacterDao characterDao, BattleSessionRegistry battleRegistry) {
        this.characterDao = characterDao;
        this.battleRegistry = battleRegistry;
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

    /**
     * Advances {@code battleId} by one turn on behalf of {@code characterId} and returns that
     * character's own Result Set. The caller must have already confirmed the character is owned by the
     * connection's account; this adds the second half of the guardrail — the character must also be a
     * participant in this specific battle (story B1).
     *
     * <p>A battle that ended this turn is closed out immediately: {@code BattleSession.end()} runs and
     * the registry drops the entry, so a finished battle can never be ticked again.
     *
     * @return the requester's Result Set, or empty when the battle is unknown/already ended or the
     *     character is not one of its combatants
     */
    public Optional<CombatActionResponse> resolveTurn(long battleId, long characterId) {
        ActiveBattle battle = battleRegistry.get(battleId);
        if (battle == null) {
            log.info("Rejected combat action: battle " + battleId + " is unknown or already ended.");
            return Optional.empty();
        }

        BattleParticipant participant = battle.participantFor(characterId);
        if (participant == null) {
            log.info("Rejected combat action: character " + characterId
                + " is not a participant in battle " + battleId + ".");
            return Optional.empty();
        }

        battle.engine().resolveTurn();
        long turnNumber = battle.engine().turnNumber();

        CombatActionResponse response = new CombatActionResponse(
            battleId, turnNumber, resultSetOf(participant, turnNumber));

        if (battle.engine().isOver()) {
            // Reward persistence (Gold/Exp/Elo + battle_history) hangs off this point — story C1.
            battleRegistry.end(battleId);
        }
        return Optional.of(response);
    }

    /**
     * A defensive copy of the participant's Result Set <i>for this turn</i>. Empty when the participant
     * never acted: the higher-priority combatant's Result Set can kill before the lower-priority one
     * rolls anything (§4.2), leaving the loser's {@link TurnResultComponent} holding the previous
     * turn's actions — which must not be reported as if they happened now. The copy matters because
     * that component is cleared and refilled in place every turn, while the response outlives the call.
     */
    private Array<ResolvedAction> resultSetOf(BattleParticipant participant, long turnNumber) {
        TurnResultComponent turnResult = participant.turnResult();
        boolean actedThisTurn = turnResult != null && turnResult.turnNumber == turnNumber;
        return actedThisTurn ? new Array<>(turnResult.actions) : new Array<>();
    }
}
