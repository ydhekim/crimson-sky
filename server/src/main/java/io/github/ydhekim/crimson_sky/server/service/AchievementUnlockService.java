package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.server.achievement.AccountAchievementFacts;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementEvaluator;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementScope;
import io.github.ydhekim.crimson_sky.server.achievement.CharacterAchievementFacts;
import io.github.ydhekim.crimson_sky.server.achievement.UnlockedAchievement;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.server.database.dao.AchievementDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.entity.AchievementDefinitionEntity;
import org.jdbi.v3.core.Handle;

import java.util.ArrayList;
import java.util.List;

/**
 * The DB-touching half of the achievement system (system design §22) — it gathers definitions, asks the pure
 * {@link AchievementEvaluator} whether each is satisfied by the caller's facts, and, for each freshly-unlocked
 * one, writes the unlock row and applies its reward.
 *
 * <p><b>Stateless by design.</b> It holds no {@code Jdbi} or DAO of its own; every method takes the caller's
 * own {@link Handle} and attaches whatever DAOs it needs to <i>that</i> handle. So an unlock and its reward
 * always land inside whichever transaction the caller already has open — atomic with the battle reward
 * (RewardService) or the account creation (UserService) that triggered the evaluation, never a separate commit
 * that could half-apply.
 */
public class AchievementUnlockService {

    public List<UnlockedAchievement> evaluateCharacterAchievements(
            Handle handle, long accountId, long characterId, CharacterAchievementFacts facts) {
        AchievementDao achievementDao = handle.attach(AchievementDao.class);
        List<UnlockedAchievement> unlocked = new ArrayList<>();
        for (AchievementDefinitionEntity def : achievementDao.findAllDefinitions()) {
            if (def.scope() != AchievementScope.CHARACTER) {
                continue;
            }
            if (!AchievementEvaluator.isSatisfiedForCharacter(def, facts)) {
                continue;
            }
            int rows = achievementDao.insertCharacterUnlockIgnoringConflict(accountId, def.id(), characterId);
            if (rows == 0) {
                continue; // already unlocked — ON CONFLICT caught it, no TOCTOU gap to worry about
            }
            applyReward(handle, accountId, characterId, def);
            unlocked.add(new UnlockedAchievement(def.keyName(), def.points()));
        }
        return unlocked;
    }

    public List<UnlockedAchievement> evaluateAccountAchievements(
            Handle handle, long accountId, AccountAchievementFacts facts) {
        AchievementDao achievementDao = handle.attach(AchievementDao.class);
        List<UnlockedAchievement> unlocked = new ArrayList<>();
        for (AchievementDefinitionEntity def : achievementDao.findAllDefinitions()) {
            if (def.scope() != AchievementScope.ACCOUNT) {
                continue;
            }
            if (!AchievementEvaluator.isSatisfiedForAccount(def, facts)) {
                continue;
            }
            int rows = achievementDao.insertAccountUnlockIgnoringConflict(accountId, def.id());
            if (rows == 0) {
                continue;
            }
            applyReward(handle, accountId, null, def);
            unlocked.add(new UnlockedAchievement(def.keyName(), def.points()));
        }
        return unlocked;
    }

    /**
     * XP/gold target the triggering character/account; bonus_character_slots/bonus_daily_battles reuse
     * Epic Q's atomic-increment grant paths — their first real caller, built then and left unwired for
     * exactly this (system design §20/§22). badge_id/title_id need no code action here: a badge is simply
     * the fact of the unlock row plus a non-null badge_id (S3 reads it); a title needs
     * characters.equipped_title, which doesn't exist until S4. XP and per-character bonus battles are only
     * applied for a character-scope unlock (characterId != null); gold and slots are account-wide either way.
     */
    private void applyReward(Handle handle, long accountId, Long characterId, AchievementDefinitionEntity def) {
        if (def.xpReward() > 0 && characterId != null) {
            handle.attach(CharacterDao.class).addExperience(characterId, def.xpReward());
        }
        if (def.goldReward() > 0) {
            handle.attach(AccountDao.class).addGlobalCurrency(accountId, def.goldReward());
        }
        if (def.bonusCharacterSlots() > 0) {
            handle.attach(AccountDao.class).addCharacterSlots(accountId, def.bonusCharacterSlots());
        }
        if (def.bonusDailyBattles() > 0 && characterId != null) {
            handle.attach(CharacterDao.class).addBonusDailyBattles(characterId, def.bonusDailyBattles());
        }
    }
}
