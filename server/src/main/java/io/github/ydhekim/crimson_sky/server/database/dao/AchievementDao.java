package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.common.model.AccountAchievement;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

public interface AchievementDao {

    @SqlQuery("SELECT " +
        "  ad.key_name AS keyName, " +
        "  lk_t.key_name AS titleLocKey, " +
        "  lk_d.key_name AS descLocKey, " +
        "  ad.xp_reward AS xpReward, " +
        "  ad.icon_id AS iconId, " +
        "  (aa.id IS NOT NULL) AS isUnlocked, " +
        "  aa.unlocked_at AS unlockedAt, " +
        "  aa.progress_data AS progressData " +
        "FROM achievement_definitions ad " +
        "JOIN localization_keys lk_t ON ad.title_loc_key = lk_t.id " +
        "JOIN localization_keys lk_d ON ad.desc_loc_key = lk_d.id " +
        "LEFT JOIN account_achievements aa ON ad.id = aa.achievement_id AND aa.account_id = :accountId")
    @RegisterConstructorMapper(AccountAchievement.class)
    List<AccountAchievement> getAchievementsForAccount(@Bind("accountId") long accountId);
}
