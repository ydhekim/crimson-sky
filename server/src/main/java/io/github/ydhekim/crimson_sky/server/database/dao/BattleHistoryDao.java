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

    @SqlUpdate("INSERT INTO battle_history (character_id, opponent_character_id, opponent_is_bot, won, gold_delta, experience_delta, elo_delta, battle_mode, ranked_elo_delta) " +
        "VALUES (:characterId, :opponentCharacterId, :opponentIsBot, :won, :goldDelta, :expDelta, :eloDelta, :battleMode, :rankedEloDelta)")
    void insert(@Bind("characterId") long characterId,
                @Bind("opponentCharacterId") Long opponentCharacterId,
                @Bind("opponentIsBot") boolean opponentIsBot,
                @Bind("won") boolean won,
                @Bind("goldDelta") int goldDelta,
                @Bind("expDelta") long expDelta,
                @Bind("eloDelta") int eloDelta,
                @Bind("battleMode") String battleMode,
                @Bind("rankedEloDelta") Integer rankedEloDelta);

    /**
     * Live ranked Elo as of {@code asOf} (system design §21) — never a stored column, always
     * {@code 1000 + the sum of ranked-only deltas up to that instant}. The {@code asOf} bound is what lets
     * a future ladder query ask "what was this character's standing at the end of last month" with the
     * identical formula used for "what is it right now".
     */
    @SqlQuery("SELECT 1000 + COALESCE(SUM(ranked_elo_delta), 0) FROM battle_history " +
        "WHERE character_id = :characterId AND battle_mode = 'RANKED' AND created_at <= :asOf")
    int getRankedEloAsOf(@Bind("characterId") long characterId, @Bind("asOf") Instant asOf);

    /** Live ranked Elo right now — the form {@code AttackService}'s matchmaking actually needs. */
    default int getRankedElo(long characterId) {
        return getRankedEloAsOf(characterId, Instant.now());
    }

    /**
     * How many battles {@code characterId} has won since {@code since} (system design §19). The live
     * progress query behind every quest — a daily/weekly/repeatable quest differs only in which period
     * boundary it passes as {@code since}. {@code created_at > :since} is a half-open window: a win exactly
     * at the boundary instant belongs to the previous period, not this one.
     */
    @SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId AND won = true AND created_at > :since")
    int countWins(@Bind("characterId") long characterId, @Bind("since") Instant since);

    /**
     * How many battles (win or lose) {@code characterId} has fought since {@code since} — the daily
     * battle cap check (system design §20). Unlike {@link #countWins}, no {@code won} filter: every
     * attempt counts against the cap, not just wins.
     */
    @SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId AND created_at > :since")
    int countBattlesSince(@Bind("characterId") long characterId, @Bind("since") Instant since);
}
