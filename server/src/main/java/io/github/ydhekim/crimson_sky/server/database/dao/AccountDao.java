package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;

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

    /** The account-wide gold wallet (system design §16), read to price a skill-tree spend. */
    @SqlQuery("SELECT global_currency FROM accounts WHERE id = :accountId")
    Optional<Long> getGlobalCurrency(@Bind("accountId") long accountId);

    /**
     * Atomically spends gold (system design §16). The {@code AND global_currency >= :cost} clause is the
     * overspend guard — the decrement fails (0 rows) rather than driving the wallet negative if the
     * caller's read lost a race. Returns rows affected: {@code 1} on success, {@code 0} when rejected.
     */
    @SqlUpdate("UPDATE accounts SET global_currency = global_currency - :cost "
        + "WHERE id = :accountId AND global_currency >= :cost")
    int spendGlobalCurrency(@Bind("accountId") long accountId, @Bind("cost") long cost);
}
