package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.LadderClaimResult;
import io.github.ydhekim.crimson_sky.common.model.LadderStatus;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.dao.LadderClaimDao;
import io.github.ydhekim.crimson_sky.server.ladder.LadderRewardTier;
import io.github.ydhekim.crimson_sky.server.quest.QuestPeriods;
import org.jdbi.v3.core.Jdbi;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The monthly ladder's live status and once-per-month claim (system design §21, Epic R3) — the same
 * shape as {@code QuestService}: nothing here stores standing, it's always recomputed off
 * {@code battle_history}, so the only state a claim writes is {@code ladder_claims}' one row per period.
 *
 * <p><b>Why the raw {@link Jdbi}, not {@code CharacterService}/{@code AccountService}:</b> identical
 * reasoning to {@code QuestService}. A claim spans {@code ladder_claims} and <i>either</i>
 * {@code accounts.global_currency} or {@code characters.inventory} atomically, and {@code onDemand} DAO
 * proxies take a new connection per call — so only DAOs attached to one {@link Jdbi#useTransaction} handle
 * commit or roll back together.
 *
 * <p><b>A claim always targets the most recently completed month, never the in-progress one</b> (system
 * design §21): standing can still move mid-month, so nothing is claimable until the month it describes has
 * actually ended. "As of end of last month" is {@code startOfMonth().minusNanos(1)} — one nanosecond before
 * this month began — fed into {@code BattleHistoryDao.getRankedEloAsOf}'s existing {@code <=} bound.
 *
 * <p>Same accepted TOCTOU gap {@code QuestService} documents: the pre-transaction rank/claim-guard read
 * can race a concurrent claim, but {@code ladder_claims}' UNIQUE constraint is the real backstop — a
 * race loses at the INSERT, caught and reported as {@code ERROR_UNKNOWN}.
 */
public class LadderService {

    private static final Logger log = new Logger("LadderService", Logger.DEBUG);

    static final int RANKED_LEVEL_REQUIREMENT = 25;
    static final int TOP_1_GOLD_REWARD = 500;
    static final int TOP_2_10_GOLD_REWARD = 100;
    static final int TOP_11_100_GOLD_REWARD = 30;

    /** Duplicated from RewardService's own milestone-bonus constant, same id/stats, deliberately not
     *  reached into (it's private there) — the identical "curated content, no new item invented ahead of
     *  its own epic" precedent RewardService's own javadoc already establishes for BONUS_WEAPONS. */
    private static final Weapon TOP_1_WEAPON_REWARD =
        new Weapon(3L, "Warhammer", "", Rarity.RARE, 40f, 15, 45, 25, 20, 20);

    private final Jdbi jdbi;
    private final CharacterService characterService;

    public LadderService(Jdbi jdbi, CharacterService characterService) {
        this.jdbi = jdbi;
        this.characterService = characterService;
    }

    /**
     * A character's live ladder standing plus last completed month's claimable tier (system design §21).
     * Ownership-guarded, failing closed to {@code ERROR_UNKNOWN}. A sub-25 character is reported
     * {@code rankedEligible=false} with everything else zeroed — no rank computation, no DB hit for it.
     */
    public ServiceResult<LadderStatus> getStatus(long accountId, long characterId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Ladder status rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            ServiceResult<Character> character = characterService.getCharacter(characterId);
            if (!character.success()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
            if (character.data().level() < RANKED_LEVEL_REQUIREMENT) {
                return ServiceResult.success(MessageCode.SUCCESS,
                    new LadderStatus(false, 1000, 0, 0, null, false, false));
            }

            LadderStatus status = jdbi.withHandle(handle -> {
                BattleHistoryDao battleHistoryDao = handle.attach(BattleHistoryDao.class);
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                LadderClaimDao claimDao = handle.attach(LadderClaimDao.class);

                int currentElo = battleHistoryDao.getRankedElo(characterId);
                int currentRank = characterDao.countRankedCharactersAboveEloAsOf(
                    characterId, currentElo, Instant.now()) + 1;

                Instant lastMonthEnd = QuestPeriods.startOfMonth().minusNanos(1);
                Instant lastMonthStart = QuestPeriods.startOfPreviousMonth();
                int lastMonthElo = battleHistoryDao.getRankedEloAsOf(characterId, lastMonthEnd);
                int lastMonthRank = characterDao.countRankedCharactersAboveEloAsOf(
                    characterId, lastMonthElo, lastMonthEnd) + 1;

                LadderRewardTier tier = LadderRewardTier.forRank(lastMonthRank);
                boolean claimed = claimDao.isClaimed(characterId, lastMonthStart);
                boolean claimable = tier != null && !claimed;

                return new LadderStatus(true, currentElo, currentRank, lastMonthRank,
                    tier != null ? tier.name() : null, claimable, claimed);
            });

            return ServiceResult.success(MessageCode.SUCCESS, status);
        } catch (Exception e) {
            log.error("Ladder status lookup failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * Claims the previous month's ladder reward for {@code characterId} on behalf of {@code accountId}
     * (system design §21). Validation order, fail-closed throughout: ownership ({@code ERROR_UNKNOWN});
     * the character exists ({@code ERROR_UNKNOWN}); it is ranked-eligible ({@code LADDER_NOT_RANKED_ELIGIBLE});
     * last month's rank falls in a rewarded tier ({@code LADDER_NO_REWARD_THIS_RANK}); and the period is not
     * already claimed ({@code LADDER_ALREADY_CLAIMED}). On success, inside one transaction: insert the claim
     * row, then apply the tier's reward.
     */
    public ServiceResult<LadderClaimResult> claim(long accountId, long characterId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Ladder claim rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            ServiceResult<Character> character = characterService.getCharacter(characterId);
            if (!character.success()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
            if (character.data().level() < RANKED_LEVEL_REQUIREMENT) {
                return ServiceResult.failure(MessageCode.LADDER_NOT_RANKED_ELIGIBLE);
            }

            Instant lastMonthEnd = QuestPeriods.startOfMonth().minusNanos(1);
            Instant lastMonthStart = QuestPeriods.startOfPreviousMonth();

            // Rank and the claim guard, read before the transaction for a clean rejection reason. The
            // authoritative backstop is the write itself: ladder_claims' UNIQUE (see this class's docstring).
            ClaimGuard guard = jdbi.withHandle(handle -> {
                int lastMonthElo = handle.attach(BattleHistoryDao.class).getRankedEloAsOf(characterId, lastMonthEnd);
                int rank = handle.attach(CharacterDao.class)
                    .countRankedCharactersAboveEloAsOf(characterId, lastMonthElo, lastMonthEnd) + 1;
                boolean claimed = handle.attach(LadderClaimDao.class).isClaimed(characterId, lastMonthStart);
                return new ClaimGuard(rank, claimed);
            });

            LadderRewardTier tier = LadderRewardTier.forRank(guard.rank());
            if (tier == null) {
                return ServiceResult.failure(MessageCode.LADDER_NO_REWARD_THIS_RANK);
            }
            if (guard.claimed()) {
                return ServiceResult.failure(MessageCode.LADDER_ALREADY_CLAIMED);
            }

            long[] remainingGold = {0L};
            int[] repairTokens = {0};
            int[] petCareKits = {0};
            String[] itemGranted = {null};

            jdbi.useTransaction(handle -> {
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                AccountDao accountDao = handle.attach(AccountDao.class);
                handle.attach(LadderClaimDao.class).insert(characterId, lastMonthStart);

                // Read the inventory under the row lock in every case: TOP_1/TOP_2_10 mutate it, and even
                // the gold-only TOP_11_100 claim reads it to report the character's consumable counts.
                Inventory locked = lockedInventory(characterDao, characterId);
                Map<String, Integer> consumables = consumablesOf(locked);
                Array<Weapon> weapons = locked.weapons() != null ? locked.weapons() : new Array<>();

                switch (tier) {
                    case TOP_1 -> {
                        accountDao.addGlobalCurrency(accountId, TOP_1_GOLD_REWARD);
                        weapons.add(TOP_1_WEAPON_REWARD);
                        itemGranted[0] = TOP_1_WEAPON_REWARD.name();
                        // The rest of the inventory rides through untouched — one in-memory transformation
                        // before the same updateInventory call, the one sanctioned writer (§18/C2).
                        characterDao.updateInventory(characterId,
                            new Inventory(weapons, locked.skills(), locked.pets(), consumables));
                    }
                    case TOP_2_10 -> {
                        accountDao.addGlobalCurrency(accountId, TOP_2_10_GOLD_REWARD);
                        consumables.merge(ShopService.REPAIR_TOKEN, 1, Integer::sum);
                        consumables.merge(ShopService.PET_CARE_KIT, 1, Integer::sum);
                        characterDao.updateInventory(characterId,
                            new Inventory(weapons, locked.skills(), locked.pets(), consumables));
                    }
                    case TOP_11_100 -> accountDao.addGlobalCurrency(accountId, TOP_11_100_GOLD_REWARD);
                }

                remainingGold[0] = accountDao.getGlobalCurrency(accountId).orElse(0L);
                repairTokens[0] = consumables.getOrDefault(ShopService.REPAIR_TOKEN, 0);
                petCareKits[0] = consumables.getOrDefault(ShopService.PET_CARE_KIT, 0);
            });

            log.info("Character " + characterId + " claimed the ladder's " + tier + " reward for "
                + lastMonthStart + ".");
            return ServiceResult.success(MessageCode.SUCCESS,
                new LadderClaimResult(tier.name(), remainingGold[0], repairTokens[0], petCareKits[0], itemGranted[0]));
        } catch (Exception e) {
            log.error("Ladder claim failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /** The reads a claim guard needs, snapshotted together before the transaction opens. */
    private record ClaimGuard(int rank, boolean claimed) {
    }

    /** Mirrors QuestService's identical helper. */
    private static Inventory lockedInventory(CharacterDao characterDao, long characterId) {
        return characterDao.getInventoryForUpdate(characterId)
            .orElseGet(() -> new Inventory(new Array<>(), new Array<>(), new Array<>(), new HashMap<>()));
    }

    /** Mirrors QuestService's identical helper. */
    private static Map<String, Integer> consumablesOf(Inventory inventory) {
        return inventory.consumables() != null ? new HashMap<>(inventory.consumables()) : new HashMap<>();
    }
}
