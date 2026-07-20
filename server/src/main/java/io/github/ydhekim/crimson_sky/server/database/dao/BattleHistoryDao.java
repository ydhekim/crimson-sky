package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.server.database.entity.RecentMatchRow;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @SqlUpdate("INSERT INTO battle_history (character_id, opponent_character_id, opponent_is_bot, won, gold_delta, experience_delta, elo_delta, battle_mode, ranked_elo_delta, turn_count) " +
        "VALUES (:characterId, :opponentCharacterId, :opponentIsBot, :won, :goldDelta, :expDelta, :eloDelta, :battleMode, :rankedEloDelta, :turnCount)")
    void insert(@Bind("characterId") long characterId,
                @Bind("opponentCharacterId") Long opponentCharacterId,
                @Bind("opponentIsBot") boolean opponentIsBot,
                @Bind("won") boolean won,
                @Bind("goldDelta") int goldDelta,
                @Bind("expDelta") long expDelta,
                @Bind("eloDelta") int eloDelta,
                @Bind("battleMode") String battleMode,
                @Bind("rankedEloDelta") Integer rankedEloDelta,
                @Bind("turnCount") int turnCount);

    /** All-time win count, no period bound (system design §22) — TOTAL_WINS' own live-computed input. */
    @SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId AND won = true")
    int countTotalWins(@Bind("characterId") long characterId);

    /** All-time battle count, win or lose (system design §22) — the denominator for win percentage. */
    @SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId")
    int countTotalBattles(@Bind("characterId") long characterId);

    /**
     * The most recent {@code limit} battles with enough detail for a match-history list (system design §22).
     * {@code opponentName} is {@code NULL} for a bot opponent (no {@code characters} row to join) or a
     * since-deleted real character — {@link io.github.ydhekim.crimson_sky.server.service.CharacterPageService}
     * is what turns that {@code NULL} into a display-safe placeholder, not this query.
     */
    @RegisterConstructorMapper(RecentMatchRow.class)
    @SqlQuery("SELECT bh.won AS won, bh.battle_mode AS battleMode, bh.elo_delta AS eloDelta, " +
        "bh.ranked_elo_delta AS rankedEloDelta, bh.turn_count AS turnCount, bh.created_at AS occurredAt, " +
        "c.name AS opponentName FROM battle_history bh LEFT JOIN characters c ON bh.opponent_character_id = c.id " +
        "WHERE bh.character_id = :characterId ORDER BY bh.created_at DESC LIMIT :limit")
    List<RecentMatchRow> findRecentMatches(@Bind("characterId") long characterId, @Bind("limit") int limit);

    /**
     * The most recent {@code limit} outcomes, newest first (system design §22) — WIN_STREAK's input.
     * {@code AchievementUnlockService} counts leading {@code true}s; a generous limit (50) is plenty for any
     * realistic streak length without scanning the whole table.
     */
    @SqlQuery("SELECT won FROM battle_history WHERE character_id = :characterId ORDER BY created_at DESC LIMIT :limit")
    List<Boolean> findRecentOutcomes(@Bind("characterId") long characterId, @Bind("limit") int limit);

    /**
     * The lowest {@code turn_count} among this character's wins, or empty if none yet (system design §22) —
     * FASTEST_WIN_TURNS' input. {@code turn_count > 0} excludes rows written before this column existed
     * (defaulted to 0), which would otherwise look like an unbeatable 0-turn win.
     */
    @SqlQuery("SELECT MIN(turn_count) FROM battle_history WHERE character_id = :characterId AND won = true AND turn_count > 0")
    Optional<Integer> findFastestWinTurnCount(@Bind("characterId") long characterId);

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
