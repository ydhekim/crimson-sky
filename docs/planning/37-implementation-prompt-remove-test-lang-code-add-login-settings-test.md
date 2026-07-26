# Implementation prompt — drop `testLangCode`, add a login/settings regression test

Follow-up to a design discussion, not a bug report: `local.properties`' two dev-config values (`testIdentityToken`, `testLangCode`) don't serve the same purpose anymore, now that Settings persists real account language and `ConnectionScreen.onLoginResponse` (prompt 31/K4) already applies it. `local.properties` itself is fine to keep (it's gitignored — confirmed via `.gitignore:32` — genuinely a personal per-developer file, same pattern as a `.env`, not shared config); this is about one stale value inside it.

## 1. `testIdentityToken` stays — it's not test scaffolding

`PlatformType.TEST` + this token is the entire current authentication mechanism (no real Steam/account-linking login exists yet — that's Epic H/M6, not started). Don't touch `ConfigurationManager.getTestIdentityToken()`, `CrimsonSky.create()`'s `testToken` variable, or the `ConnectionScreen(game, testIdentityToken)` constructor — all of that is load-bearing today, not disposable.

## 2. `testLangCode` is vestigial — remove it

Traced the flow: `CrimsonSky.create()` sets `languageManager.setCurrentLang(testLangCode)` once at boot; `ConnectionScreen.onConnected()` uses whatever that set for the *first* `LocalizationRequest` — which only affects the transient "Connecting…"/"Authenticating…" status text shown for well under a second, before login completes. The instant `onLoginResponse()` succeeds (`ConnectionScreen.java:279`), it already overwrites this with the real persisted value: `game.getLanguageManager().setCurrentLang(settings.language())`. So `testLangCode` today controls nothing a player actually interacts with — only a sub-second pre-login flash.

Removing it doesn't need a replacement default: `LanguageManager`'s own constructor already reads a real, sensible default from LibGDX `Preferences` (`prefs.getString(PREF_LANG_KEY, "en_US")`), and `setCurrentLang()` already persists back into those same `Preferences` on every change — including the one `onLoginResponse` performs. So once `testLangCode` is gone, the very first boot ever falls back to `"en_US"` correctly, and every subsequent boot on the same machine already remembers whatever language was last active (via `Preferences`, independent of any account) even before the login round trip completes — a better bootstrap than the old hardcoded override, not just a removal.

Changes:

- `local.properties`: delete the `testLangCode=tr_TR` line. Keep `testIdentityToken=...`.
- `ConfigurationManager.java`: delete `getLangCode()` entirely (no other call site after this change).
- `CrimsonSky.java`'s `create()`: delete these two lines —
  ```java
  String testLangCode = configManager.getLangCode();
  ...
  languageManager.setCurrentLang(testLangCode);
  ```
  (Leave `String testToken = configManager.getTestIdentityToken();` and the `setScreen(new ConnectionScreen(this, testToken));` call untouched.)

## 3. Add a regression test for the login → settings round trip

No test today asserts that `LoginResponse` actually carries the account's persisted `AccountSettings` correctly — the exact property `ConnectionScreen.onLoginResponse` (and, transitively, `testLangCode`'s removal) depends on. Mirrors the `AccountServiceTest`/`FakeAccountDao` pattern already established for K4's volume-key regression guard.

New support fake, `server/src/test/java/io/github/ydhekim/crimson_sky/server/support/FakeUserDao.java`:

```java
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
```

New test, `server/src/test/java/io/github/ydhekim/crimson_sky/server/network/handler/LoginRequestHandlerTest.java`:

```java
package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.server.service.UserService;
import io.github.ydhekim.crimson_sky.server.support.FakeGameConnection;
import io.github.ydhekim.crimson_sky.server.support.FakeUserDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the login -> settings round trip {@code ConnectionScreen.onLoginResponse} depends on
 * (prompt 31/K4) to apply a returning player's real language/resolution/fullscreen instead of
 * boot-time defaults — the same property that made {@code testLangCode} (local.properties)
 * safe to remove (prompt 37): once this test passes, the client no longer needs a boot-time
 * override to end up with the right language, because the server-sent LoginResponse already
 * carries it correctly.
 */
class LoginRequestHandlerTest {

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
    }

    @Test
    void successfulLoginCarriesTheAccountsPersistedSettings() {
        FakeUserDao userDao = new FakeUserDao();
        AccountSettings persisted = new AccountSettings(0.6, "tr_TR", true, "1920x1080");
        userDao.seedExistingUser(PlatformType.TEST.name(), "known-token", persisted);

        var connection = FakeGameConnection.unauthenticated(1);
        // jdbi/achievementUnlockService are only touched on the new-user-creation branch (UserService.java:46-61) —
        // this test exercises the existing-user branch (:42-45) only, so both are safely null here, same as
        // SaveAccountSettingsRequestHandlerTest's precedent for an untouched dependency.
        var handler = new LoginRequestHandler(new UserService(userDao, null, null));

        handler.handle(connection, new LoginRequest(PlatformType.TEST, "known-token", "1.0.0", "Desktop", new HashMap<>()));

        LoginResponse response = connection.onlySentPacket(LoginResponse.class);
        assertEquals(persisted, response.settings());
    }

    @Test
    void unsupportedPlatformStillReturnsDefaultSettingsNeverNull() {
        var connection = FakeGameConnection.unauthenticated(1);
        var handler = new LoginRequestHandler(new UserService(new FakeUserDao(), null, null));

        handler.handle(connection, new LoginRequest(PlatformType.STEAM, "irrelevant", "1.0.0", "Desktop", new HashMap<>()));

        LoginResponse response = connection.onlySentPacket(LoginResponse.class);
        assertNotNull(response.settings());
        assertEquals(AccountSettings.createDefault(), response.settings());
    }
}
```

(`UserService`'s constructor is `UserService(UserDao userDao, Jdbi jdbi, AchievementUnlockService achievementUnlockService)` — confirmed both `null`s above are safe since the existing-user login path never reaches either. `PlatformType.STEAM` already exists as a real enum value alongside `TEST`, confirmed against `PlatformType.java` — no substitution needed.)

## 4. Testing / Definition of Done

1. `gradlew.bat core:test server:test` (or `gradlew.bat test`) — confirm the new `LoginRequestHandlerTest` passes and nothing else regresses.
2. `gradlew.bat lwjgl3:run` with a fresh `Preferences` state (or just note this is hard to reset without clearing LibGDX's local prefs store manually) — confirm the app still boots and logs in correctly with `testLangCode` gone from `local.properties`.
3. Grep the repo afterward for `testLangCode`/`getLangCode` — confirm zero remaining references outside this prompt's own history.

Definition of done: `local.properties` only has `testIdentityToken`; `ConfigurationManager.getLangCode()` is gone; `CrimsonSky.create()` no longer overrides the boot-time language; a new handler-level test guards that `LoginResponse` carries the account's real persisted settings (and never a null settings object on any path).
