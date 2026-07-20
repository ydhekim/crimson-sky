package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.server.database.entity.CharacterEntity;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ranked candidate queries (system design §21), proven against real SQL — the elo band is a
 * correlated subquery over {@code battle_history} that {@code FakeCharacterDao} explicitly cannot
 * model, so this test (mirroring {@code BattleHistoryDaoCountWinsTest}'s precedent) is where band
 * correctness actually lives. A candidate's ranked Elo is {@code 1000 + SUM(ranked_elo_delta)} of its
 * RANKED rows — never a stored column.
 */
class CharacterDaoRankedOpponentCandidatesTest {

    private static final long ACCOUNT = 10L;
    private static final long REQUESTER = 1L;
    private static final long IN_BAND = 2L;
    private static final long OUT_OF_BAND = 3L;
    private static final long UNDER_LEVELED = 4L;
    private static final long BASELINE = 5L;
    private static final String EMPTY = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private CharacterDao dao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create()
            .withAccount(ACCOUNT, 0L)
            .withCharacter(REQUESTER, ACCOUNT, "Ayla", 25, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(IN_BAND, ACCOUNT, "Boran", 25, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(OUT_OF_BAND, ACCOUNT, "Cem", 30, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(UNDER_LEVELED, ACCOUNT, "Derya", 24, 0L, 1000, EMPTY, EMPTY)
            .withCharacter(BASELINE, ACCOUNT, "Esen", 25, 0L, 1000, EMPTY, EMPTY);

        // IN_BAND's ranked Elo is a real SUM (two rows, one negative): 1000 + 100 − 50 = 1050.
        db.withRankedBattleHistory(IN_BAND, 100, Instant.parse("2026-07-01T10:00:00Z"))
            .withRankedBattleHistory(IN_BAND, -50, Instant.parse("2026-07-02T10:00:00Z"))
            // OUT_OF_BAND has climbed to 1400 — outside the ±100 band around 1000.
            .withRankedBattleHistory(OUT_OF_BAND, 400, Instant.parse("2026-07-01T10:00:00Z"))
            // UNDER_LEVELED sits at exactly the baseline — in band, but level 24.
            // BASELINE has no ranked history at all: COALESCE must read it as exactly 1000.
            .withRankedBattleHistory(UNDER_LEVELED, 0, Instant.parse("2026-07-01T10:00:00Z"));
        dao = db.jdbi().onDemand(CharacterDao.class);
    }

    private static Set<Long> idsOf(List<CharacterEntity> entities) {
        return entities.stream().map(CharacterEntity::id).collect(Collectors.toSet());
    }

    @Test
    void bandedQueryReturnsOnlyInBandLevel25NonSelfCandidates() {
        assertEquals(Set.of(IN_BAND, BASELINE),
            idsOf(dao.findRankedOpponentCandidatesInEloRange(REQUESTER, 900, 1100)),
            "the summed 1050 and the history-less 1000 qualify; 1400 is out of band, level 24 is gated, "
                + "and the requester is excluded");
    }

    @Test
    void bandedQueryFollowsTheLiveSumAsTheBandMoves() {
        assertEquals(Set.of(OUT_OF_BAND),
            idsOf(dao.findRankedOpponentCandidatesInEloRange(REQUESTER, 1300, 1500)),
            "a band around 1400 finds exactly the character whose deltas sum there");
    }

    @Test
    void underLeveledCharacterIsExcludedEvenWithAQualifyingRankedElo() {
        assertEquals(Set.of(IN_BAND, BASELINE),
            idsOf(dao.findRankedOpponentCandidatesInEloRange(REQUESTER, 950, 1050)),
            "UNDER_LEVELED's 1000 is squarely in band — only its level keeps it out (§21)");
    }

    @Test
    void wideningStepReturnsEveryLevel25PlusCharacterRegardlessOfElo() {
        assertEquals(Set.of(IN_BAND, OUT_OF_BAND, BASELINE),
            idsOf(dao.findAllRankedOpponentCandidates(REQUESTER)),
            "the widen step drops the Elo band but keeps the level gate and the self-exclusion");
    }
}
