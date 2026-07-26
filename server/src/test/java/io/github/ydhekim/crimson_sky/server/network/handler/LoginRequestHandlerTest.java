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
