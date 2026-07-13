package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.AttackRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.AttackResponse;
import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.service.AttackService;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.RewardService;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.FakeGameConnection;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The B3 ownership guardrail carries forward unchanged into B4's handler (system design §6). The old
 * "is this character a participant in `battleId`" check is gone with `battleId` itself — there is no
 * client-supplied battle or opponent id left to validate.
 *
 * <p>Story C1 adds a second responsibility to the same handler: rewards are applied between resolving
 * the battle and answering, and the response reports what was paid. Crucially, a reward that fails to
 * persist must not cost the player the battle they just fought — the last test pins that.
 */
class AttackRequestHandlerTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long CHARACTER_A = 1L;
    private static final long CHARACTER_B = 2L;
    private static final String NO_ITEMS = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private FakeCharacterDao characterDao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        characterDao = new FakeCharacterDao()
            .with(CombatFixtures.character(CHARACTER_A, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(CHARACTER_B, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);
        db = TestDatabase.create();
    }

    /** Gives the reward path rows to write to. Omit it and every reward transaction fails. */
    private void seedRewardTables() {
        db.withAccount(ACCOUNT_A, 0L).withAccount(ACCOUNT_B, 0L)
            .withCharacter(CHARACTER_A, ACCOUNT_A, "Ayla", 0L, 1000, NO_ITEMS, NO_ITEMS)
            .withCharacter(CHARACTER_B, ACCOUNT_B, "Boran", 0L, 1000, NO_ITEMS, NO_ITEMS);
    }

    private AttackRequestHandler handler() {
        CharacterService characterService = new CharacterService(characterDao);
        return new AttackRequestHandler(
            new AttackService(characterService, new BotFactory()),
            new RewardService(db.jdbi(), characterService));
    }

    @Test
    void resolvesABattleForAnOwnedCharacter() {
        seedRewardTables();
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler().handle(connection, new AttackRequest(CHARACTER_A));

        AttackResponse response = connection.onlySentPacket(AttackResponse.class);
        assertNotNull(response.opponentDisplayName());
        assertTrue(response.turns().size > 0, "the response carries the battle log");
    }

    @Test
    void reportsTheRewardsItJustCommitted() {
        seedRewardTables();
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler().handle(connection, new AttackRequest(CHARACTER_A));

        AttackResponse response = connection.onlySentPacket(AttackResponse.class);
        assertTrue(response.goldDelta() > 0, "every battle pays something, win or lose");
        assertTrue(response.expDelta() > 0L);
        assertNotEquals(0, response.eloDelta(), "an evenly rated fight always moves the rating");
        assertEquals(response.goldDelta(), db.goldOf(ACCOUNT_A), "what the client is told is what was written");
        assertEquals(response.expDelta(), db.experienceOf(CHARACTER_A));
        assertEquals(1, db.battleHistoryRowCount());
    }

    @Test
    void stillSendsTheBattleWhenItsRewardFailsToPersist() {
        // No rows seeded: the reward transaction's history insert violates its foreign key and rolls back.
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler().handle(connection, new AttackRequest(CHARACTER_A));

        AttackResponse response = connection.onlySentPacket(AttackResponse.class);
        assertTrue(response.turns().size > 0, "the fight already happened — the player still gets to see it");
        assertEquals(0, response.goldDelta(), "a rolled-back reward is reported as a zero payout, not a lie");
        assertEquals(0L, response.expDelta());
        assertEquals(0, response.eloDelta());
        assertEquals(0, db.battleHistoryRowCount(), "nothing partially committed");
    }

    @Test
    void rejectsUnauthenticatedConnection() {
        seedRewardTables();
        FakeGameConnection anonymous = FakeGameConnection.unauthenticated(9);

        handler().handle(anonymous, new AttackRequest(CHARACTER_A));

        assertTrue(anonymous.sentNothing(), "an unauthenticated request is dropped, never resolved");
    }

    @Test
    void rejectsCharacterNotOwnedByTheConnectionsAccount() {
        seedRewardTables();
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler().handle(connection, new AttackRequest(CHARACTER_B)); // belongs to account B

        assertTrue(connection.sentNothing(), "a non-owned character can never be sent into battle");
        assertEquals(0, db.battleHistoryRowCount(), "a rejected attack pays nothing and records nothing");
    }
}
