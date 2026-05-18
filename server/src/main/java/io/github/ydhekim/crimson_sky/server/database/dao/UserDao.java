package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.server.database.entity.Account;
import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
import io.github.ydhekim.crimson_sky.server.database.entity.User;
import org.jdbi.v3.json.Json;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.util.Optional;

@RegisterConstructorMapper(User.class)
@RegisterConstructorMapper(Account.class)
public interface UserDao {

    @SqlQuery("SELECT * FROM users WHERE platform_type = :platformType AND identity_token = :token")
    Optional<User> findUserByToken(@Bind("platformType") String platformType, @Bind("token") String token);

    @SqlUpdate("INSERT INTO users (platform_type, identity_token) VALUES (:platformType, :token)")
    @GetGeneratedKeys("id")
    long insertUser(@Bind("platformType") String platformType, @Bind("token") String token);

    @SqlUpdate("INSERT INTO accounts (user_id, max_slots, global_currency, settings) VALUES (:userId, 3, 0, :s)")
    @GetGeneratedKeys("id")
    long insertAccount(@Bind("userId") long userId, @Json @Bind("s") AccountSettings defaultSettings);

    @SqlQuery("SELECT * FROM accounts WHERE user_id = :userId")
    Optional<Account> findAccountByUserId(@Bind("userId") long userId);

    @Transaction
    default Account createUserAndAccount(PlatformType platformType, String token) {
        long userId = insertUser(platformType.name(), token);
        AccountSettings defaultSettings = new AccountSettings(0.5, "en_US", true);
        long accountId = insertAccount(userId, defaultSettings);
        return findAccountByUserId(userId).orElseThrow(() -> new IllegalStateException("Failed to retrieve created account"));
    }
}
