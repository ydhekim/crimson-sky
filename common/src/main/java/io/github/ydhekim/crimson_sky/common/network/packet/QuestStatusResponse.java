package io.github.ydhekim.crimson_sky.common.network.packet;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.QuestProgress;

/**
 * Server → client answer to a {@link QuestStatusRequest} (system design §19, Epic P). On success,
 * {@code quests} carries each of the three quests' live progress and claim state for the current period. On
 * failure ({@code success == false}), {@code message} is the {@code MessageCode} name and {@code quests} is
 * {@code null}.
 */
public record QuestStatusResponse(boolean success, String message, Array<QuestProgress> quests) {
}
