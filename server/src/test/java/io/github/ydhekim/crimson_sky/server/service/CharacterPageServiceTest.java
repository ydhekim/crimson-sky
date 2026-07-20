package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.CharacterPage;
import io.github.ydhekim.crimson_sky.common.model.CharacterPageAchievement;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.RecentMatch;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementCriteriaType;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementScope;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The character-page aggregate (system design §22, Epic S3) and title equip (S4), against a real (in-memory)
 * database — mirrors {@code LadderServiceTest}/{@code AchievementUnlockServiceTest}: statistics, match history
 * and unlocks are read from real {@code battle_history}/{@code achievement_unlocks} rows exactly as production
 * assembles them, and the ownership/getCharacter reads go through a real {@link CharacterService} over the
 * same H2 {@code characters} rows.
 */
class CharacterPageServiceTest {

    private static final long ACCOUNT = 10L;
    private static final long OTHER_ACCOUNT = 20L;
    private static final long CHARACTER = 1L;
    private static final long OPPONENT = 2L;
    private static final long SECOND_CHARACTER = 3L;
    private static final int ELO = 1000;
    private static final String EMPTY_INVENTORY = "{\"weapons\":[],\"skills\":[],\"pets\":[],\"consumables\":{}}";
    private static final String EMPTY_LOADOUT = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private CharacterPageService service;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create()
            .withAccount(ACCOUNT, 0L)
            .withCharacter(CHARACTER, ACCOUNT, "Ayla", 5, 0L, ELO, EMPTY_INVENTORY, EMPTY_LOADOUT)
            .withCharacter(OPPONENT, ACCOUNT, "Rival", 5, 0L, ELO, EMPTY_INVENTORY, EMPTY_LOADOUT);
        service = new CharacterPageService(db.jdbi(),
            new CharacterService(db.jdbi().onDemand(CharacterDao.class)));
    }

    private CharacterPage page() {
        var result = service.getCharacterPage(ACCOUNT, CHARACTER);
        assertTrue(result.success(), "page assembly should succeed for an owned character");
        return result.data();
    }

    // --- getCharacterPage: statistics --------------------------------------------------------------

    @Test
    void aCharacterWithNoBattlesHasZeroedStatisticsAndNoTitle() {
        CharacterPage page = page();

        assertEquals(0, page.statistics().totalWins());
        assertEquals(0, page.statistics().totalLosses());
        assertEquals(0.0, page.statistics().winPercentage(),
            "no battles reads as 0%, never a divide-by-zero NaN");
        assertEquals(0, page.statistics().currentWinStreak());
        assertNull(page.statistics().fastestWinTurns(), "never won → no fastest win");
        assertTrue(page.statistics().recentMatches().isEmpty());
        assertNull(page.equippedTitle(), "nothing equipped by default");
    }

    @Test
    void matchHistoryIsNewestFirstCappedAtFiveWithBotAsPlaceholder() {
        // Six battles oldest→newest; the oldest (t1) must fall off the last-5 window.
        db.withMatchHistory(CHARACTER, null, false, "NORMAL", -10, null, 8, at("2026-07-01T10:00:00Z"))   // t1 loss, bot
            .withMatchHistory(CHARACTER, OPPONENT, true, "NORMAL", 12, null, 5, at("2026-07-02T10:00:00Z"))  // t2 win
            .withMatchHistory(CHARACTER, null, true, "NORMAL", 11, null, 4, at("2026-07-03T10:00:00Z"))      // t3 win, bot
            .withMatchHistory(CHARACTER, OPPONENT, false, "RANKED", -8, -12, 9, at("2026-07-04T10:00:00Z"))  // t4 loss, ranked
            .withMatchHistory(CHARACTER, OPPONENT, true, "RANKED", 9, 15, 3, at("2026-07-05T10:00:00Z"))     // t5 win, ranked
            .withMatchHistory(CHARACTER, null, true, "NORMAL", 7, null, 6, at("2026-07-06T10:00:00Z"));      // t6 win, bot (newest)

        CharacterPage page = page();

        assertEquals(4, page.statistics().totalWins(), "t2, t3, t5, t6 won");
        assertEquals(2, page.statistics().totalLosses(), "t1, t4 lost");
        assertEquals(400.0 / 6, page.statistics().winPercentage(), 1e-9, "4 of 6 battles");
        assertEquals(2, page.statistics().currentWinStreak(), "t6 and t5 are the leading win run; t4 is a loss");
        assertEquals(3, page.statistics().fastestWinTurns(), "t5's 3-turn win is the fastest");

        List<RecentMatch> matches = page.statistics().recentMatches();
        assertEquals(5, matches.size(), "capped at 5, oldest t1 dropped");

        RecentMatch newest = matches.get(0);
        assertTrue(newest.won());
        assertEquals("NORMAL", newest.battleMode());
        assertNull(newest.rankedEloDelta(), "a NORMAL battle carries no ranked delta");
        assertEquals("Unknown Challenger", newest.opponentName(),
            "a bot opponent surfaces the placeholder, never a NULL or a tell");

        RecentMatch secondNewest = matches.get(1);
        assertEquals("Rival", secondNewest.opponentName(), "a real opponent keeps its name");
        assertEquals("RANKED", secondNewest.battleMode());
        assertEquals(15, secondNewest.rankedEloDelta());
    }

    // --- getCharacterPage: achievements ------------------------------------------------------------

    @Test
    void achievementScoreSumsOnlyUnlockedPointsAndBothAppear() {
        db.withPageAchievementDefinition(100L, "FIRST_BLOOD", AchievementScope.CHARACTER,
            AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, "badge_fb", "TITLE_FB", false, "COMBAT");
        db.withPageAchievementDefinition(200L, "TEN_WINS", AchievementScope.CHARACTER,
            AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":10}", 40, null, null, false, "COMBAT");
        db.withAchievementUnlock(ACCOUNT, 100L, CHARACTER); // only FIRST_BLOOD is unlocked

        CharacterPage page = page();

        List<CharacterPageAchievement> achievements = page.achievements();
        assertEquals(2, achievements.size(), "every definition appears, unlocked or not");

        CharacterPageAchievement first = achievements.get(0); // ordered by ad.id
        assertEquals("FIRST_BLOOD", first.keyName());
        assertEquals("FIRST_BLOOD_TITLE", first.titleLocKey());
        assertEquals("FIRST_BLOOD_DESC", first.descLocKey());
        assertTrue(first.isUnlocked());

        CharacterPageAchievement second = achievements.get(1);
        assertEquals("TEN_WINS", second.keyName());
        assertFalse(second.isUnlocked());

        assertEquals(10, page.achievementScore(), "only the unlocked FIRST_BLOOD's 10 points count");
    }

    @Test
    void aPageForACharacterTheCallerDoesNotOwnFailsAndLeaksNothing() {
        var result = service.getCharacterPage(OTHER_ACCOUNT, CHARACTER);

        assertFalse(result.success());
        assertEquals(MessageCode.ERROR_UNKNOWN, result.code());
        assertNull(result.data(), "no page assembled for a character the account does not own");
    }

    // --- setEquippedTitle --------------------------------------------------------------------------

    @Test
    void equippingAnUnlockedTitleSucceedsAndPersists() {
        db.withPageAchievementDefinition(100L, "FIRST_BLOOD", AchievementScope.CHARACTER,
            AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, "badge_fb", "TITLE_FB", false, "COMBAT");
        db.withAchievementUnlock(ACCOUNT, 100L, CHARACTER);

        var result = service.setEquippedTitle(ACCOUNT, CHARACTER, "TITLE_FB");

        assertTrue(result.success());
        assertEquals("TITLE_FB", result.data());
        assertEquals("TITLE_FB", db.equippedTitleOf(CHARACTER), "the equip persisted");
        assertEquals("TITLE_FB", page().equippedTitle(), "and surfaces on the page");
    }

    @Test
    void equippingATitleWithNoMatchingUnlockIsRejectedAndLeavesTheColumnUntouched() {
        db.withPageAchievementDefinition(100L, "FIRST_BLOOD", AchievementScope.CHARACTER,
            AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, "badge_fb", "TITLE_FB", false, "COMBAT");
        db.withAchievementUnlock(ACCOUNT, 100L, CHARACTER);
        assertTrue(service.setEquippedTitle(ACCOUNT, CHARACTER, "TITLE_FB").success(), "start with a real title on");

        var result = service.setEquippedTitle(ACCOUNT, CHARACTER, "TITLE_NEVER_UNLOCKED");

        assertFalse(result.success());
        assertEquals(MessageCode.TITLE_NOT_UNLOCKED, result.code());
        assertEquals("TITLE_FB", db.equippedTitleOf(CHARACTER), "the rejected equip changed nothing");
    }

    @Test
    void clearingTheTitleAlwaysSucceedsRegardlessOfUnlockState() {
        db.withPageAchievementDefinition(100L, "FIRST_BLOOD", AchievementScope.CHARACTER,
            AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 10, "badge_fb", "TITLE_FB", false, "COMBAT");
        db.withAchievementUnlock(ACCOUNT, 100L, CHARACTER);
        service.setEquippedTitle(ACCOUNT, CHARACTER, "TITLE_FB");

        var result = service.setEquippedTitle(ACCOUNT, CHARACTER, null);

        assertTrue(result.success(), "clearing never runs an unlock check");
        assertNull(result.data());
        assertNull(db.equippedTitleOf(CHARACTER), "the column was cleared");
    }

    @Test
    void anAccountScopeTitleIsWearableByAnyCharacterOnTheAccount() {
        // The unlock write for an ACCOUNT-scope achievement carries character_id = NULL, so the title belongs
        // to the whole account — any character on it can wear it, not just the one that triggered the unlock.
        db.withCharacter(SECOND_CHARACTER, ACCOUNT, "Boran", 5, 0L, ELO, EMPTY_INVENTORY, EMPTY_LOADOUT);
        db.withPageAchievementDefinition(300L, "PIONEER", AchievementScope.ACCOUNT,
            AchievementCriteriaType.ACCOUNT_CREATED_BEFORE, "{\"date\":\"2026-12-31\"}", 25, "badge_p", "TITLE_ACC",
            false, "ONBOARDING");
        db.withAchievementUnlock(ACCOUNT, 300L, null); // account-scope: character_id NULL

        var result = service.setEquippedTitle(ACCOUNT, SECOND_CHARACTER, "TITLE_ACC");

        assertTrue(result.success(), "an account-scope title is wearable by a character that never triggered it");
        assertEquals("TITLE_ACC", db.equippedTitleOf(SECOND_CHARACTER));
    }

    private static Instant at(String iso) {
        return Instant.parse(iso);
    }
}
