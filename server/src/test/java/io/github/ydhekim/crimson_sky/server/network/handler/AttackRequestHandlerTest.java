package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.AttackRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.AttackResponse;
import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.service.AttackService;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.FakeGameConnection;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The B3 ownership guardrail carries forward unchanged into B4's handler (system design §6). The old
 * "is this character a participant in `battleId`" check is gone with `battleId` itself — there is no
 * client-supplied battle or opponent id left to validate.
 */
class AttackRequestHandlerTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long CHARACTER_A = 1L;
    private static final long CHARACTER_B = 2L;

    private AttackRequestHandler handler;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        FakeCharacterDao characterDao = new FakeCharacterDao()
            .with(CombatFixtures.character(CHARACTER_A, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(CHARACTER_B, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);

        handler = new AttackRequestHandler(new AttackService(new CharacterService(characterDao), new BotFactory()));
    }

    @Test
    void resolvesABattleForAnOwnedCharacter() {
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler.handle(connection, new AttackRequest(CHARACTER_A));

        AttackResponse response = connection.onlySentPacket(AttackResponse.class);
        assertNotNull(response.opponentDisplayName());
        assertTrue(response.turns().size > 0, "the response carries the battle log");
    }

    @Test
    void rejectsUnauthenticatedConnection() {
        FakeGameConnection anonymous = FakeGameConnection.unauthenticated(9);

        handler.handle(anonymous, new AttackRequest(CHARACTER_A));

        assertTrue(anonymous.sentNothing(), "an unauthenticated request is dropped, never resolved");
    }

    @Test
    void rejectsCharacterNotOwnedByTheConnectionsAccount() {
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler.handle(connection, new AttackRequest(CHARACTER_B)); // belongs to account B

        assertTrue(connection.sentNothing(), "a non-owned character can never be sent into battle");
    }
}
