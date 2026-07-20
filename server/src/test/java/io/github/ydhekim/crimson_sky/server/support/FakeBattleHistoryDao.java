package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link BattleHistoryDao} so AttackServiceTest can construct an AttackService without a
 * database. Empty by default (0 battles fought today, ranked Elo at the 1000 baseline), so it never
 * blocks the existing opponent-selection tests; seed it via {@link #with} to exercise the daily-cap
 * rejection path, or {@link #withRanked} to give the ranked track a real number (system design §21).
 */
public class FakeBattleHistoryDao implements BattleHistoryDao {

    private record Row(long characterId, boolean won, Instant createdAt, String battleMode, Integer rankedEloDelta,
                       int turnCount) {
    }

    private final List<Row> rows = new ArrayList<>();

    /** Seeds one past NORMAL battle, for cap-rejection tests. */
    public FakeBattleHistoryDao with(long characterId, boolean won, Instant createdAt) {
        rows.add(new Row(characterId, won, createdAt, "NORMAL", null, 0));
        return this;
    }

    /** Seeds one past RANKED battle with a known Elo swing, for ranked-Elo tests. */
    public FakeBattleHistoryDao withRanked(long characterId, int rankedEloDelta, Instant createdAt) {
        rows.add(new Row(characterId, true, createdAt, "RANKED", rankedEloDelta, 0));
        return this;
    }

    @Override
    public void insert(long characterId, Long opponentCharacterId, boolean opponentIsBot, boolean won,
                        int goldDelta, long expDelta, int eloDelta, String battleMode, Integer rankedEloDelta,
                        int turnCount) {
        rows.add(new Row(characterId, won, Instant.now(), battleMode, rankedEloDelta, turnCount));
    }

    @Override
    public int countWins(long characterId, Instant since) {
        return (int) rows.stream()
            .filter(r -> r.characterId() == characterId && r.won() && r.createdAt().isAfter(since))
            .count();
    }

    @Override
    public int countBattlesSince(long characterId, Instant since) {
        return (int) rows.stream()
            .filter(r -> r.characterId() == characterId && r.createdAt().isAfter(since))
            .count();
    }

    @Override
    public int countTotalWins(long characterId) {
        return (int) rows.stream()
            .filter(r -> r.characterId() == characterId && r.won())
            .count();
    }

    @Override
    public List<Boolean> findRecentOutcomes(long characterId, int limit) {
        return rows.stream()
            .filter(r -> r.characterId() == characterId)
            .sorted(Comparator.comparing(Row::createdAt).reversed()) // newest first, matching the SQL ORDER BY
            .limit(limit)
            .map(Row::won)
            .toList();
    }

    @Override
    public Optional<Integer> findFastestWinTurnCount(long characterId) {
        return rows.stream()
            .filter(r -> r.characterId() == characterId && r.won() && r.turnCount() > 0)
            .map(Row::turnCount)
            .min(Comparator.naturalOrder());
    }

    @Override
    public int getRankedEloAsOf(long characterId, Instant asOf) {
        int sum = rows.stream()
            .filter(r -> r.characterId() == characterId && "RANKED".equals(r.battleMode()) && !r.createdAt().isAfter(asOf))
            .mapToInt(r -> r.rankedEloDelta() != null ? r.rankedEloDelta() : 0)
            .sum();
        return 1000 + sum;
    }
}
