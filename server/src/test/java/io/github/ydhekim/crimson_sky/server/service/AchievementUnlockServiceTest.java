package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.server.achievement.AccountAchievementFacts;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementCriteriaType;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementScope;
import io.github.ydhekim.crimson_sky.server.achievement.CharacterAchievementFacts;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The DB-touching orchestrator (system design §22), against a real (in-memory) database — mirrors
 * {@code LadderServiceTest}'s shape: the unlock row and its reward commit or roll back together, a property
 * only a real transaction can show. The facts themselves are handed in directly (the pure evaluator is
 * covered by {@code AchievementEvaluatorTest}); what's exercised here is idempotency, the two-scope partial
 * index, and that every reward column actually lands.
 */
class AchievementUnlockServiceTest {

    private static final long ACCOUNT = 10L;
    private static final long CHARACTER = 1L;
    private static final int ELO = 1000;
    private static final long STARTING_GOLD = 100L;
    private static final String EMPTY_INVENTORY = "{\"weapons\":[],\"skills\":[],\"pets\":[],\"consumables\":{}}";
    private static final String EMPTY_LOADOUT = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private AchievementUnlockService service;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create()
            .withAccount(ACCOUNT, STARTING_GOLD)
            .withCharacter(CHARACTER, ACCOUNT, "Ayla", 0L, ELO, EMPTY_INVENTORY, EMPTY_LOADOUT);
        service = new AchievementUnlockService();
    }

    /** A character-scope win-count achievement carrying every kind of reward, so one pass exercises them all. */
    private void seedRichCharacterAchievement() {
        db.withAchievementDefinition(100L, "FIRST_BLOOD", AchievementScope.CHARACTER,
            AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":1}", 100, 50, 2, 3, 10);
    }

    private CharacterAchievementFacts oneWin() {
        return new CharacterAchievementFacts(1, 1, null, 1, List.of());
    }

    private List<?> evaluateCharacter(CharacterAchievementFacts facts) {
        return db.jdbi().inTransaction(handle ->
            service.evaluateCharacterAchievements(handle, ACCOUNT, CHARACTER, facts));
    }

    @Test
    void aSatisfiedCharacterAchievementUnlocksOnceAndAppliesEveryReward() {
        seedRichCharacterAchievement();

        List<?> unlocked = evaluateCharacter(oneWin());

        assertEquals(1, unlocked.size(), "exactly one achievement unlocked");
        assertEquals(1, db.achievementUnlockCountOf(ACCOUNT), "exactly one unlock row");
        assertEquals(STARTING_GOLD + 50, db.goldOf(ACCOUNT), "gold reward landed on the account");
        assertEquals(100L, db.experienceOf(CHARACTER), "XP reward landed on the character");
        assertEquals(3 + 2, db.maxSlotsOf(ACCOUNT), "bonus character slots moved accounts.max_slots (Epic Q)");
        assertEquals(3, db.bonusDailyBattlesOf(CHARACTER), "bonus daily battles moved characters.bonus_daily_battles (Epic Q)");
    }

    @Test
    void reEvaluatingTheSameSatisfiedFactsUnlocksAndRewardsOnlyOnce() {
        seedRichCharacterAchievement();

        evaluateCharacter(oneWin());
        List<?> second = evaluateCharacter(oneWin());

        assertEquals(0, second.size(), "the second pass finds nothing new — ON CONFLICT no-oped the insert");
        assertEquals(1, db.achievementUnlockCountOf(ACCOUNT), "still exactly one unlock row");
        assertEquals(STARTING_GOLD + 50, db.goldOf(ACCOUNT), "gold was not paid twice");
        assertEquals(100L, db.experienceOf(CHARACTER), "XP was not granted twice");
        assertEquals(3 + 2, db.maxSlotsOf(ACCOUNT), "slots were not granted twice");
        assertEquals(3, db.bonusDailyBattlesOf(CHARACTER), "bonus battles were not granted twice");
    }

    @Test
    void anAccountScopeAndACharacterScopeUnlockCoexistForTheSameAccount() {
        // Same account/achievement-id space, different scopes: the two partial indexes (modelled in H2 by the
        // generated character_key) must let both rows live rather than colliding on each other.
        seedRichCharacterAchievement();
        db.withAchievementDefinition(200L, "PIONEER", AchievementScope.ACCOUNT,
            AchievementCriteriaType.ACCOUNT_CREATED_BEFORE, "{\"date\":\"2026-12-31\"}", 0, 25, 0, 0, 10);

        evaluateCharacter(oneWin());
        db.jdbi().useTransaction(handle -> service.evaluateAccountAchievements(
            handle, ACCOUNT, new AccountAchievementFacts(Instant.parse("2026-06-01T00:00:00Z"))));

        assertEquals(2, db.achievementUnlockCountOf(ACCOUNT), "one character-scope and one account-scope unlock");
        assertEquals(STARTING_GOLD + 50 + 25, db.goldOf(ACCOUNT), "both gold rewards landed");
    }

    @Test
    void anUnsatisfiedAchievementUnlocksNothing() {
        db.withAchievementDefinition(100L, "TEN_WINS", AchievementScope.CHARACTER,
            AchievementCriteriaType.TOTAL_WINS, "{\"threshold\":10}", 0, 0, 0, 0, 10);

        List<?> unlocked = evaluateCharacter(oneWin()); // only 1 win, threshold is 10

        assertEquals(0, unlocked.size());
        assertEquals(0, db.achievementUnlockCountOf(ACCOUNT));
        assertEquals(STARTING_GOLD, db.goldOf(ACCOUNT), "nothing paid");
    }
}
