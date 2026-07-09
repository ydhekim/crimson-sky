package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.network.packet.CombatActionRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CombatActionResponse;
import io.github.ydhekim.crimson_sky.common.network.packet.MatchmakingFoundResponse;
import io.github.ydhekim.crimson_sky.common.network.packet.MatchmakingRequest;
import io.github.ydhekim.crimson_sky.server.combat.ActiveBattle;
import io.github.ydhekim.crimson_sky.server.combat.BattleSessionRegistry;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.CombatService;
import io.github.ydhekim.crimson_sky.server.service.MatchmakingService;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.FakeGameConnection;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stories B3 + B1, end to end without a DB or a socket: the ownership guardrail still rejects, the new
 * participation guardrail rejects, and an accepted request actually runs the {@link
 * io.github.ydhekim.crimson_sky.combat.BattleEngine} — proving the whole chain (matchmaking → session
 * registry → engine → response) connects, not just each piece in isolation.
 */
class CombatActionRequestHandlerTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long CHARACTER_A = 1L;
    private static final long CHARACTER_B = 2L;

    private BattleSessionRegistry battleRegistry;
    private MatchmakingService matchmaking;
    private CombatService combatService;
    private CombatActionRequestHandler handler;

    private FakeGameConnection connectionA;
    private FakeGameConnection connectionB;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        FakeCharacterDao characterDao = new FakeCharacterDao()
            .with(CombatFixtures.character(CHARACTER_A, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(CHARACTER_B, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);

        battleRegistry = new BattleSessionRegistry();
        matchmaking = new MatchmakingService(new CharacterService(characterDao), battleRegistry);
        combatService = new CombatService(characterDao, battleRegistry);
        handler = new CombatActionRequestHandler(combatService);

        connectionA = FakeGameConnection.authenticated(1, ACCOUNT_A);
        connectionB = FakeGameConnection.authenticated(2, ACCOUNT_B);
    }

    /** Runs both characters through matchmaking, as a real client pair would, and returns the battle. */
    private ActiveBattle matchBothSides() {
        MatchmakingRequestHandler matchmakingHandler = new MatchmakingRequestHandler(matchmaking, combatService);
        matchmakingHandler.handle(connectionA, new MatchmakingRequest(CHARACTER_A));
        matchmakingHandler.handle(connectionB, new MatchmakingRequest(CHARACTER_B));

        long battleId = connectionA.onlySentPacket(MatchmakingFoundResponse.class).battleId();
        ActiveBattle battle = battleRegistry.get(battleId);
        assertNotNull(battle, "matchmaking must have registered a live battle");
        return battle;
    }

    @Test
    void matchedCharacterResolvesARealTurnWithDamage() {
        ActiveBattle battle = matchBothSides();

        handler.handle(connectionA, new CombatActionRequest(battle.battleId(), CHARACTER_A, null));

        CombatActionResponse response = connectionA.onlySentPacket(CombatActionResponse.class);
        assertEquals(battle.battleId(), response.battleId());
        assertEquals(1L, response.turnNumber(), "the first request resolves turn 1");
        assertEquals(1, response.actions().size, "a weapon-only loadout produces a single-entry Result Set");

        ResolvedAction action = response.actions().first();
        assertFalse(action.failed(), "the weapon draw always succeeds at STR 100");
        assertEquals(CombatFixtures.EXPECTED_HIT_DAMAGE, action.damage(),
            "the Result Set carries the damage the engine actually applied");

        // The engine really mutated battle state: the opponent lost exactly that much HP.
        assertEquals(500 - CombatFixtures.EXPECTED_HIT_DAMAGE,
            battle.participantFor(CHARACTER_B).health().currentHealth,
            "one resolveTurn() applies both sides' Result Sets, so B took A's hit");
    }

    @Test
    void bothSidesResolveInOneTurn() {
        ActiveBattle battle = matchBothSides();

        handler.handle(connectionA, new CombatActionRequest(battle.battleId(), CHARACTER_A, null));

        // A single turn resolves both combatants' Result Sets, so A is damaged too even though only
        // A's client sent a request — and only A's own Result Set comes back in the response.
        assertEquals(500 - CombatFixtures.EXPECTED_HIT_DAMAGE,
            battle.participantFor(CHARACTER_A).health().currentHealth);
        assertTrue(connectionB.sent().stream().noneMatch(p -> p instanceof CombatActionResponse),
            "the opponent is not yet notified of the turn — open design question, see CombatService");
    }

    @Test
    void battleIsEndedAndEvictedOnceItIsOver() {
        ActiveBattle battle = matchBothSides();

        // 500 HP each, 150 damage per side per turn → someone falls on turn 4.
        for (int turn = 0; turn < 4; turn++) {
            handler.handle(connectionA, new CombatActionRequest(battle.battleId(), CHARACTER_A, null));
        }

        assertTrue(battle.engine().isOver(), "four turns of 150 damage kills a 500 HP combatant");
        assertNull(battleRegistry.get(battle.battleId()), "a finished battle is evicted from the registry");
        assertEquals(0, battleRegistry.activeCount());

        // A further request against the now-dead battle is rejected, not resolved.
        int sentBefore = connectionA.sent().size();
        handler.handle(connectionA, new CombatActionRequest(battle.battleId(), CHARACTER_A, null));
        assertEquals(sentBefore, connectionA.sent().size(), "an ended battle answers nothing");
    }

    @Test
    void rejectsUnauthenticatedConnection() {
        ActiveBattle battle = matchBothSides();
        FakeGameConnection anonymous = FakeGameConnection.unauthenticated(9);

        handler.handle(anonymous, new CombatActionRequest(battle.battleId(), CHARACTER_A, null));

        assertTrue(anonymous.sentNothing(), "an unauthenticated request is dropped");
        assertEquals(0, battle.engine().turnNumber(), "and no turn is resolved");
    }

    @Test
    void rejectsCharacterNotOwnedByTheConnectionsAccount() {
        ActiveBattle battle = matchBothSides();
        int sentBefore = connectionA.sent().size();

        // Account A asks to act as character B, a real combatant in this battle it does not own.
        handler.handle(connectionA, new CombatActionRequest(battle.battleId(), CHARACTER_B, null));

        assertEquals(sentBefore, connectionA.sent().size(), "a non-owned character is rejected");
        assertEquals(0, battle.engine().turnNumber(), "and no turn is resolved");
    }

    @Test
    void rejectsOwnedCharacterThatIsNotAParticipantInThatBattle() {
        ActiveBattle battle = matchBothSides();

        // A second battle exists; account A owns CHARACTER_A but it does not fight in that one.
        FakeCharacterDao otherDao = new FakeCharacterDao()
            .with(CombatFixtures.character(3L, 30L, "Cem"), 30L, 1000)
            .with(CombatFixtures.character(4L, 40L, "Deniz"), 40L, 1000);
        MatchmakingService otherMatchmaking = new MatchmakingService(new CharacterService(otherDao), battleRegistry);
        otherMatchmaking.enqueue(FakeGameConnection.authenticated(3, 30L), 3L);
        FakeGameConnection fourth = FakeGameConnection.authenticated(4, 40L);
        otherMatchmaking.enqueue(fourth, 4L);
        long otherBattleId = fourth.onlySentPacket(MatchmakingFoundResponse.class).battleId();

        int sentBefore = connectionA.sent().size();
        handler.handle(connectionA, new CombatActionRequest(otherBattleId, CHARACTER_A, null));

        assertEquals(sentBefore, connectionA.sent().size(),
            "an owned character cannot act in a battle it is not a participant of");
        assertEquals(0, battleRegistry.get(otherBattleId).engine().turnNumber(),
            "the other battle is untouched");
        assertEquals(0, battle.engine().turnNumber());
    }

    @Test
    void rejectsUnknownBattleId() {
        matchBothSides();
        int sentBefore = connectionA.sent().size();

        handler.handle(connectionA, new CombatActionRequest(404L, CHARACTER_A, null));

        assertEquals(sentBefore, connectionA.sent().size(), "an unknown battle answers nothing");
    }
}
