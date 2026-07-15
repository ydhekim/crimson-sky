package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.RewardOutcome;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story C1 / system design §8.1: the reward formulas, the atomicity of the three writes they trigger,
 * and the shape of the {@code battle_history} row they leave behind. Runs against a real (in-memory)
 * database, because "these three writes commit or roll back together" is not a property a fake DAO can
 * demonstrate.
 *
 * <p>The character *reads* still come from {@link FakeCharacterDao} — {@code RewardService} only ever
 * reads through {@code CharacterService}, and its Elo/account lookups are not what these tests are
 * about. {@link #seedCharacter} keeps the two views of a character in step.
 */
class RewardServiceTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long ATTACKER = 1L;
    private static final long OPPONENT = 2L;

    /** A character with no rewards yet: the deltas applied are exactly what these columns end up holding. */
    private static final long STARTING_GOLD = 100L;
    private static final long STARTING_EXP = 0L;

    private static final String INVENTORY_JSON = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";
    private static final String LOADOUT_JSON = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private FakeCharacterDao characterDao;
    private RewardService rewardService;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create().withAccount(ACCOUNT_A, STARTING_GOLD).withAccount(ACCOUNT_B, 0L);
        characterDao = new FakeCharacterDao();
        rewardService = new RewardService(db.jdbi(), new CharacterService(characterDao));
    }

    /** Registers a character in both views: the DAO the service reads from, and the DB it writes to. */
    private void seedCharacter(long characterId, long accountId, String name, int elo) {
        characterDao.with(CombatFixtures.character(characterId, accountId, name), accountId, elo);
        db.withCharacter(characterId, accountId, name, STARTING_EXP, elo, INVENTORY_JSON, LOADOUT_JSON);
    }

    /** A finished battle against a real, persisted opponent. */
    private AttackResult realFight(boolean won) {
        return new AttackResult(77L, ATTACKER, OPPONENT, "Boran", false, won, new Array<Array<ResolvedAction>>());
    }

    /** A finished battle against a synthesized bot — no row in `characters`, so no opponent id. */
    private AttackResult botFight(boolean won) {
        return new AttackResult(78L, ATTACKER, null, "Wanderer", true, won, new Array<Array<ResolvedAction>>());
    }

    @Test
    void winAgainstAnEvenlyRatedOpponentPaysTheFlatBase() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1000);
        seedCharacter(OPPONENT, ACCOUNT_B, "Boran", 1000);

        RewardOutcome outcome = rewardService.applyRewards(realFight(true));

        // expectedScore 0.5 → eloDelta = round(32 * 0.5) = 16; no Elo gap, so no gold/exp bonus. The 50
        // exp still carries a level-1 character across the 24-exp level-2 threshold (§15's corrected
        // curve), so this win is also a single level-up: +3 stat points, +3 skill points (a win).
        assertEquals(new RewardOutcome(25, 50L, 16, 3, 1, 3, null), outcome);
        assertEquals(STARTING_GOLD + 25, db.goldOf(ACCOUNT_A), "gold lands on the account, not the character");
        assertEquals(50L, db.experienceOf(ATTACKER));
        assertEquals(1016, db.eloOf(ATTACKER));
        assertEquals(2, db.levelOf(ATTACKER), "50 exp crosses the level-2 threshold of 24");
        assertEquals(3, db.unspentStatPointsOf(ATTACKER), "3 stat points per level gained");
        assertEquals(3, db.skillPointsOf(ATTACKER), "a win pays 3 skill points");
    }

    @Test
    void winAgainstAHigherRatedOpponentPaysMoreOfEverything() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1000);
        seedCharacter(OPPONENT, ACCOUNT_B, "Boran", 1200); // 200 Elo above the attacker

        RewardOutcome outcome = rewardService.applyRewards(realFight(true));

        // expectedScore ≈ 0.24 → eloDelta = round(32 * 0.76) = 24; gold/exp take the Elo-gap bonus. The
        // 90-exp payout crosses TWO thresholds in one battle (24 → level 2, 64 → level 3), exercising the
        // multi-level loop: levelsGained 2, 6 stat points. Neither level 2 nor 3 is a milestone, so no bonus.
        assertEquals(new RewardOutcome(45, 90L, 24, 3, 2, 6, null), outcome);
        assertTrue(outcome.eloDelta() > 16, "an upset moves the rating further than an even win");
        assertTrue(outcome.goldDelta() > 25 && outcome.expDelta() > 50L,
            "beating a higher-rated opponent pays a bonus on top of the base");
        assertEquals(1024, db.eloOf(ATTACKER));
        assertEquals(3, db.levelOf(ATTACKER), "90 exp crosses both the level-2 (24) and level-3 (64) thresholds");
        assertEquals(6, db.unspentStatPointsOf(ATTACKER), "two levels gained → 6 stat points");
    }

    @Test
    void winAgainstALowerRatedOpponentNeverPaysLessThanTheBase() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1200);
        seedCharacter(OPPONENT, ACCOUNT_B, "Boran", 1000); // 200 Elo below: a negative gap

        RewardOutcome outcome = rewardService.applyRewards(realFight(true));

        // The bonus term is floored at zero — beating someone weaker pays the base, never a penalty.
        assertEquals(25, outcome.goldDelta());
        assertEquals(50L, outcome.expDelta());
        assertEquals(8, outcome.eloDelta(), "an expected win moves the rating only a little");
    }

    @Test
    void lossPaysTheFlatConsolationAndCostsRating() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1000);
        seedCharacter(OPPONENT, ACCOUNT_B, "Boran", 1000);

        RewardOutcome outcome = rewardService.applyRewards(realFight(false));

        // 10 exp stays under the 24-exp level-2 threshold, so no level-up; a loss still pays 1 skill point.
        assertEquals(new RewardOutcome(5, 10L, -16, 1, 0, 0, null), outcome);
        assertEquals(STARTING_GOLD + 5, db.goldOf(ACCOUNT_A), "a losing streak still pays something");
        assertEquals(10L, db.experienceOf(ATTACKER));
        assertEquals(984, db.eloOf(ATTACKER), "the standard Elo formula produces the loss itself");
        assertEquals(1, db.levelOf(ATTACKER), "10 exp does not reach the level-2 threshold");
        assertEquals(0, db.unspentStatPointsOf(ATTACKER), "no level gained, no stat points");
        assertEquals(1, db.skillPointsOf(ATTACKER), "a loss still pays 1 skill point");
    }

    @Test
    void aBotIsRatedAsThoughItWereTheAttackersEqual() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1400);

        RewardOutcome win = rewardService.applyRewards(botFight(true));

        // Bot Elo == the attacker's own (system design §8.1) → expectedScore 0.5, exactly what an evenly
        // matched real fight pays. Nothing about the payout betrays that the opponent was synthesized —
        // including the identical single level-up (50 exp over the 24 threshold) and skill-point payout.
        assertEquals(new RewardOutcome(25, 50L, 16, 3, 1, 3, null), win);
        assertEquals(1416, db.eloOf(ATTACKER));
    }

    @Test
    void aLostBotFightCostsTheSameRatingAnEvenRealLossWould() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1400);

        assertEquals(new RewardOutcome(5, 10L, -16, 1, 0, 0, null), rewardService.applyRewards(botFight(false)));
    }

    @Test
    void recordsARealOpponentInBattleHistory() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1000);
        seedCharacter(OPPONENT, ACCOUNT_B, "Boran", 1000);

        rewardService.applyRewards(realFight(true));

        TestDatabase.BattleHistoryRow row = db.onlyBattleHistoryRow();
        assertEquals(ATTACKER, row.characterId());
        assertEquals(OPPONENT, row.opponentCharacterId().longValue());
        assertFalse(row.opponentIsBot());
        assertEquals(25, row.goldDelta());
        assertEquals(50L, row.experienceDelta());
        assertEquals(16, row.eloDelta());
    }

    @Test
    void recordsABotOpponentWithNoCharacterId() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1000);

        rewardService.applyRewards(botFight(true));

        TestDatabase.BattleHistoryRow row = db.onlyBattleHistoryRow();
        assertNull(row.opponentCharacterId(), "a bot has no row in `characters` to point at");
        assertTrue(row.opponentIsBot(), "bot-ness is recorded server-side, for analytics only");
    }

    @Test
    void neverRewardsTheOpponentsSideOfTheFight() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1000);
        seedCharacter(OPPONENT, ACCOUNT_B, "Boran", 1000);

        rewardService.applyRewards(realFight(true));

        // An async attack is one-sided: the defender never queued for this and is never touched by it.
        assertEquals(0L, db.goldOf(ACCOUNT_B));
        assertEquals(STARTING_EXP, db.experienceOf(OPPONENT));
        assertEquals(1000, db.eloOf(OPPONENT));
        assertEquals(1, db.battleHistoryRowCount(), "one row, for the attacker's side only");
    }

    @Test
    void aFailedRewardCommitsNothingAtAllAndStillLetsTheBattleStand() {
        seedCharacter(ATTACKER, ACCOUNT_A, "Ayla", 1000);
        // The opponent exists to CharacterService (so the Elo lookup succeeds and rewards are computed)
        // but has no row in `characters`, so the history insert's foreign key fails — a failure landing
        // *after* the character and account updates have already been issued inside the transaction.
        characterDao.with(CombatFixtures.character(OPPONENT, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);

        RewardOutcome outcome = rewardService.applyRewards(realFight(true));

        assertEquals(RewardOutcome.none(), outcome, "a battle whose reward failed pays nothing");
        assertEquals(STARTING_GOLD, db.goldOf(ACCOUNT_A), "the account update rolled back with the insert");
        assertEquals(STARTING_EXP, db.experienceOf(ATTACKER), "the character update rolled back too");
        assertEquals(1000, db.eloOf(ATTACKER));
        assertEquals(0, db.battleHistoryRowCount());
    }
}
