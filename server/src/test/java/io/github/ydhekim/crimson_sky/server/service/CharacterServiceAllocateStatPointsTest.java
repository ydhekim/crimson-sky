package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.server.service.CharacterService.AllocateStatPointsResult;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Epic L / system design §15: spending earned stat points. Validation lives in
 * {@link CharacterService#allocateStatPoints}; this pins each rejection reason and a successful partial
 * spend against an in-memory DAO (no Postgres — the atomic guard's SQL is exercised through H2 in the
 * reward tests; here the concern is the service's ordering of checks and its arithmetic).
 */
class CharacterServiceAllocateStatPointsTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long CHARACTER = 1L;

    private FakeCharacterDao dao;
    private CharacterService characterService;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        dao = new FakeCharacterDao();
        characterService = new CharacterService(dao);
    }

    private static Stats stats(int str, int dex, int vit, int intel, int wis, int spi, int spd, int ins) {
        return new Stats(str, dex, vit, intel, wis, spi, spd, ins);
    }

    private void seed(long characterId, long accountId, Stats stats, int unspentStatPoints) {
        Character c = new Character(characterId, accountId, "Ayla", Faction.A, 5, 0, 100, 100, 100, 10, 10,
            stats, new Inventory(null, null, null), new Loadout(null, null, null));
        dao.with(c, accountId, 1000, unspentStatPoints);
    }

    @Test
    void rejectsACharacterTheAccountDoesNotOwn() {
        seed(CHARACTER, ACCOUNT_B, stats(5, 5, 5, 5, 5, 5, 5, 5), 20);

        var result = characterService.allocateStatPoints(ACCOUNT_A, CHARACTER, stats(1, 0, 0, 0, 0, 0, 0, 0));

        assertFalse(result.success(), "a non-owned character can never be modified");
        assertEquals(MessageCode.ERROR_UNKNOWN, result.code());
    }

    @Test
    void rejectsASpendLargerThanTheBalance() {
        seed(CHARACTER, ACCOUNT_A, stats(5, 5, 5, 5, 5, 5, 5, 5), 5);

        // sum(delta) = 10, but only 5 points are available.
        var result = characterService.allocateStatPoints(ACCOUNT_A, CHARACTER, stats(4, 3, 3, 0, 0, 0, 0, 0));

        assertFalse(result.success());
        assertEquals(MessageCode.STAT_POINTS_INSUFFICIENT, result.code());
    }

    @Test
    void rejectsANegativeDeltaComponent() {
        seed(CHARACTER, ACCOUNT_A, stats(5, 5, 5, 5, 5, 5, 5, 5), 20);

        // A spend may only add points — a component trying to *remove* them (and free budget elsewhere)
        // is rejected outright, before any balance arithmetic.
        var result = characterService.allocateStatPoints(ACCOUNT_A, CHARACTER, stats(10, -5, 0, 0, 0, 0, 0, 0));

        assertFalse(result.success());
        assertEquals(MessageCode.STAT_POINTS_INSUFFICIENT, result.code());
    }

    @Test
    void rejectsASpendThatWouldPushAStatOverTheCap() {
        // Strength already near the cap; plenty of points, but the merge would exceed Stats.MAX_STAT_VALUE.
        seed(CHARACTER, ACCOUNT_A, stats(58, 5, 5, 5, 5, 5, 5, 5), 100);

        var result = characterService.allocateStatPoints(ACCOUNT_A, CHARACTER, stats(5, 0, 0, 0, 0, 0, 0, 0));

        assertFalse(result.success());
        assertEquals(MessageCode.STAT_CAP_EXCEEDED, result.code());
    }

    @Test
    void appliesAValidPartialSpendAndReportsTheRemainingBalance() {
        seed(CHARACTER, ACCOUNT_A, stats(5, 5, 5, 5, 5, 5, 5, 5), 10);

        var result = characterService.allocateStatPoints(ACCOUNT_A, CHARACTER, stats(3, 2, 1, 0, 0, 0, 0, 0));

        assertTrue(result.success());
        AllocateStatPointsResult data = result.data();
        assertEquals(stats(8, 7, 6, 5, 5, 5, 5, 5), data.newStats(), "the delta is merged component-wise");
        assertEquals(4, data.unspentStatPoints(), "10 available − 6 spent = 4 remaining");
        assertEquals(4, dao.getUnspentStatPoints(CHARACTER).orElseThrow(),
            "the balance is decremented in storage, not just reported");
    }
}
