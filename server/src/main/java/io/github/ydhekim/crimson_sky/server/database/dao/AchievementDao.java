package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.common.model.AccountAchievement;
import io.github.ydhekim.crimson_sky.server.database.entity.AchievementDefinitionEntity;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface AchievementDao {

    /**
     * The existing read endpoint's query (S3's predecessor). Updated for V15 only where the schema forced
     * it: the join now targets the renamed {@code achievement_unlocks} table, and the dropped
     * {@code progress_data} column is gone from both this SELECT and {@code AccountAchievement}. The
     * {@code character_id IS NULL} filter keeps this account-level view reading only account-scope unlocks —
     * per-character unlocks surface through S3's character page, not here.
     */
    @SqlQuery("SELECT " +
        "  ad.key_name AS keyName, " +
        "  lk_t.key_name AS titleLocKey, " +
        "  lk_d.key_name AS descLocKey, " +
        "  ad.xp_reward AS xpReward, " +
        "  ad.icon_id AS iconId, " +
        "  (au.id IS NOT NULL) AS isUnlocked, " +
        "  au.unlocked_at AS unlockedAt " +
        "FROM achievement_definitions ad " +
        "JOIN localization_keys lk_t ON ad.title_loc_key = lk_t.id " +
        "JOIN localization_keys lk_d ON ad.desc_loc_key = lk_d.id " +
        "LEFT JOIN achievement_unlocks au ON ad.id = au.achievement_id AND au.account_id = :accountId " +
        "  AND au.character_id IS NULL")
    @RegisterConstructorMapper(AccountAchievement.class)
    List<AccountAchievement> getAchievementsForAccount(@Bind("accountId") long accountId);

    /** Every definition in evaluable form (system design §22) — the unlock engine's per-pass catalog read. */
    @RegisterConstructorMapper(AchievementDefinitionEntity.class)
    @SqlQuery("SELECT id, key_name AS keyName, scope, criteria_type AS criteriaType, criteria_params AS criteriaParams, " +
        "xp_reward AS xpReward, gold_reward AS goldReward, badge_id AS badgeId, title_id AS titleId, " +
        "bonus_character_slots AS bonusCharacterSlots, bonus_daily_battles AS bonusDailyBattles, " +
        "points, hidden, category FROM achievement_definitions")
    List<AchievementDefinitionEntity> findAllDefinitions();

    /**
     * Attempts an ACCOUNT-scope unlock, idempotently (system design §22). {@code ON CONFLICT DO NOTHING}
     * with no explicit target lets one statement work against Postgres's partial unique indexes
     * ({@code achv_unlock_account_uq}/{@code achv_unlock_character_uq}) <i>and</i> the H2 test schema —
     * Postgres considers every applicable unique index, so the {@code character_id IS NULL} partial index is
     * the one that fires here. Returns rows affected: 1 on a genuinely new unlock, 0 if already unlocked.
     * The {@code ON CONFLICT} clause is the only check, so there's no read-then-write TOCTOU gap to accept.
     */
    @SqlUpdate("INSERT INTO achievement_unlocks (account_id, achievement_id, character_id) " +
        "VALUES (:accountId, :achievementId, NULL) ON CONFLICT DO NOTHING")
    int insertAccountUnlockIgnoringConflict(@Bind("accountId") long accountId, @Bind("achievementId") long achievementId);

    /** As above, but CHARACTER-scope — the same bare {@code ON CONFLICT DO NOTHING} fires the other partial index. */
    @SqlUpdate("INSERT INTO achievement_unlocks (account_id, achievement_id, character_id) " +
        "VALUES (:accountId, :achievementId, :characterId) ON CONFLICT DO NOTHING")
    int insertCharacterUnlockIgnoringConflict(@Bind("accountId") long accountId, @Bind("achievementId") long achievementId,
                                              @Bind("characterId") long characterId);
}
