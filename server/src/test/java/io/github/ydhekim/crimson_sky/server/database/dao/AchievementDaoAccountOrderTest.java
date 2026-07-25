package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.common.model.AccountAchievement;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementCriteriaType;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementScope;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code getAchievementsForAccount} returns rows in ascending {@code id} order (prompt 33 fix #1). Before
 * the {@code ORDER BY ad.id} clause was added, "locked achievements stay in server order" meant whatever
 * order Postgres's heap scan happened to return — non-deterministic, so the AchievementsScreen's locked
 * group could shuffle between restarts. The definitions are seeded out of natural order here so a passing
 * assertion pins the ORDER BY, not incidental insertion order.
 */
class AchievementDaoAccountOrderTest {

    private static final long ACCOUNT = 42L;

    private TestDatabase db;
    private AchievementDao dao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create().withAccount(ACCOUNT, 0L);
        // Seed out of ascending-id order: 3, 1, 2. A correct ORDER BY ad.id must re-sort these to 1, 2, 3.
        db.withPageAchievementDefinition(3, "ACH_THIRD", AchievementScope.ACCOUNT,
                AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, null, null, false, "COMBAT")
            .withPageAchievementDefinition(1, "ACH_FIRST", AchievementScope.ACCOUNT,
                AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, null, null, false, "COMBAT")
            .withPageAchievementDefinition(2, "ACH_SECOND", AchievementScope.ACCOUNT,
                AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, null, null, false, "COMBAT");
        dao = db.jdbi().onDemand(AchievementDao.class);
    }

    @Test
    void returnsRowsInAscendingIdOrder() {
        List<AccountAchievement> result = dao.getAchievementsForAccount(ACCOUNT);

        assertEquals(List.of("ACH_FIRST", "ACH_SECOND", "ACH_THIRD"),
            result.stream().map(AccountAchievement::keyName).toList(),
            "getAchievementsForAccount must return definitions ordered by ad.id ascending, "
                + "regardless of insertion order");
    }
}
