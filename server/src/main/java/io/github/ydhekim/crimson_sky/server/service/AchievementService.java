package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.AccountAchievement;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.server.database.dao.AchievementDao;

import java.util.List;

public class AchievementService {
    private static final Logger log = new Logger("AchievementService", Logger.DEBUG);
    private final AchievementDao achievementDao;

    public AchievementService(AchievementDao achievementDao) {
        this.achievementDao = achievementDao;
    }

    public ServiceResult<List<AccountAchievement>> getPlayerAchievements(long accountId) {
        try {
            List<AccountAchievement> achievements = achievementDao.getAchievementsForAccount(accountId);

            return ServiceResult.success(MessageCode.SUCCESS, achievements);

        } catch (Exception e) {
            log.error("Failed to fetch achievements for account ID " + accountId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }
}
