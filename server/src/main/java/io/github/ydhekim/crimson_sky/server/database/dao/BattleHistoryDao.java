package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;

/**
 * The audit row written for every resolved attack (story C1, system design §8).
 *
 * <p>{@code opponentCharacterId} is a boxed {@link Long} on purpose: it is {@code null} exactly when
 * the opponent was a synthesized bot, which has no row in {@code characters} to reference.
 *
 * <p>{@code won} was added with the quest system (Epic P / system design §19, V11): quests count wins live
 * off this table, and reconstructing a win/loss from {@code gold_delta} would silently break the day those
 * reward constants are tuned — so the real outcome is recorded, not inferred.
 */
public interface BattleHistoryDao {

    @SqlUpdate("INSERT INTO battle_history (character_id, opponent_character_id, opponent_is_bot, won, gold_delta, experience_delta, elo_delta) " +
        "VALUES (:characterId, :opponentCharacterId, :opponentIsBot, :won, :goldDelta, :expDelta, :eloDelta)")
    void insert(@Bind("characterId") long characterId,
                @Bind("opponentCharacterId") Long opponentCharacterId,
                @Bind("opponentIsBot") boolean opponentIsBot,
                @Bind("won") boolean won,
                @Bind("goldDelta") int goldDelta,
                @Bind("expDelta") long expDelta,
                @Bind("eloDelta") int eloDelta);

    /**
     * How many battles {@code characterId} has won since {@code since} (system design §19). The live
     * progress query behind every quest — a daily/weekly/repeatable quest differs only in which period
     * boundary it passes as {@code since}. {@code created_at > :since} is a half-open window: a win exactly
     * at the boundary instant belongs to the previous period, not this one.
     */
    @SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId AND won = true AND created_at > :since")
    int countWins(@Bind("characterId") long characterId, @Bind("since") Instant since);
}
