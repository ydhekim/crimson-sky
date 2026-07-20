package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.LadderClaimResult;
import io.github.ydhekim.crimson_sky.common.model.LadderStatus;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.server.quest.QuestPeriods;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ladder service (system design §21, Epic R3). Like {@code QuestServiceTest}, the claim writes run
 * against a real (in-memory) database — "the claim row and the reward commit or roll back together" is a
 * property only a real transaction can show — while ownership/level reads come from {@link FakeCharacterDao}.
 * Rank is computed from real {@code battle_history}/{@code characters} rows, exactly as production does.
 */
class LadderServiceTest {

    private static final long ACCOUNT = 10L;
    private static final long OTHER_ACCOUNT = 20L;
    private static final long CHARACTER = 1L;
    private static final int ELO = 1000;
    private static final long STARTING_GOLD = 100L;
    private static final String EMPTY_INVENTORY = "{\"weapons\":[],\"skills\":[],\"pets\":[],\"consumables\":{}}";
    private static final String EMPTY_LOADOUT = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private LadderService service;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
    }

    /**
     * Seeds one character (in both views) at {@code level}, holding {@link #STARTING_GOLD}. The DB row is
     * what the live rank query reads; the fake is what the ownership and level-gate checks read.
     */
    private void seed(int level) {
        Character character = CombatFixtures.characterAtLevel(CHARACTER, ACCOUNT, "Ayla", level, 0L);
        db = TestDatabase.create()
            .withAccount(ACCOUNT, STARTING_GOLD)
            .withCharacter(CHARACTER, ACCOUNT, "Ayla", level, 0L, ELO, EMPTY_INVENTORY, EMPTY_LOADOUT);
        service = new LadderService(db.jdbi(),
            new CharacterService(new FakeCharacterDao().with(character, ACCOUNT, ELO)));
    }

    private LadderStatus status() {
        var result = service.getStatus(ACCOUNT, CHARACTER);
        assertTrue(result.success(), "status lookup should succeed for an owned character");
        return result.data();
    }

    // --- getStatus ---------------------------------------------------------------------------------

    @Test
    void aFreshLevel25CharacterIsRankOneWithAClaimableTop1() {
        seed(25);

        LadderStatus status = status();
        assertTrue(status.rankedEligible());
        assertEquals(1000, status.currentRankedElo(), "no ranked history reads as the 1000 baseline");
        assertEquals(1, status.currentRank(), "alone on the ladder");
        assertEquals(1, status.lastMonthRank());
        assertEquals("TOP_1", status.rewardTier());
        assertTrue(status.claimable());
        assertFalse(status.alreadyClaimed());
    }

    @Test
    void aSubTwentyFiveCharacterIsNotRankedEligible() {
        seed(24);

        LadderStatus status = status();
        assertFalse(status.rankedEligible());
        assertEquals(0, status.currentRank(), "no rank is computed for an ineligible character");
        assertEquals(0, status.lastMonthRank());
        assertNull(status.rewardTier());
        assertFalse(status.claimable());
        assertFalse(status.alreadyClaimed());
    }

    @Test
    void anAlreadyClaimedMonthReportsAsClaimedAndNotClaimable() {
        seed(25);
        db.withLadderClaim(CHARACTER, QuestPeriods.startOfPreviousMonth());

        LadderStatus status = status();
        assertEquals("TOP_1", status.rewardTier(), "the tier is still shown");
        assertTrue(status.alreadyClaimed());
        assertFalse(status.claimable(), "but last month's reward is already spent");
    }

    // --- claim -------------------------------------------------------------------------------------

    @Test
    void claimingAnUnownedCharacterIsRejectedAndTouchesNothing() {
        seed(25);

        var result = service.claim(OTHER_ACCOUNT, CHARACTER);

        assertFalse(result.success());
        assertEquals(MessageCode.ERROR_UNKNOWN, result.code());
        assertEquals(STARTING_GOLD, db.goldOf(ACCOUNT), "no reward was applied");
        assertEquals(0, db.ladderClaimCountOf(CHARACTER), "no claim row was written");
    }

    @Test
    void aSubTwentyFiveCharacterCannotClaim() {
        seed(24);

        var result = service.claim(ACCOUNT, CHARACTER);

        assertFalse(result.success());
        assertEquals(MessageCode.LADDER_NOT_RANKED_ELIGIBLE, result.code());
        assertEquals(0, db.ladderClaimCountOf(CHARACTER));
    }

    @Test
    void claimingATop1StandingPaysGoldAndGrantsTheWarhammer() {
        seed(25);

        var result = service.claim(ACCOUNT, CHARACTER);

        assertTrue(result.success());
        LadderClaimResult data = result.data();
        assertEquals("TOP_1", data.rewardTier());
        assertEquals("Warhammer", data.itemGranted(), "top 1 grants the curated rare weapon");
        assertEquals(STARTING_GOLD + LadderService.TOP_1_GOLD_REWARD, data.remainingGold());
        assertEquals(STARTING_GOLD + LadderService.TOP_1_GOLD_REWARD, db.goldOf(ACCOUNT),
            "500 gold landed on the account");
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("Warhammer"),
            "the Warhammer lands in the persisted inventory");
        assertEquals(1, db.ladderClaimCountOf(CHARACTER), "exactly one claim row for the month");
    }

    @Test
    void aSecondClaimForTheSameMonthIsRejected() {
        seed(25);

        assertTrue(service.claim(ACCOUNT, CHARACTER).success(), "first claim succeeds");

        var second = service.claim(ACCOUNT, CHARACTER);
        assertFalse(second.success());
        assertEquals(MessageCode.LADDER_ALREADY_CLAIMED, second.code());
        assertEquals(1, db.ladderClaimCountOf(CHARACTER), "still exactly one claim row");
        assertEquals(STARTING_GOLD + LadderService.TOP_1_GOLD_REWARD, db.goldOf(ACCOUNT),
            "the second claim paid nothing");
    }

    @Test
    void aRankBelowOneHundredEarnsNoReward() {
        seed(25);
        // 100 higher-ranked level-25 characters push CHARACTER (baseline 1000) to rank 101. Their qualifying
        // battles are dated far in the past so they land before any run's last-month boundary.
        Instant longAgo = Instant.parse("2020-01-01T00:00:00Z");
        for (int i = 0; i < 100; i++) {
            long rivalId = 100 + i;
            db.withCharacter(rivalId, ACCOUNT, "Rival" + i, 25, 0L, ELO, EMPTY_INVENTORY, EMPTY_LOADOUT)
                .withRankedBattleHistory(rivalId, 100, longAgo); // 1100 > our 1000
        }

        var result = service.claim(ACCOUNT, CHARACTER);

        assertFalse(result.success());
        assertEquals(MessageCode.LADDER_NO_REWARD_THIS_RANK, result.code());
        assertEquals(0, db.ladderClaimCountOf(CHARACTER), "a sub-100 rank claims nothing");
        assertEquals(STARTING_GOLD, db.goldOf(ACCOUNT), "and pays nothing");
    }
}
