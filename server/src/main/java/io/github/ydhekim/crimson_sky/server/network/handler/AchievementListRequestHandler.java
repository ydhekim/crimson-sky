package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.AchievementListRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.AchievementListResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.AchievementService;

public class AchievementListRequestHandler implements RequestHandler<AchievementListRequest> {
    private static final Logger log = new Logger("AchievementListRequestHandler", Logger.DEBUG);
    private final AchievementService achievementService;

    public AchievementListRequestHandler(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @Override
    public void handle(GameConnection connection, AchievementListRequest request) {
        if (connection.account == null) {
            log.info("Rejected achievement list request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        log.info("Received achievement list request from Connection ID: " + connection.getID());
        var result = achievementService.getPlayerAchievements(connection.account.id());

        if (result.success()) {
            log.info("Successfully processed achievement list request for Connection ID: " + connection.getID());
        } else {
            log.info("Failed to process achievement list request for Connection ID: " + connection.getID() + ". Reason: " + result.code().name());
        }

        connection.sendTCP(new AchievementListResponse(result.success(), result.code().name(), result.data()));
    }
}
