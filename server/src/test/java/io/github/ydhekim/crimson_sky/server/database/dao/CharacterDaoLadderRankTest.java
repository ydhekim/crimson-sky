package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code countRankedCharactersAboveEloAsOf} (system design §21), proven against real SQL — the ranked Elo
 * it ranks by is a correlated {@code SUM} over {@code battle_history} that {@code FakeCharacterDao} cannot
 * model (its stub returns 0), so — mirroring {@code CharacterDaoRankedOpponentCandidatesTest}'s precedent —
 * this is where rank correctness actually lives. One more than the count this returns is a character's
 * ladder rank.
 */
class CharacterDaoLadderRankTest {

    private static final long ACCOUNT = 10L;
    private static final long REQUESTER = 1L;
    private static final long HIGHER = 2L;
    private static final long SAME = 3L;
    private static final long LOWER = 4L;
    private static final long UNDER_LEVELED = 5L;
    private static final long LATE = 6L;
    private static final String EMPTY = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    /** The month boundary a ladder claim ranks "as of" — one instant before this-month began, in the fixture. */
    private static final Instant AS_OF = Instant.parse("2026-06-30T23:59:59Z");

    private TestDatabase db;
    private CharacterDao dao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create()
            .withAccount(ACCOUNT, 0L)
            .withCharacter(REQUESTER, ACCOUNT, "Ayla", 25, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(HIGHER, ACCOUNT, "Boran", 25, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(SAME, ACCOUNT, "Cem", 25, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(LOWER, ACCOUNT, "Derya", 25, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(UNDER_LEVELED, ACCOUNT, "Esen", 24, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(LATE, ACCOUNT, "Faruk", 25, 0L, 1000, EMPTY, EMPTY);

        // Standings as of AS_OF (each is 1000 + SUM of its RANKED deltas up to AS_OF):
        db.withRankedBattleHistory(HIGHER, 200, Instant.parse("2026-06-01T10:00:00Z"))   // 1200, strictly above
            .withRankedBattleHistory(LOWER, -100, Instant.parse("2026-06-01T10:00:00Z")) // 900, below
            .withRankedBattleHistory(UNDER_LEVELED, 400, Instant.parse("2026-06-01T10:00:00Z")) // 1400 but level 24
            // SAME has no history — COALESCE reads it as exactly 1000 (equal, not strictly above).
            // LATE's only qualifying battle is dated AFTER AS_OF, so as of AS_OF it reads as baseline 1000.
            .withRankedBattleHistory(LATE, 500, Instant.parse("2026-07-15T10:00:00Z"));
        dao = db.jdbi().onDemand(CharacterDao.class);
    }

    @Test
    void countsOnlyStrictlyHigherLevel25NonSelfCharacters() {
        assertEquals(1, dao.countRankedCharactersAboveEloAsOf(REQUESTER, 1000, AS_OF),
            "only HIGHER (1200) is strictly above 1000; SAME (equal), LOWER (900), UNDER_LEVELED (gated), "
                + "LATE (still baseline as of AS_OF) and the requester itself are all excluded");
    }

    @Test
    void anUnderLeveledCharacterIsNeverCountedEvenWithAQualifyingElo() {
        assertEquals(0, dao.countRankedCharactersAboveEloAsOf(REQUESTER, 1350, AS_OF),
            "UNDER_LEVELED's 1400 clears a 1350 bar no level-25 character does — only its level keeps it out (§21)");
    }

    @Test
    void theRequesterItselfIsNeverCounted() {
        // At a 999 threshold every level-25 character at or above 1000 qualifies on Elo — HIGHER, SAME and
        // LATE (baseline 1000 as of AS_OF). The requester's own 1000 would be a fourth were it not excluded.
        assertEquals(3, dao.countRankedCharactersAboveEloAsOf(REQUESTER, 999, AS_OF),
            "HIGHER, SAME and LATE all sit above 999; the requester's own 1000 is excluded, UNDER_LEVELED gated");
    }

    @Test
    void theAsOfBoundExcludesABattleThatHappenedAfterIt() {
        assertEquals(1, dao.countRankedCharactersAboveEloAsOf(REQUESTER, 1000, AS_OF),
            "as of AS_OF, LATE's post-boundary +500 has not happened yet, so it reads as baseline 1000");

        Instant afterLate = Instant.parse("2026-07-31T23:59:59Z");
        assertEquals(2, dao.countRankedCharactersAboveEloAsOf(REQUESTER, 1000, afterLate),
            "as of a later bound, LATE's +500 (→1500) lands and now counts alongside HIGHER");
    }
}
