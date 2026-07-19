package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The live quest-progress query (system design §19): {@code countWins} counts exactly the character's wins
 * strictly after a period boundary. This is the test that would have caught §0's gap — before the {@code won}
 * column existed, "wins this period" had no honest way to be computed at all.
 */
class BattleHistoryDaoCountWinsTest {

    private static final long ACCOUNT = 10L;
    private static final long CHARACTER = 1L;
    private static final long OTHER_CHARACTER = 2L;
    private static final String EMPTY = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private BattleHistoryDao dao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create()
            .withAccount(ACCOUNT, 0L)
            .withCharacter(CHARACTER, ACCOUNT, "Ayla", 0L, 1000, EMPTY, EMPTY)
            .withCharacter(OTHER_CHARACTER, ACCOUNT, "Boran", 0L, 1000, EMPTY, EMPTY);
        dao = db.jdbi().onDemand(BattleHistoryDao.class);
    }

    @Test
    void countsExactlyTheInPeriodWins() {
        Instant boundary = Instant.parse("2026-07-15T00:00:00Z");

        db.withBattleHistory(CHARACTER, true, Instant.parse("2026-07-15T10:00:00Z"));  // in-period win
        db.withBattleHistory(CHARACTER, true, Instant.parse("2026-07-16T09:00:00Z"));  // in-period win
        db.withBattleHistory(CHARACTER, false, Instant.parse("2026-07-15T11:00:00Z")); // in-period loss (not a win)
        db.withBattleHistory(CHARACTER, true, Instant.parse("2026-07-14T23:00:00Z"));  // won, but before the boundary
        db.withBattleHistory(CHARACTER, false, Instant.parse("2026-07-14T10:00:00Z")); // lost and before

        assertEquals(2, dao.countWins(CHARACTER, boundary));
    }

    @Test
    void excludesAWinExactlyAtTheBoundaryInstant() {
        // created_at > :since is half-open: a win at the boundary belongs to the previous period.
        Instant boundary = Instant.parse("2026-07-15T00:00:00Z");
        db.withBattleHistory(CHARACTER, true, boundary);
        db.withBattleHistory(CHARACTER, true, Instant.parse("2026-07-15T00:00:01Z"));

        assertEquals(1, dao.countWins(CHARACTER, boundary), "only the win strictly after the boundary counts");
    }

    @Test
    void countsOnlyTheGivenCharactersWins() {
        Instant boundary = Instant.parse("2026-07-15T00:00:00Z");
        db.withBattleHistory(CHARACTER, true, Instant.parse("2026-07-15T10:00:00Z"));
        db.withBattleHistory(OTHER_CHARACTER, true, Instant.parse("2026-07-15T10:00:00Z"));

        assertEquals(1, dao.countWins(CHARACTER, boundary));
        assertEquals(1, dao.countWins(OTHER_CHARACTER, boundary));
    }
}
