package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * The audit row written for every resolved attack (story C1, system design §8).
 *
 * <p>{@code opponentCharacterId} is a boxed {@link Long} on purpose: it is {@code null} exactly when
 * the opponent was a synthesized bot, which has no row in {@code characters} to reference.
 */
public interface BattleHistoryDao {

    @SqlUpdate("INSERT INTO battle_history (character_id, opponent_character_id, opponent_is_bot, gold_delta, experience_delta, elo_delta) " +
        "VALUES (:characterId, :opponentCharacterId, :opponentIsBot, :goldDelta, :expDelta, :eloDelta)")
    void insert(@Bind("characterId") long characterId,
                @Bind("opponentCharacterId") Long opponentCharacterId,
                @Bind("opponentIsBot") boolean opponentIsBot,
                @Bind("goldDelta") int goldDelta,
                @Bind("expDelta") long expDelta,
                @Bind("eloDelta") int eloDelta);
}
