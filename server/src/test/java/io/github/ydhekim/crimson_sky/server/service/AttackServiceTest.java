package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.BattleMode;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.network.packet.AttackResponse;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.combat.RewardOutcome;
import io.github.ydhekim.crimson_sky.server.quest.QuestPeriods;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeBattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story B4 / system design §7: opponent selection (Elo band → unbounded → bot), whole-battle
 * resolution in one call, and the protocol-level guarantee that a bot opponent is invisible to the
 * client. Headless, no DB, no socket.
 */
class AttackServiceTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ATTACKER = 1L;

    private FakeCharacterDao characterDao;
    private FakeBattleHistoryDao battleHistoryDao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        characterDao = new FakeCharacterDao()
            .with(CombatFixtures.character(ATTACKER, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000);
        battleHistoryDao = new FakeBattleHistoryDao();
    }

    /** A service with a seeded RNG so "pick randomly among candidates" is reproducible. */
    private AttackService service() {
        return service(new BotFactory(new Random(42L)));
    }

    private AttackService service(BotFactory botFactory) {
        return new AttackService(new CharacterService(characterDao), botFactory, battleHistoryDao, new Random(42L));
    }

    private AttackResult attack() {
        return attack(BattleMode.NORMAL);
    }

    private AttackResult attack(BattleMode mode) {
        Optional<AttackResult> result = service().attack(ATTACKER, mode);
        assertTrue(result.isPresent(), "an owned, loadable character always resolves a battle");
        return result.get();
    }

    @Test
    void picksAPersistedOpponentInsideTheEloBand() {
        characterDao.with(CombatFixtures.character(2L, 20L, "Boran"), 20L, 1050); // gap 50, inside ±100
        characterDao.with(CombatFixtures.character(3L, 30L, "Cem"), 30L, 1400);   // gap 400, outside

        AttackResult result = attack();

        assertFalse(result.opponentIsBot(), "a real opponent exists inside the band");
        assertEquals(2L, result.opponentCharacterId().longValue(), "the out-of-band character is never chosen");
        assertEquals("Boran", result.opponentDisplayName());
    }

    @Test
    void widensToAnUnboundedRangeWhenNobodyIsInsideTheBand() {
        characterDao.with(CombatFixtures.character(3L, 30L, "Cem"), 30L, 1400); // 400 Elo away

        AttackResult result = attack();

        assertFalse(result.opponentIsBot(), "widening finds the distant real opponent before any bot");
        assertEquals(3L, result.opponentCharacterId().longValue());
    }

    @Test
    void fallsBackToABotWhenNoPersistedOpponentExists() {
        // Only the attacker exists in the table.
        AttackResult result = attack();

        assertTrue(result.opponentIsBot());
        assertNull(result.opponentCharacterId(), "a bot has no row in `characters` (mirrors §8's nullable column)");
        assertNotNull(result.opponentDisplayName());
        assertFalse(result.opponentDisplayName().isBlank(), "a bot still needs a plausible display name");
    }

    @Test
    void neverSelectsTheRequesterAsItsOwnOpponent() {
        // The DAO hands back the requester, as a broken WHERE clause would; the service must re-exclude
        // it rather than fight itself.
        characterDao.leakingRequesterIntoCandidates();

        AttackResult result = attack();

        assertTrue(result.opponentIsBot(), "with only the requester in the table, a bot is the only option");
        assertNull(result.opponentCharacterId());
    }

    @Test
    void resolvesTheWholeBattleAndKeepsEveryTurn() {
        characterDao.with(CombatFixtures.character(2L, 20L, "Boran"), 20L, 1000);

        AttackResult result = attack();

        // Two 500 HP fixtures trading a guaranteed 150 damage per turn need four turns to settle, so
        // the log must hold more than the single final turn TurnResultComponent alone would leave.
        assertTrue(result.turns().size > 1,
            "the whole battle's turns are returned, not just the last one (got " + result.turns().size + ")");

        int totalDamage = 0;
        for (Array<ResolvedAction> turn : result.turns()) {
            assertFalse(turn.isEmpty(), "a recorded turn always has at least one Result Set entry");
            for (ResolvedAction action : turn) {
                totalDamage += action.damage();
            }
        }
        assertTrue(totalDamage > 0, "the log carries real applied damage, not decision-layer zeroes");
    }

    @Test
    void reportsWhetherTheAttackerWon() {
        // A defenceless 1 HP opponent: whoever strikes first ends it, and the attacker always strikes
        // for 150. The attacker can only lose if the opponent's own hit lands first, which it cannot —
        // the opponent has no weapon and punches for at most 5 against 500 HP.
        characterDao.with(CombatFixtures.frailCharacter(2L, 20L, "Boran"), 20L, 1000);

        AttackResult result = attack();

        assertTrue(result.won(), "the attacker one-shots a 1 HP opponent");
        assertTrue(result.toResponse(RewardOutcome.none()).won());
    }

    @Test
    void refusesToAttackWithACharacterThatCannotBeLoaded() {
        assertTrue(service().attack(999L, BattleMode.NORMAL).isEmpty(), "an unknown character resolves no battle");
    }

    @Test
    void allowsFiveBattlesADayWhenNoneHaveBeenFoughtYet() {
        assertEquals(5, service().remainingDailyBattles(ATTACKER),
            "the base cap is five battles per UTC day (system design §20)");
    }

    @Test
    void reachesZeroRemainingAfterFiveBattlesToday() {
        for (int i = 0; i < 5; i++) {
            battleHistoryDao.with(ATTACKER, false, Instant.now());
        }
        assertEquals(0, service().remainingDailyBattles(ATTACKER),
            "five battles today exhausts the base cap");
    }

    @Test
    void doesNotCountYesterdaysBattlesAgainstTodaysCap() {
        Instant beforeMidnight = QuestPeriods.startOfToday().minusSeconds(1);
        for (int i = 0; i < 5; i++) {
            battleHistoryDao.with(ATTACKER, false, beforeMidnight);
        }
        assertEquals(5, service().remainingDailyBattles(ATTACKER),
            "battles before this UTC day's midnight are outside the daily window");
    }

    @Test
    void rankedEligibilityIsGatedAtLevel25() {
        characterDao.with(CombatFixtures.characterAtLevel(2L, 20L, "Boran", 24, 0L), 20L, 1000);
        characterDao.with(CombatFixtures.characterAtLevel(3L, 30L, "Cem", 25, 0L), 30L, 1000);

        AttackService service = service();
        assertFalse(service.isRankedEligible(ATTACKER), "the level-1 fixture is below the gate (§21)");
        assertFalse(service.isRankedEligible(2L), "level 24 is still below the gate");
        assertTrue(service.isRankedEligible(3L), "level 25 is exactly eligible");
        assertFalse(service.isRankedEligible(999L), "an unloadable character is never eligible");
    }

    @Test
    void rankedMatchmakingExcludesSub25CandidatesAndFallsBackToABot() {
        // A level-1 candidate a NORMAL attack would happily match against (same Elo, real row) — ranked
        // must never see it, leaving only the bot fallback.
        characterDao.with(CombatFixtures.character(2L, 20L, "Boran"), 20L, 1000);
        characterDao.with(CombatFixtures.characterAtLevel(ATTACKER, ACCOUNT_A, "Ayla", 25, 0L), ACCOUNT_A, 1000);

        AttackResult result = attack(BattleMode.RANKED);

        assertTrue(result.opponentIsBot(), "the sub-25 candidate is invisible to ranked matchmaking (§21)");
        assertNull(result.opponentCharacterId());
        assertEquals(BattleMode.RANKED, result.mode(), "the result records which track this battle fought on");
    }

    @Test
    void rankedMatchmakingStillFindsALevel25PlusOpponent() {
        characterDao.with(CombatFixtures.characterAtLevel(ATTACKER, ACCOUNT_A, "Ayla", 25, 0L), ACCOUNT_A, 1000);
        characterDao.with(CombatFixtures.characterAtLevel(2L, 20L, "Boran", 30, 0L), 20L, 1000);

        AttackResult result = attack(BattleMode.RANKED);

        assertFalse(result.opponentIsBot(), "a level-25+ candidate is a real ranked opponent");
        assertEquals(2L, result.opponentCharacterId().longValue());
    }

    @Test
    void aRankedAttackKeysOffTheLiveRankedEloNotTheStoredColumn() {
        // The stored `characters.elo` fixture value is 1000; the ranked track diverges to 1120 via a
        // seeded past RANKED battle. With no eligible candidates the bot fallback receives whichever
        // number matchmaking keyed off — the one observable that distinguishes the two tracks headlessly.
        characterDao.with(CombatFixtures.characterAtLevel(ATTACKER, ACCOUNT_A, "Ayla", 25, 0L), ACCOUNT_A, 1000);
        battleHistoryDao.withRanked(ATTACKER, 120, Instant.now());
        RecordingBotFactory botFactory = new RecordingBotFactory();

        assertTrue(service(botFactory).attack(ATTACKER, BattleMode.RANKED).isPresent());
        assertEquals(1120, botFactory.lastElo, "ranked matchmaking reads 1000 + SUM(ranked_elo_delta) (§21)");

        assertTrue(service(botFactory).attack(ATTACKER, BattleMode.NORMAL).isPresent());
        assertEquals(1000, botFactory.lastElo, "a NORMAL attack still reads the stored `characters.elo`");
    }

    @Test
    void aNormalAttackRecordsTheNormalModeOnItsResult() {
        assertEquals(BattleMode.NORMAL, attack().mode(),
            "regression: the pre-§21 attack path is byte-for-byte a NORMAL battle");
    }

    /** Captures the Elo handed to the bot fallback — the seam that reveals which track matchmaking read. */
    private static final class RecordingBotFactory extends BotFactory {
        int lastElo = -1;

        RecordingBotFactory() {
            super(new Random(42L));
        }

        @Override
        public Character createBot(int elo) {
            lastElo = elo;
            return super.createBot(elo);
        }
    }

    @Test
    void attackResponseCannotRevealThatTheOpponentWasABot() {
        AttackResult botFight = attack();
        assertTrue(botFight.opponentIsBot(), "precondition: this fight was against a bot");

        AttackResponse response = botFight.toResponse(RewardOutcome.none());

        // The internal result knows; the packet's wire format has nowhere to put it. Assert on the
        // record's shape, so adding an opponent id or bot flag later fails this test loudly. The reward
        // deltas (story C1) and the progression fields (Epic L) are safe to carry: a bot fight's Elo
        // delta — and its stat/skill/level payout — are computed against the attacker's own rating
        // (§8.1/§15), indistinguishable from an evenly matched real fight. The bonus-reward field names
        // only a granted item, never the opponent.
        List<String> wireFields = Arrays.stream(AttackResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
        assertEquals(List.of("battleId", "opponentDisplayName", "won", "turns",
                "goldDelta", "expDelta", "eloDelta",
                "skillPointsGained", "levelsGained", "statPointsGained", "bonusRewardGranted"), wireFields,
            "AttackResponse must expose no opponent id and no bot flag (system design §7)");
        assertNotNull(response.opponentDisplayName());
    }
}
