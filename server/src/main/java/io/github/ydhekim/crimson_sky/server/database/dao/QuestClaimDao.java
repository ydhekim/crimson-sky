package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;

/**
 * The claim ledger for the quest system (system design §19, V11). Quest <i>progress</i> needs no storage —
 * it is counted live off {@code battle_history} — so this table holds only the fact that a completed quest
 * was already claimed within its period, which is the one thing live counting can't reconstruct.
 */
public interface QuestClaimDao {

    @SqlUpdate("INSERT INTO quest_claims (character_id, quest_id, period_start) VALUES (:characterId, :questId, :periodStart)")
    void insert(@Bind("characterId") long characterId, @Bind("questId") String questId, @Bind("periodStart") Instant periodStart);

    /** Daily/weekly's guard: has this exact period already been claimed (system design §19)? */
    @SqlQuery("SELECT EXISTS(SELECT 1 FROM quest_claims WHERE character_id = :characterId AND quest_id = :questId AND period_start = :periodStart)")
    boolean isClaimed(@Bind("characterId") long characterId, @Bind("questId") String questId, @Bind("periodStart") Instant periodStart);

    /**
     * The repeatable quest's cap (§19's note): every claim gets its own {@code period_start} (its own
     * {@code claimed_at} moment, not a shared boundary), so the daily/weekly {@code UNIQUE} trick can't
     * enforce the "at most 3" rule — this counts claims by wall-clock time instead.
     */
    @SqlQuery("SELECT COUNT(*) FROM quest_claims WHERE character_id = :characterId AND quest_id = :questId AND claimed_at > :since")
    int countClaimsSince(@Bind("characterId") long characterId, @Bind("questId") String questId, @Bind("since") Instant since);
}
