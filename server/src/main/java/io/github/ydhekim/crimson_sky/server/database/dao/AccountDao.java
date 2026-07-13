package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface AccountDao {

    @SqlUpdate("UPDATE accounts SET " +
        "settings = COALESCE(settings, '{}'::jsonb) || :settingsJson::jsonb " +
        "WHERE id = :accountId")
    boolean updateSettings(@Bind("accountId") long accountId, @Bind("settingsJson") String settingsJson);

    /**
     * Applies one battle's Gold payout (story C1). Gold is the account-wide wallet
     * ({@code accounts.global_currency}, V1), not a per-character balance — so a reward touches this
     * table as well as {@code characters}, and both writes must land together (see {@code RewardService}).
     * An atomic increment, not a read-then-write.
     */
    @SqlUpdate("UPDATE accounts SET global_currency = global_currency + :goldDelta WHERE id = :accountId")
    void addGlobalCurrency(@Bind("accountId") long accountId, @Bind("goldDelta") int goldDelta);
}
