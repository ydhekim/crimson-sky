package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.QuestClaimResult;
import io.github.ydhekim.crimson_sky.common.model.QuestProgress;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.dao.QuestClaimDao;
import io.github.ydhekim.crimson_sky.server.quest.QuestCatalog;
import io.github.ydhekim.crimson_sky.server.quest.QuestDefinition;
import io.github.ydhekim.crimson_sky.server.quest.QuestPeriodType;
import io.github.ydhekim.crimson_sky.server.quest.QuestPeriods;
import org.jdbi.v3.core.Jdbi;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The three v1.0 quests (system design §19, Epic P): live progress computed off {@code battle_history} and a
 * claim that pays a reward. Nothing here stores progress — "Win N battles this period" is a live win count,
 * never a counter that could drift — so the only state a claim writes is the {@code quest_claims} row that
 * stops a completed quest being claimed twice in one period.
 *
 * <p><b>Why the raw {@link Jdbi}, not {@code CharacterService}/{@code AccountService}:</b> identical reasoning
 * to {@code ShopService}/{@code RewardService}/{@code SkillTreeService}. A claim spans {@code quest_claims}
 * (the claim row) and <i>either</i> {@code accounts.global_currency} (the repeatable's gold) or
 * {@code characters.inventory} (a daily/weekly consumable) atomically, and {@code onDemand} DAO proxies take
 * a new connection per call — so only DAOs attached to one {@link Jdbi#useTransaction} handle commit or roll
 * back together.
 *
 * <p><b>Rewards, decided this pass</b> (real gaps §19 left open, resolved the same way L3's bonus table was):
 * <ul>
 *   <li><b>Daily</b> → one skill-restoration scroll ({@code ShopService.SKILL_RESTORATION_SCROLL} +1). This
 *       service lives in {@code server.service} specifically so it can reuse {@code ShopService}'s
 *       package-private consumable keys directly rather than duplicating the strings.</li>
 *   <li><b>Weekly</b> → the player's choice of one Repair Token or one Pet Care Kit
 *       ({@code ShopService.REPAIR_TOKEN}/{@code PET_CARE_KIT} +1). Deliberately only the two existing
 *       maintenance-economy consumables — no weapon/pet option, which would reach into constants calibrated
 *       for starter content / milestone bonuses, never meant to double as quest rewards.</li>
 *   <li><b>Repeatable</b> → a flat {@value #REPEATABLE_GOLD_REWARD} gold via
 *       {@code AccountDao.addGlobalCurrency}. §19 also names "a potion charge" as an alternative — not
 *       implemented, because O2 built the potion mechanic but no potion {@code Skill} instance has ever been
 *       authored, so there is nothing concrete to grant. Gold-only for this pass.</li>
 * </ul>
 *
 * <p>There is a small TOCTOU gap between the pre-transaction progress/claim-guard reads and the writes —
 * accepted deliberately, the same simplification {@code CharacterService.allocateStatPoints} and
 * {@code ShopService}'s consumable paths already take. For daily/weekly the {@code quest_claims} {@code UNIQUE}
 * constraint is the real backstop: a race loses at the {@code INSERT}, caught and reported as
 * {@code ERROR_UNKNOWN}. The repeatable cap's worst case is one extra claim slipping through on a genuine
 * simultaneous double-submit — not a realistic concern at this scale.
 */
public class QuestService {

    private static final Logger log = new Logger("QuestService", Logger.DEBUG);

    static final int REPEATABLE_GOLD_REWARD = 15;
    static final int REPEATABLE_DAILY_CLAIM_CAP = 3;

    private final Jdbi jdbi;
    private final CharacterService characterService;

    public QuestService(Jdbi jdbi, CharacterService characterService) {
        this.jdbi = jdbi;
        this.characterService = characterService;
    }

    /**
     * The live status of all three quests for {@code characterId}, current period (system design §19).
     * Ownership-guarded, failing closed to {@code ERROR_UNKNOWN}. Wins are counted from each quest's own
     * period boundary; the claim state is the period's own guard (an existing-claim lookup for daily/weekly,
     * the today's-count against the cap for the repeatable).
     */
    public ServiceResult<Array<QuestProgress>> getStatus(long accountId, long characterId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Quest status rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            Instant startOfToday = QuestPeriods.startOfToday();
            Instant startOfWeek = QuestPeriods.startOfWeek();

            Array<QuestProgress> quests = jdbi.withHandle(handle -> {
                BattleHistoryDao battleHistoryDao = handle.attach(BattleHistoryDao.class);
                QuestClaimDao questClaimDao = handle.attach(QuestClaimDao.class);

                Array<QuestProgress> progress = new Array<>();
                for (QuestDefinition quest : QuestDefinition.values()) {
                    Instant periodStart = periodStart(quest, startOfToday, startOfWeek);
                    int currentWins = battleHistoryDao.countWins(characterId, periodStart);
                    progress.add(statusOf(quest, characterId, currentWins, periodStart, startOfToday, questClaimDao));
                }
                return progress;
            });

            return ServiceResult.success(MessageCode.SUCCESS, quests);
        } catch (Exception e) {
            log.error("Quest status lookup failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * Claims a completed quest's reward for {@code characterId} on behalf of {@code accountId} (system
     * design §19). Validation order, fail-closed throughout: ownership ({@code ERROR_UNKNOWN}); the quest
     * exists ({@code QUEST_NOT_FOUND}); live progress meets the target ({@code QUEST_NOT_COMPLETE}); the
     * period's claim guard is not yet spent ({@code QUEST_ALREADY_CLAIMED} for daily/weekly,
     * {@code QUEST_DAILY_CLAIM_CAP_REACHED} for the repeatable); and — weekly only — {@code rewardChoice} is
     * exactly a Repair Token or a Pet Care Kit ({@code QUEST_INVALID_REWARD_CHOICE}). On success, inside one
     * transaction: insert the claim row, then apply the reward.
     */
    public ServiceResult<QuestClaimResult> claim(long accountId, long characterId, String questId, String rewardChoice) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Quest claim rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            QuestDefinition quest = QuestCatalog.find(questId);
            if (quest == null) {
                return ServiceResult.failure(MessageCode.QUEST_NOT_FOUND);
            }

            Instant startOfToday = QuestPeriods.startOfToday();
            Instant periodStart = periodStart(quest, startOfToday, QuestPeriods.startOfWeek());

            // Progress and the claim guard, read before the transaction for a clean rejection reason. The
            // authoritative backstop is the write itself: quest_claims' UNIQUE for daily/weekly, and the
            // deliberately-simple accepted race for the repeatable cap (see this class's docstring).
            ClaimGuard guard = jdbi.withHandle(handle -> {
                int wins = handle.attach(BattleHistoryDao.class).countWins(characterId, periodStart);
                QuestClaimDao claimDao = handle.attach(QuestClaimDao.class);
                boolean exhausted = quest.periodType == QuestPeriodType.REPEATABLE
                    ? claimDao.countClaimsSince(characterId, quest.id, startOfToday) >= REPEATABLE_DAILY_CLAIM_CAP
                    : claimDao.isClaimed(characterId, quest.id, periodStart);
                return new ClaimGuard(wins, exhausted);
            });

            if (guard.wins() < quest.targetWins) {
                return ServiceResult.failure(MessageCode.QUEST_NOT_COMPLETE);
            }
            if (guard.exhausted()) {
                return ServiceResult.failure(quest.periodType == QuestPeriodType.REPEATABLE
                    ? MessageCode.QUEST_DAILY_CLAIM_CAP_REACHED
                    : MessageCode.QUEST_ALREADY_CLAIMED);
            }
            if (quest.periodType == QuestPeriodType.WEEKLY && !isValidWeeklyChoice(rewardChoice)) {
                return ServiceResult.failure(MessageCode.QUEST_INVALID_REWARD_CHOICE);
            }

            long[] remainingGold = {0L};
            int[] scrolls = {0};
            int[] repairTokens = {0};
            int[] petCareKits = {0};

            jdbi.useTransaction(handle -> {
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                AccountDao accountDao = handle.attach(AccountDao.class);
                QuestClaimDao claimDao = handle.attach(QuestClaimDao.class);

                // Daily/weekly share the period boundary so a second claim collides on the UNIQUE triple;
                // the repeatable claim takes its own moment so its rows never collide (its cap is a count).
                Instant claimPeriod = quest.periodType == QuestPeriodType.REPEATABLE ? Instant.now() : periodStart;
                claimDao.insert(characterId, quest.id, claimPeriod);

                // Read the inventory under the row lock in every case: the daily/weekly reward mutates it,
                // and even the gold-only repeatable claim reads it to report the character's consumable
                // counts. Concurrent claims for one character serialize on this FOR UPDATE.
                Inventory locked = lockedInventory(characterDao, characterId);
                Map<String, Integer> consumables = consumablesOf(locked);
                if (quest.periodType == QuestPeriodType.REPEATABLE) {
                    accountDao.addGlobalCurrency(accountId, REPEATABLE_GOLD_REWARD);
                } else {
                    String key = quest.periodType == QuestPeriodType.DAILY
                        ? ShopService.SKILL_RESTORATION_SCROLL : rewardChoice;
                    consumables.put(key, consumables.getOrDefault(key, 0) + 1);
                    // The rest of the inventory (weapons, skills, pets, other consumables) rides through
                    // untouched — one in-memory transformation before the same updateInventory call (§18).
                    characterDao.updateInventory(characterId,
                        new Inventory(locked.weapons(), locked.skills(), locked.pets(), consumables));
                }

                remainingGold[0] = accountDao.getGlobalCurrency(accountId).orElse(0L);
                scrolls[0] = consumables.getOrDefault(ShopService.SKILL_RESTORATION_SCROLL, 0);
                repairTokens[0] = consumables.getOrDefault(ShopService.REPAIR_TOKEN, 0);
                petCareKits[0] = consumables.getOrDefault(ShopService.PET_CARE_KIT, 0);
            });

            log.info("Character " + characterId + " claimed quest " + quest.id + " ("
                + (quest.periodType == QuestPeriodType.REPEATABLE ? REPEATABLE_GOLD_REWARD + " gold"
                : quest.periodType == QuestPeriodType.DAILY ? "1 skill-restoration scroll"
                : "1 " + rewardChoice) + ").");
            return ServiceResult.success(MessageCode.SUCCESS,
                new QuestClaimResult(quest.id, remainingGold[0], scrolls[0], repairTokens[0], petCareKits[0]));
        } catch (Exception e) {
            log.error("Quest claim failed for character " + characterId + " / quest " + questId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /** The reads a claim guard needs, snapshotted together before the transaction opens. */
    private record ClaimGuard(int wins, boolean exhausted) {
    }

    private QuestProgress statusOf(QuestDefinition quest, long characterId, int currentWins, Instant periodStart,
                                   Instant startOfToday, QuestClaimDao questClaimDao) {
        boolean complete = currentWins >= quest.targetWins;
        if (quest.periodType == QuestPeriodType.REPEATABLE) {
            int claimsToday = questClaimDao.countClaimsSince(characterId, quest.id, startOfToday);
            int remaining = Math.max(0, REPEATABLE_DAILY_CLAIM_CAP - claimsToday);
            return new QuestProgress(quest.id, quest.description, currentWins, quest.targetWins,
                complete && remaining > 0, remaining == 0, remaining);
        }
        boolean claimed = questClaimDao.isClaimed(characterId, quest.id, periodStart);
        return new QuestProgress(quest.id, quest.description, currentWins, quest.targetWins,
            complete && !claimed, claimed, claimed ? 0 : 1);
    }

    /** DAILY/REPEATABLE measure from UTC midnight today; WEEKLY from the most recent Monday (§19). */
    private static Instant periodStart(QuestDefinition quest, Instant startOfToday, Instant startOfWeek) {
        return quest.periodType == QuestPeriodType.WEEKLY ? startOfWeek : startOfToday;
    }

    private static boolean isValidWeeklyChoice(String rewardChoice) {
        return ShopService.REPAIR_TOKEN.equals(rewardChoice) || ShopService.PET_CARE_KIT.equals(rewardChoice);
    }

    /** The row-locked inventory the transaction acts on, tolerating the legacy null-array shape (§18). */
    private static Inventory lockedInventory(CharacterDao characterDao, long characterId) {
        return characterDao.getInventoryForUpdate(characterId)
            .orElseGet(() -> new Inventory(new Array<>(), new Array<>(), new Array<>(), new HashMap<>()));
    }

    /** A mutable copy of the consumables map; never {@code null} (an inventory predating §18 has no key). */
    private static Map<String, Integer> consumablesOf(Inventory inventory) {
        return inventory.consumables() != null ? new HashMap<>(inventory.consumables()) : new HashMap<>();
    }
}
