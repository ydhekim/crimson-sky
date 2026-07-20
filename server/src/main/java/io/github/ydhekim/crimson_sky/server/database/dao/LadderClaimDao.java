package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;

/** The claim ledger for the monthly ladder (system design §21, V14) — mirrors QuestClaimDao's shape. */
public interface LadderClaimDao {

    @SqlUpdate("INSERT INTO ladder_claims (character_id, period_start) VALUES (:characterId, :periodStart)")
    void insert(@Bind("characterId") long characterId, @Bind("periodStart") Instant periodStart);

    @SqlQuery("SELECT EXISTS(SELECT 1 FROM ladder_claims WHERE character_id = :characterId AND period_start = :periodStart)")
    boolean isClaimed(@Bind("characterId") long characterId, @Bind("periodStart") Instant periodStart);
}
