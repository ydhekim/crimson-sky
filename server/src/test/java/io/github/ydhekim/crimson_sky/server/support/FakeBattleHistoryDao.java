package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link BattleHistoryDao} so AttackServiceTest can construct an AttackService without a
 * database. Empty by default (0 battles fought today, ranked Elo at the 1000 baseline), so it never
 * blocks the existing opponent-selection tests; seed it via {@link #with} to exercise the daily-cap
 * rejection path, or {@link #withRanked} to give the ranked track a real number (system design §21).
 */
public class FakeBattleHistoryDao implements BattleHistoryDao {

    private record Row(long characterId, boolean won, Instant createdAt, String battleMode, Integer rankedEloDelta) {
    }

    private final List<Row> rows = new ArrayList<>();

    /** Seeds one past NORMAL battle, for cap-rejection tests. */
    public FakeBattleHistoryDao with(long characterId, boolean won, Instant createdAt) {
        rows.add(new Row(characterId, won, createdAt, "NORMAL", null));
        return this;
    }

    /** Seeds one past RANKED battle with a known Elo swing, for ranked-Elo tests. */
    public FakeBattleHistoryDao withRanked(long characterId, int rankedEloDelta, Instant createdAt) {
        rows.add(new Row(characterId, true, createdAt, "RANKED", rankedEloDelta));
        return this;
    }

    @Override
    public void insert(long characterId, Long opponentCharacterId, boolean opponentIsBot, boolean won,
                        int goldDelta, long expDelta, int eloDelta, String battleMode, Integer rankedEloDelta) {
        rows.add(new Row(characterId, won, Instant.now(), battleMode, rankedEloDelta));
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
    public int getRankedEloAsOf(long characterId, Instant asOf) {
        int sum = rows.stream()
            .filter(r -> r.characterId() == characterId && "RANKED".equals(r.battleMode()) && !r.createdAt().isAfter(asOf))
            .mapToInt(r -> r.rankedEloDelta() != null ? r.rankedEloDelta() : 0)
            .sum();
        return 1000 + sum;
    }
}
