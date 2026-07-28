package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.common.model.AccountAchievement;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementCriteriaType;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementScope;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code getAchievementsForAccount} as a genuine account-wide aggregate (K14). The shipped query joined with
 * a flat {@code au.character_id IS NULL}, which matches only ACCOUNT-scope unlock rows — so the 8 of 10
 * seeded achievements that are CHARACTER-scope showed permanently locked on MainMenu's AchievementsScreen no
 * matter what a player had actually done, because nothing ever writes a CHARACTER-scope unlock with a NULL
 * character_id. The first test here is the one that would have caught that.
 *
 * <p>The other half of the fix is shape, not truth: one row per definition even when several of an account's
 * characters independently unlocked the same CHARACTER-scope achievement, since a fanned-out row set would
 * both render duplicates and inflate the screen's unlocked-count math.
 */
class AchievementDaoAccountAggregateTest {

    private static final long ACCOUNT = 42L;
    private static final long OTHER_ACCOUNT = 99L;
    private static final long CHARACTER_A = 1L;
    private static final long CHARACTER_B = 2L;
    private static final long OTHER_ACCOUNTS_CHARACTER = 3L;

    // Definition ids, seeded in ascending order so the DAO's ORDER BY ad.id makes positions predictable.
    private static final long ACCOUNT_SCOPE = 10L;
    private static final long CHARACTER_SCOPE = 20L;
    private static final long NEVER_UNLOCKED = 30L;

    private static final String EMPTY_INVENTORY = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";
    private static final String EMPTY_LOADOUT = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private AchievementDao dao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create()
            .withAccount(ACCOUNT, 0L)
            .withAccount(OTHER_ACCOUNT, 0L);
        seedCharacter(CHARACTER_A, ACCOUNT, "Ayla");
        seedCharacter(CHARACTER_B, ACCOUNT, "Boran");
        seedCharacter(OTHER_ACCOUNTS_CHARACTER, OTHER_ACCOUNT, "Stranger");

        db.withPageAchievementDefinition(ACCOUNT_SCOPE, "ACH_ACCOUNT_SCOPE", AchievementScope.ACCOUNT,
                AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, null, null, false, "COMBAT")
            .withPageAchievementDefinition(CHARACTER_SCOPE, "ACH_CHARACTER_SCOPE", AchievementScope.CHARACTER,
                AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, null, null, false, "COMBAT")
            .withPageAchievementDefinition(NEVER_UNLOCKED, "ACH_NEVER_UNLOCKED", AchievementScope.CHARACTER,
                AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, null, null, false, "COMBAT");

