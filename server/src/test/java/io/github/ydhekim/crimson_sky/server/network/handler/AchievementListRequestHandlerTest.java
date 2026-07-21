package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.AchievementListRequest;
import io.github.ydhekim.crimson_sky.server.support.FakeGameConnection;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The auth guard on the achievement-list handler (review §2.1/§3.2). The service dependency is deliberately
 * {@code null}: an unauthenticated connection must be dropped before the service is ever touched, so if the
 * guard is ever removed the handler NPEs instead of passing this test vacuously.
 */
class AchievementListRequestHandlerTest {

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
    }

    @Test
    void anUnauthenticatedConnectionGetsNoResponse() {
        var connection = FakeGameConnection.unauthenticated(1);
        var handler = new AchievementListRequestHandler(null);

        handler.handle(connection, new AchievementListRequest());

        assertTrue(connection.sentNothing());
    }
}
