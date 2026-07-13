package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.server.database.entity.CharacterEntity;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(CharacterEntity.class)
public interface CharacterDao {

    @SqlQuery("SELECT * FROM characters WHERE account_id = :accountId")
    List<CharacterEntity> getCharactersByAccountId(@Bind("accountId") long accountId);

    /** Loads one character by id, regardless of owner — matchmaking needs both sides of a pairing. */
    @SqlQuery("SELECT * FROM characters WHERE id = :characterId")
    Optional<CharacterEntity> findById(@Bind("characterId") long characterId);

    /**
     * Opponent-selection rating only. Deliberately narrow rather than widening the shared
     * {@code Character} record with an {@code elo} field: only opponent selection reads it today.
     * Story C1 is where Elo becomes client-visible and gets written back.
     */
    @SqlQuery("SELECT elo FROM characters WHERE id = :characterId")
    Optional<Integer> getElo(@Bind("characterId") long characterId);

    /**
     * Opponent candidates for an attack (story B4): every persisted character inside the Elo band,
     * excluding the requester itself. The caller picks randomly among them — deliberately not
     * "closest Elo", which would make matchups predictable fight after fight (system design §7).
     */
    @SqlQuery("SELECT * FROM characters WHERE id <> :characterId AND elo BETWEEN :minElo AND :maxElo")
    List<CharacterEntity> findOpponentCandidatesInEloRange(@Bind("characterId") long characterId,
                                                           @Bind("minElo") int minElo,
                                                           @Bind("maxElo") int maxElo);

    /** The unbounded-Elo widening step, used when no candidate falls inside the band (system design §7). */
    @SqlQuery("SELECT * FROM characters WHERE id <> :characterId")
    List<CharacterEntity> findAllOpponentCandidates(@Bind("characterId") long characterId);

    @SqlQuery("SELECT EXISTS(SELECT 1 FROM characters WHERE name = :name)")
    boolean isNameTaken(@Bind("name") String name);

    /**
     * Ownership guardrail (system design §6/B3): true only when the character with {@code id}
     * belongs to {@code accountId}. Used to reject combat requests referencing a character the
     * connection's account does not own, never trusting the client-supplied id beyond this check.
     */
    @SqlQuery("SELECT EXISTS(SELECT 1 FROM characters WHERE id = :characterId AND account_id = :accountId)")
    boolean isOwnedByAccount(@Bind("accountId") long accountId, @Bind("characterId") long characterId);

    @SqlUpdate("INSERT INTO characters (account_id, name, faction, level, experience, max_hp, max_mp, max_stamina, base_def, base_atk, stats, inventory, loadout) " +
        "VALUES (:c.accountId, :c.name, :c.faction, :c.level, :c.experience, :c.maxHp, :c.maxMp, :c.maxStamina, :c.baseDef, :c.baseAtk, :c.stats, :c.inventory, :c.loadout)")
    @GetGeneratedKeys("id")
    long createCharacter(@BindMethods("c") CharacterEntity characterEntity);

    /**
     * Applies one battle's Exp and Elo payout (story C1). An atomic increment, not a read-then-write:
     * the new totals are computed by the database, so two concurrent battles for the same character
     * can't overwrite each other's reward. {@code eloDelta} is negative on a loss (system design §8.1).
     */
    @SqlUpdate("UPDATE characters SET experience = experience + :expDelta, elo = elo + :eloDelta WHERE id = :characterId")
    void addExperienceAndElo(@Bind("characterId") long characterId,
                             @Bind("expDelta") long expDelta,
                             @Bind("eloDelta") int eloDelta);

    @SqlUpdate("DELETE FROM characters WHERE account_id = :accountId AND name = :name")
    boolean deleteCharacter(@Bind("accountId") long accountId, @Bind("name") String name);

    @SqlQuery("SELECT COUNT(*) FROM characters WHERE account_id = :accountId")
    int getCharacterCount(@Bind("accountId") long accountId);
}