        dao = db.jdbi().onDemand(AchievementDao.class);
    }

    private void seedCharacter(long characterId, long accountId, String name) {
        db.withCharacter(characterId, accountId, name, 0L, 1000, EMPTY_INVENTORY, EMPTY_LOADOUT);
    }

    private AccountAchievement find(String keyName) {
        return dao.getAchievementsForAccount(ACCOUNT).stream()
            .filter(a -> a.keyName().equals(keyName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no achievement named " + keyName + " in the account view"));
    }

    // --- the regression this fix exists for --------------------------------------------------------

    @Test
    void aCharacterScopeUnlockOnAnyCharacterCountsAccountWide() {
        // Character B earned it; character A never did. The account has earned it.
        db.withAchievementUnlock(ACCOUNT, CHARACTER_SCOPE, CHARACTER_B);

        AccountAchievement earned = find("ACH_CHARACTER_SCOPE");
        assertTrue(earned.isUnlocked(),
            "a CHARACTER-scope achievement unlocked by one of the account's characters must read as unlocked "
                + "account-wide — this is exactly what the old character_id IS NULL join could never report");
        assertNotNull(earned.unlockedAt(), "an unlocked achievement must carry the timestamp it was earned at");
    }

    @Test
    void anAchievementNoCharacterHasUnlockedStaysLocked() {
        db.withAchievementUnlock(ACCOUNT, CHARACTER_SCOPE, CHARACTER_B);

        AccountAchievement unearned = find("ACH_NEVER_UNLOCKED");
        assertFalse(unearned.isUnlocked(), "nobody unlocked this one");
        assertNull(unearned.unlockedAt(), "and it carries no timestamp");
    }

    // --- ACCOUNT-scope behaviour is unchanged ------------------------------------------------------

    @Test
    void anAccountScopeUnlockStillReadsAsUnlocked() {
        db.withAchievementUnlock(ACCOUNT, ACCOUNT_SCOPE, null);

        assertTrue(find("ACH_ACCOUNT_SCOPE").isUnlocked(), "the pre-existing ACCOUNT-scope path is untouched");
    }

    @Test
    void aCharacterScopedUnlockRowDoesNotSatisfyAnAccountScopeDefinition() {
        // A row with a character_id against an ACCOUNT-scope definition is not a shape the unlock engine
        // writes; the scope branch must still refuse to count it, rather than matching on achievement_id alone.
        db.withAchievementUnlock(ACCOUNT, ACCOUNT_SCOPE, CHARACTER_A);

        assertFalse(find("ACH_ACCOUNT_SCOPE").isUnlocked(),
            "an ACCOUNT-scope achievement is earned only by an account-scope (NULL character) unlock row");
    }

    // --- shape: one row per definition, earliest unlock wins ---------------------------------------

    @Test
    void twoCharactersUnlockingTheSameAchievementStillProduceExactlyOneRow() {
        db.withAchievementUnlock(ACCOUNT, CHARACTER_SCOPE, CHARACTER_A);
        db.withAchievementUnlock(ACCOUNT, CHARACTER_SCOPE, CHARACTER_B);

        List<AccountAchievement> all = dao.getAchievementsForAccount(ACCOUNT);

        assertEquals(3, all.size(), "one row per definition — a fanned-out join would return 4 here");
        assertEquals(List.of("ACH_ACCOUNT_SCOPE", "ACH_CHARACTER_SCOPE", "ACH_NEVER_UNLOCKED"),
            all.stream().map(AccountAchievement::keyName).toList(),
            "no duplicate entries, still ordered by ad.id");
        assertEquals(1, all.stream().filter(AccountAchievement::isUnlocked).count(),
            "the screen's unlocked count must not be inflated by a second character earning the same thing");
    }

    @Test
    void theEarliestQualifyingUnlockIsTheOneReported() {
        Instant earlier = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant later = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        db.withAchievementUnlock(ACCOUNT, CHARACTER_SCOPE, CHARACTER_B, later);
        db.withAchievementUnlock(ACCOUNT, CHARACTER_SCOPE, CHARACTER_A, earlier);

        String reported = find("ACH_CHARACTER_SCOPE").unlockedAt();

        // unlocked_at is a timezone-less TIMESTAMP read back as text, so compare against the same local-time
        // rendering the insert wrote — an Instant's own UTC toString() would differ by the JVM's offset.
        assertTrue(reported.startsWith(localTimestampPrefix(earlier)),
            "MIN(unlocked_at) reports when the account first earned it, not the most recent character to; "
                + "expected " + localTimestampPrefix(earlier) + " but got " + reported);
        assertFalse(reported.startsWith(localTimestampPrefix(later)), "the later unlock must not win");
    }

    /** {@code yyyy-MM-dd HH:mm:ss} in the JVM's zone — how a {@code TIMESTAMP} column renders as text. */
    private static String localTimestampPrefix(Instant instant) {
        return Timestamp.from(instant).toString().substring(0, 19);
    }

    // --- isolation ---------------------------------------------------------------------------------

    @Test
    void anotherAccountsCharacterUnlockDoesNotLeakIn() {
        db.withAchievementUnlock(OTHER_ACCOUNT, CHARACTER_SCOPE, OTHER_ACCOUNTS_CHARACTER);

        assertFalse(find("ACH_CHARACTER_SCOPE").isUnlocked(),
            "CHARACTER-scope unlocks count only for characters this account owns");
        assertTrue(dao.getAchievementsForAccount(OTHER_ACCOUNT).stream()
                .filter(a -> a.keyName().equals("ACH_CHARACTER_SCOPE"))
                .allMatch(AccountAchievement::isUnlocked),
            "and it does count for the account that actually owns that character");
    }
}
