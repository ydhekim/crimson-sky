package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.CharacterPage;
import io.github.ydhekim.crimson_sky.common.model.CharacterPageAchievement;
import io.github.ydhekim.crimson_sky.common.model.CharacterStatistics;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.RecentMatch;
import io.github.ydhekim.crimson_sky.server.database.dao.AchievementDao;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import org.jdbi.v3.core.Jdbi;

import java.util.List;

/**
 * The read-only character-page aggregate (system design §22, Epic S3) plus the title-equip write (S4). Same
 * "odd one out, takes the raw {@link Jdbi}" shape as {@code LadderService}: a page assembly reads across
 * {@code battle_history}, {@code achievement_definitions}/{@code achievement_unlocks}, and {@code characters},
 * and the title-equip write, though a single-table update, uses the same handle-attach convention for
 * consistency. Nothing here stores statistics — every number is recomputed live off {@code battle_history},
 * the same rule §19/§20/§21 already established.
 */
public class CharacterPageService {

    private static final Logger log = new Logger("CharacterPageService", Logger.DEBUG);
    private static final int RECENT_MATCH_LIMIT = 5;
    private static final String UNKNOWN_OPPONENT_PLACEHOLDER = "Unknown Challenger";

    private final Jdbi jdbi;
    private final CharacterService characterService;

    public CharacterPageService(Jdbi jdbi, CharacterService characterService) {
        this.jdbi = jdbi;
        this.characterService = characterService;
    }

    /**
     * A character's full page (system design §22): live statistics, last-{@value #RECENT_MATCH_LIMIT} match
     * history, every achievement with its unlock status, the summed achievement score, and the equipped title.
     * Ownership-guarded, failing closed to {@code ERROR_UNKNOWN}.
     */
    public ServiceResult<CharacterPage> getCharacterPage(long accountId, long characterId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Character page rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            ServiceResult<Character> character = characterService.getCharacter(characterId);
            if (!character.success()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            CharacterPage page = jdbi.withHandle(handle -> {
                BattleHistoryDao battleHistoryDao = handle.attach(BattleHistoryDao.class);
                AchievementDao achievementDao = handle.attach(AchievementDao.class);
                CharacterDao characterDao = handle.attach(CharacterDao.class);

                int totalWins = battleHistoryDao.countTotalWins(characterId);
                int totalBattles = battleHistoryDao.countTotalBattles(characterId);
                int totalLosses = totalBattles - totalWins;
                double winPercentage = totalBattles == 0 ? 0.0 : (totalWins * 100.0) / totalBattles;
                int currentStreak = RewardService.currentStreak(
                    battleHistoryDao.findRecentOutcomes(characterId, 50));
                Integer fastestWinTurns = battleHistoryDao.findFastestWinTurnCount(characterId).orElse(null);

                List<RecentMatch> recentMatches = battleHistoryDao.findRecentMatches(characterId, RECENT_MATCH_LIMIT)
                    .stream()
                    .map(row -> new RecentMatch(row.won(), row.battleMode(), row.eloDelta(), row.rankedEloDelta(),
                        row.turnCount(), row.opponentName() != null ? row.opponentName() : UNKNOWN_OPPONENT_PLACEHOLDER,
                        row.occurredAt()))
                    .toList();

                CharacterStatistics statistics = new CharacterStatistics(
                    totalWins, totalLosses, winPercentage, currentStreak, fastestWinTurns, recentMatches);

                List<CharacterPageAchievement> achievements =
                    achievementDao.getAchievementsForCharacterPage(accountId, characterId);
                int achievementScore = achievements.stream()
                    .filter(CharacterPageAchievement::isUnlocked)
                    .mapToInt(CharacterPageAchievement::points)
                    .sum();

                String equippedTitle = characterDao.getEquippedTitle(characterId).orElse(null);

                return new CharacterPage(character.data(), statistics, achievements, achievementScore, equippedTitle);
            });

            return ServiceResult.success(MessageCode.SUCCESS, page);
        } catch (Exception e) {
            log.error("Character page assembly failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * {@code titleId == null} always succeeds (clearing the title never needs an unlock check). A non-null
     * {@code titleId} must be unlocked for this account/character (system design §22) — the same
     * account-vs-character OR-by-scope rule {@link AchievementUnlockService} writes against, read here.
     */
    public ServiceResult<String> setEquippedTitle(long accountId, long characterId, String titleId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Title equip rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            jdbi.useHandle(handle -> {
                if (titleId != null) {
                    boolean unlocked = handle.attach(AchievementDao.class)
                        .isTitleUnlockedFor(titleId, accountId, characterId);
                    if (!unlocked) {
                        throw new TitleNotUnlockedException();
                    }
                }
                handle.attach(CharacterDao.class).setEquippedTitle(characterId, titleId);
            });

            log.info("Character " + characterId + " equipped title: " + titleId);
            return ServiceResult.success(MessageCode.SUCCESS, titleId);
        } catch (TitleNotUnlockedException e) {
            log.info("Title equip rejected for character " + characterId + ": '" + titleId + "' is not unlocked.");
            return ServiceResult.failure(MessageCode.TITLE_NOT_UNLOCKED);
        } catch (Exception e) {
            log.error("Title equip failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /** Internal control-flow signal only, to unwind out of the {@code useHandle} lambda with the right code. */
    private static final class TitleNotUnlockedException extends RuntimeException {
    }
}
