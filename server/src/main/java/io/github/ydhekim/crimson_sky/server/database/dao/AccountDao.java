package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface AccountDao {

    @SqlUpdate("UPDATE accounts SET " +
        "settings = COALESCE(settings, '{}'::jsonb) || :settingsJson::jsonb " +
        "WHERE id = :accountId")
    boolean updateSettings(@Bind("accountId") long accountId, @Bind("settingsJson") String settingsJson);
}
