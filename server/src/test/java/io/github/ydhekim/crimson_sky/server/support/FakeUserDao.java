package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;
import io.github.ydhekim.crimson_sky.server.database.entity.Account;
import io.github.ydhekim.crimson_sky.server.database.entity.User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link UserDao} for {@code LoginRequestHandlerTest} — lets a test seed a known
 * existing user/account pair (with specific {@link AccountSettings}) so the login round trip
 * can be asserted without a database. Only the existing-user path is exercised (new-account
 * creation is UserServiceTest territory, not this handler-level test's job).
 */
public class FakeUserDao implements UserDao {
    private final Map<String, User> usersByKey = new HashMap<>();
    private final Map<Long, Account> accountsByUserId = new HashMap<>();
    private long nextUserId = 1;
    private long nextAccountId = 1;

    /** Seeds an existing user + account, as if already registered, with the given settings. */
    public Account seedExistingUser(String platformType, String token, AccountSettings settings) {
        long userId = nextUserId++;
        usersByKey.put(platformType + ":" + token, new User(userId, platformType, token, Instant.now()));
        Account account = new Account(nextAccountId++, userId, 3, 0, settings, Instant.now());
        accountsByUserId.put(userId, account);
        return account;
    }

    @Override
    public Optional<User> findUserByToken(String platformType, String token) {
        return Optional.ofNullable(usersByKey.get(platformType + ":" + token));
    }

    @Override
    public long insertUser(String platformType, String token) {
        throw new UnsupportedOperationException("not exercised — this test only covers the existing-user login path");
    }

    @Override
    public long insertAccount(long userId, AccountSettings defaultSettings) {
        throw new UnsupportedOperationException("not exercised — this test only covers the existing-user login path");
    }

    @Override
    public Optional<Account> findAccountByUserId(long userId) {
        return Optional.ofNullable(accountsByUserId.get(userId));
    }
}
