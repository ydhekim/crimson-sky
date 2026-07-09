package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.MatchmakingFoundResponse;
import io.github.ydhekim.crimson_sky.common.network.packet.MatchmakingRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The B3 ownership guardrail applies to matchmaking too (story B1): a client must never be able to
 * queue — and therefore commit to a battle with — a character it does not own.
 */
class MatchmakingRequestHandlerTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;

    private MatchmakingService matchmaking;
    private MatchmakingRequestHandler handler;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        FakeCharacterDao characterDao = new FakeCharacterDao()
            .with(CombatFixtures.character(1L, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(2L, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);

        BattleSessionRegistry battleRegistry = new BattleSessionRegistry();
        matchmaking = new MatchmakingService(new CharacterService(characterDao), battleRegistry);
        handler = new MatchmakingRequestHandler(matchmaking, new CombatService(characterDao, battleRegistry));
    }

    @Test
    void queuesAnOwnedCharacter() {
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler.handle(connection, new MatchmakingRequest(1L));

        assertTrue(matchmaking.isQueued(1L));
        assertTrue(connection.sentNothing(), "nothing is sent until a pairing actually happens");
    }

    @Test
    void pairsTwoOwnedCharactersAndNotifiesBoth() {
        FakeGameConnection first = FakeGameConnection.authenticated(1, ACCOUNT_A);
        FakeGameConnection second = FakeGameConnection.authenticated(2, ACCOUNT_B);

        handler.handle(first, new MatchmakingRequest(1L));
        handler.handle(second, new MatchmakingRequest(2L));

        assertNotNull(first.onlySentPacket(MatchmakingFoundResponse.class));
        assertNotNull(second.onlySentPacket(MatchmakingFoundResponse.class));
        assertEquals(0, matchmaking.queueSize());
    }

    @Test
    void rejectsUnauthenticatedConnection() {
        FakeGameConnection anonymous = FakeGameConnection.unauthenticated(9);

        handler.handle(anonymous, new MatchmakingRequest(1L));

        assertEquals(0, matchmaking.queueSize(), "an unauthenticated request never reaches the queue");
        assertTrue(anonymous.sentNothing());
    }

    @Test
    void rejectsCharacterNotOwnedByTheConnectionsAccount() {
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler.handle(connection, new MatchmakingRequest(2L)); // character 2 belongs to account B

        assertEquals(0, matchmaking.queueSize(), "a non-owned character is never queued");
        assertTrue(connection.sentNothing());
    }
}
