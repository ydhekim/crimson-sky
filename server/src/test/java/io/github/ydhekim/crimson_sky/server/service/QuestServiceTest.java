package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.QuestClaimResult;
import io.github.ydhekim.crimson_sky.common.model.QuestProgress;
import io.github.ydhekim.crimson_sky.server.quest.QuestPeriods;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quest service (system design §19, Epic P). Like {@code ShopServiceTest}, the claim writes run against a
 * real (in-memory) database — "the claim row and the reward commit or roll back together" is a property only a
 * real transaction can show — while ownership reads come from {@link FakeCharacterDao}. Progress is seeded as
 * real {@code battle_history} rows so the live win count is exercised end to end, not stubbed.
 */
class QuestServiceTest {

    private static final long ACCOUNT = 10L;
    private static final long OTHER_ACCOUNT = 20L;
    private static final long CHARACTER = 1L;
    private static final int ELO = 1000;
    private static final long STARTING_GOLD = 100L;
    private static final String EMPTY_LOADOUT = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private static final String DAILY = "daily.win2";
    private static final String WEEKLY = "weekly.win10";
    private static final String REPEATABLE = "repeatable.win1";

    private TestDatabase db;
    private QuestService service;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
    }

    /** Seeds one character (in both views) with {@code consumables} in stock and {@code gold} in its wallet. */
    private void seed(long gold, Map<String, Integer> consumables) {
        Character character = CombatFixtures.character(CHARACTER, ACCOUNT, "Ayla");
        db = TestDatabase.create()
            .withAccount(ACCOUNT, gold)
            .withCharacter(CHARACTER, ACCOUNT, "Ayla", 0L, ELO, inventoryJson(consumables), EMPTY_LOADOUT);
        service = new QuestService(db.jdbi(),
            new CharacterService(new FakeCharacterDao().with(character, ACCOUNT, ELO)));
    }

    private void seed(long gold) {
        seed(gold, Map.of());
    }

    /** Records {@code n} won battles for the character, dated now — inside every quest period boundary. */
    private void seedWins(int n) {
        for (int i = 0; i < n; i++) {
            db.withBattleHistory(CHARACTER, true, Instant.now());
        }
    }

    private static String inventoryJson(Map<String, Integer> consumables) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : consumables.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        return "{\"weapons\":[],\"skills\":[],\"pets\":[],\"consumables\":" + json + "}";
    }

    private static QuestProgress progressFor(Array<QuestProgress> quests, String questId) {
        for (QuestProgress quest : quests) {
            if (quest.questId().equals(questId)) {
                return quest;
            }
        }
        throw new AssertionError("no quest with id " + questId + " in the status");
    }

    private Array<QuestProgress> status() {
        var result = service.getStatus(ACCOUNT, CHARACTER);
        assertTrue(result.success(), "status lookup should succeed for an owned character");
        return result.data();
    }

    // --- getStatus ---------------------------------------------------------------------------------

    @Test
    void reportsAllThreeQuests() {
        seed(STARTING_GOLD);
        Array<QuestProgress> quests = status();
        assertEquals(3, quests.size);
        assertEquals(2, progressFor(quests, DAILY).targetWins());
        assertEquals(10, progressFor(quests, WEEKLY).targetWins());
        assertEquals(1, progressFor(quests, REPEATABLE).targetWins());
    }

    @Test
    void anIncompleteDailyIsNotClaimable() {
        seed(STARTING_GOLD);
        seedWins(1); // 1 of 2

        QuestProgress daily = progressFor(status(), DAILY);
        assertEquals(1, daily.currentWins());
        assertFalse(daily.claimable(), "1 of 2 wins is not enough to claim");
        assertFalse(daily.alreadyClaimed());
    }

    @Test
    void aCompleteDailyIsClaimableUntilItIsClaimed() {
        seed(STARTING_GOLD);
        seedWins(2);

        QuestProgress before = progressFor(status(), DAILY);
        assertEquals(2, before.currentWins());
        assertTrue(before.claimable(), "2 of 2 wins is claimable");
        assertEquals(1, before.claimsRemainingToday());

        // Claiming the period spends its one allowance — even though the wins remain on the board.
        db.withQuestClaim(CHARACTER, DAILY, QuestPeriods.startOfToday());

        QuestProgress after = progressFor(status(), DAILY);
        assertEquals(2, after.currentWins(), "the wins are still there");
        assertTrue(after.alreadyClaimed());
        assertFalse(after.claimable(), "but the period is already claimed");
        assertEquals(0, after.claimsRemainingToday());
    }

    @Test
    void theRepeatablesRemainingClaimsFloorIndependentlyOfDailyAndWeekly() {
        seed(STARTING_GOLD);
        seedWins(2); // enough for daily too, so we can prove the two don't share state

        assertEquals(3, progressFor(status(), REPEATABLE).claimsRemainingToday(), "cap 3, none claimed yet");

        // Seed repeatable claims with distinct period_starts (its rows never collide, unlike daily/weekly).
        db.withQuestClaim(CHARACTER, REPEATABLE, Instant.now().minusSeconds(2));
        db.withQuestClaim(CHARACTER, REPEATABLE, Instant.now().minusSeconds(1));

        Array<QuestProgress> mid = status();
        assertEquals(1, progressFor(mid, REPEATABLE).claimsRemainingToday(), "3 − 2 seeded");
        assertTrue(progressFor(mid, REPEATABLE).claimable(), "1 win, 1 claim left → still claimable");
        // The daily quest is untouched by the repeatable's claims — no shared state.
        assertTrue(progressFor(mid, DAILY).claimable(), "the daily is still independently claimable");
        assertEquals(1, progressFor(mid, DAILY).claimsRemainingToday());

        db.withQuestClaim(CHARACTER, REPEATABLE, Instant.now());
        QuestProgress exhausted = progressFor(status(), REPEATABLE);
        assertEquals(0, exhausted.claimsRemainingToday(), "floored at 0, never negative");
        assertTrue(exhausted.alreadyClaimed());
        assertFalse(exhausted.claimable());
    }

    // --- claim: rejections -------------------------------------------------------------------------

    @Test
    void claimingAnUnownedCharacterIsRejectedAndTouchesNothing() {
        seed(STARTING_GOLD);
        seedWins(2);

        var result = service.claim(OTHER_ACCOUNT, CHARACTER, DAILY, null);

        assertFalse(result.success());
        assertEquals(MessageCode.ERROR_UNKNOWN, result.code());
        assertEquals(STARTING_GOLD, db.goldOf(ACCOUNT), "no reward was applied");
        assertEquals(0, db.questClaimCountOf(CHARACTER, DAILY), "no claim row was written");
    }

    @Test
    void claimingAnUnknownQuestIsRejected() {
        seed(STARTING_GOLD);

        var result = service.claim(ACCOUNT, CHARACTER, "no.such.quest", null);

        assertFalse(result.success());
        assertEquals(MessageCode.QUEST_NOT_FOUND, result.code());
    }

    @Test
    void claimingAnIncompleteQuestIsRejected() {
        seed(STARTING_GOLD);
        seedWins(1); // daily needs 2

        var result = service.claim(ACCOUNT, CHARACTER, DAILY, null);

        assertFalse(result.success());
        assertEquals(MessageCode.QUEST_NOT_COMPLETE, result.code());
        assertEquals(0, db.questClaimCountOf(CHARACTER, DAILY));
    }

    @Test
    void aSecondClaimOfTheSameDailyPeriodIsRejected() {
        seed(STARTING_GOLD);
        seedWins(2);

        assertTrue(service.claim(ACCOUNT, CHARACTER, DAILY, null).success(), "first claim succeeds");

        var second = service.claim(ACCOUNT, CHARACTER, DAILY, null);
        assertFalse(second.success());
        assertEquals(MessageCode.QUEST_ALREADY_CLAIMED, second.code());
        assertEquals(1, db.questClaimCountOf(CHARACTER, DAILY), "still exactly one claim");
    }

    @Test
    void aFourthRepeatableClaimInOneDayIsRejectedButTheThirdSucceeds() {
        seed(STARTING_GOLD);
        seedWins(1); // repeatable needs 1

        assertTrue(service.claim(ACCOUNT, CHARACTER, REPEATABLE, null).success(), "claim 1");
        assertTrue(service.claim(ACCOUNT, CHARACTER, REPEATABLE, null).success(), "claim 2");
        assertTrue(service.claim(ACCOUNT, CHARACTER, REPEATABLE, null).success(), "claim 3 still under the cap");

        var fourth = service.claim(ACCOUNT, CHARACTER, REPEATABLE, null);
        assertFalse(fourth.success());
        assertEquals(MessageCode.QUEST_DAILY_CLAIM_CAP_REACHED, fourth.code());
        assertEquals(3, db.questClaimCountOf(CHARACTER, REPEATABLE), "the 4th never wrote a row");
        assertEquals(STARTING_GOLD + 3 * QuestService.REPEATABLE_GOLD_REWARD, db.goldOf(ACCOUNT),
            "exactly three payouts landed");
    }

    @Test
    void anInvalidWeeklyRewardChoiceIsRejected() {
        seed(STARTING_GOLD);
        seedWins(10);

        assertEquals(MessageCode.QUEST_INVALID_REWARD_CHOICE,
            service.claim(ACCOUNT, CHARACTER, WEEKLY, "a_free_weapon").code());
        assertEquals(MessageCode.QUEST_INVALID_REWARD_CHOICE,
            service.claim(ACCOUNT, CHARACTER, WEEKLY, null).code(), "no choice is not a valid choice");
        assertEquals(0, db.questClaimCountOf(CHARACTER, WEEKLY), "a rejected weekly claim writes nothing");
    }

    // --- claim: success, one per quest -------------------------------------------------------------

    @Test
    void claimingTheDailyGrantsOneScrollAndRecordsTheClaim() {
        seed(STARTING_GOLD);
        seedWins(2);

        var result = service.claim(ACCOUNT, CHARACTER, DAILY, null);

        assertTrue(result.success());
        QuestClaimResult data = result.data();
        assertEquals(1, data.scrollCount(), "one skill-restoration scroll granted");
        assertEquals(STARTING_GOLD, data.remainingGold(), "the daily costs no gold and pays none");
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"skill_restoration_scroll\":1"),
            "the scroll lands in the persisted inventory");
        assertEquals(1, db.questClaimCountOf(CHARACTER, DAILY));
    }

    @Test
    void claimingTheWeeklyGrantsThePlayersChosenToken() {
        seed(STARTING_GOLD);
        seedWins(10);

        var result = service.claim(ACCOUNT, CHARACTER, WEEKLY, ShopService.REPAIR_TOKEN);

        assertTrue(result.success());
        assertEquals(1, result.data().repairTokenCount(), "the chosen Repair Token was granted");
        assertEquals(0, result.data().petCareKitCount(), "and not the other option");
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"repair_token\":1"));
        assertEquals(1, db.questClaimCountOf(CHARACTER, WEEKLY));
    }

    @Test
    void claimingTheWeeklyCanGrantAPetCareKitInstead() {
        seed(STARTING_GOLD);
        seedWins(10);

        var result = service.claim(ACCOUNT, CHARACTER, WEEKLY, ShopService.PET_CARE_KIT);

        assertTrue(result.success());
        assertEquals(1, result.data().petCareKitCount());
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"pet_care_kit\":1"));
    }

    @Test
    void claimingTheRepeatablePaysFlatGold() {
        seed(STARTING_GOLD);
        seedWins(1);

        var result = service.claim(ACCOUNT, CHARACTER, REPEATABLE, null);

        assertTrue(result.success());
        assertEquals(STARTING_GOLD + QuestService.REPEATABLE_GOLD_REWARD, result.data().remainingGold());
        assertEquals(STARTING_GOLD + QuestService.REPEATABLE_GOLD_REWARD, db.goldOf(ACCOUNT),
            "15 gold landed on the account");
        assertEquals(1, db.questClaimCountOf(CHARACTER, REPEATABLE));
    }

    @Test
    void twoSameDayRepeatableClaimsGetDistinctPeriodStartsUnderTheOneQuestId() {
        seed(STARTING_GOLD);
        seedWins(1);

        assertTrue(service.claim(ACCOUNT, CHARACTER, REPEATABLE, null).success());
        assertTrue(service.claim(ACCOUNT, CHARACTER, REPEATABLE, null).success());

        assertEquals(2, db.questClaimCountOf(CHARACTER, REPEATABLE), "two claim rows, same quest id");
        assertEquals(2, db.distinctQuestClaimPeriodCountOf(CHARACTER, REPEATABLE),
            "each repeatable claim took its own period_start, so the two never collide on the UNIQUE triple");
    }

    @Test
    void aDailyClaimLeavesExistingConsumablesAlone() {
        // The blob is read-modify-written whole (§18): a claim must touch only its own key.
        Map<String, Integer> stock = new HashMap<>();
        stock.put(ShopService.REPAIR_TOKEN, 4);
        seed(STARTING_GOLD, stock);
        seedWins(2);

        assertTrue(service.claim(ACCOUNT, CHARACTER, DAILY, null).success());

        String inventory = db.inventoryJsonOf(CHARACTER);
        assertTrue(inventory.contains("\"skill_restoration_scroll\":1"), "the scroll was added");
        assertTrue(inventory.contains("\"repair_token\":4"), "the pre-existing tokens are untouched");
    }
}
