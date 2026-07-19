package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link BattleHistoryDao} so AttackServiceTest can construct an AttackService without a
 * database. Empty by default (0 battles fought today), so it never blocks the existing
 * opponent-selection tests; seed it via {@link #with} to exercise the daily-cap rejection path.
 */
public class FakeBattleHistoryDao implements BattleHistoryDao {

    private record Row(long characterId, boolean won, Instant createdAt) {
    }

    private final List<Row> rows = new ArrayList<>();

    /** Seeds one past battle, for cap-rejection tests. */
    public FakeBattleHistoryDao with(long characterId, boolean won, Instant createdAt) {
        rows.add(new Row(characterId, won, createdAt));
        return this;
    }

    @Override
    public void insert(long characterId, Long opponentCharacterId, boolean opponentIsBot, boolean won,
                        int goldDelta, long expDelta, int eloDelta) {
        rows.add(new Row(characterId, won, Instant.now()));
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
}
