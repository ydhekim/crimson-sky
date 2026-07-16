package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.network.packet.AllocateStatPointsRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.AllocateStatPointsResponse;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.FakeGameConnection;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Epic L / system design §15 — the stat-point spend handler. Two guardrails are drops (no answer):
 * unauthenticated connections and non-owned characters, the same posture as {@code AttackRequestHandler}.
 * Actionable validation failures (insufficient points, over cap) are answered so the client can explain
 * the refusal.
 */
class AllocateStatPointsRequestHandlerTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long CHARACTER_A = 1L;
    private static final long CHARACTER_B = 2L;

    private FakeCharacterDao dao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        dao = new FakeCharacterDao();
    }

    private static Stats stats(int str, int dex, int vit, int intel, int wis, int spi, int spd, int ins) {
        return new Stats(str, dex, vit, intel, wis, spi, spd, ins);
    }

    private void seed(long characterId, long accountId, Stats stats, int unspentStatPoints) {
        Character c = new Character(characterId, accountId, "Ayla", Faction.A, 5, 0, 100, 100, 100, 10, 10,
            stats, new Inventory(null, null, null), new Loadout(null, null, null), new java.util.HashMap<>());
        dao.with(c, accountId, 1000, unspentStatPoints);
    }

    private AllocateStatPointsRequestHandler handler() {
        return new AllocateStatPointsRequestHandler(new CharacterService(dao));
    }

    @Test
    void appliesAndReportsAValidSpend() {
        seed(CHARACTER_A, ACCOUNT_A, stats(5, 5, 5, 5, 5, 5, 5, 5), 10);
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler().handle(connection, new AllocateStatPointsRequest(CHARACTER_A, stats(3, 0, 0, 0, 0, 0, 0, 0)));

        AllocateStatPointsResponse response = connection.onlySentPacket(AllocateStatPointsResponse.class);
        assertTrue(response.success());
        assertEquals(8, response.newStats().strength());
        assertEquals(7, response.unspentStatPoints(), "10 − 3 = 7 remaining");
    }

    @Test
    void answersAnOverBudgetSpendWithAFailureTheClientCanShow() {
        seed(CHARACTER_A, ACCOUNT_A, stats(5, 5, 5, 5, 5, 5, 5, 5), 2);
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler().handle(connection, new AllocateStatPointsRequest(CHARACTER_A, stats(5, 0, 0, 0, 0, 0, 0, 0)));

        AllocateStatPointsResponse response = connection.onlySentPacket(AllocateStatPointsResponse.class);
        assertFalse(response.success());
        assertEquals(MessageCode.STAT_POINTS_INSUFFICIENT.name(), response.message());
    }

    @Test
    void dropsAnUnauthenticatedRequest() {
        seed(CHARACTER_A, ACCOUNT_A, stats(5, 5, 5, 5, 5, 5, 5, 5), 10);
        FakeGameConnection anonymous = FakeGameConnection.unauthenticated(9);

        handler().handle(anonymous, new AllocateStatPointsRequest(CHARACTER_A, stats(1, 0, 0, 0, 0, 0, 0, 0)));

        assertTrue(anonymous.sentNothing(), "an unauthenticated spend is dropped, never answered");
    }

    @Test
    void dropsASpendOnACharacterTheAccountDoesNotOwn() {
        seed(CHARACTER_B, ACCOUNT_B, stats(5, 5, 5, 5, 5, 5, 5, 5), 10);
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler().handle(connection, new AllocateStatPointsRequest(CHARACTER_B, stats(1, 0, 0, 0, 0, 0, 0, 0)));

        assertTrue(connection.sentNothing(), "a non-owned character's points can never be spent — and it is not answered");
    }
}
