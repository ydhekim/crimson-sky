package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.server.database.entity.CharacterEntity;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

@RegisterConstructorMapper(CharacterEntity.class)
public interface CharacterDao {

    @SqlQuery("SELECT * FROM characters WHERE account_id = :accountId")
    List<CharacterEntity> getCharactersByAccountId(@Bind("accountId") long accountId);

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

    @SqlUpdate("DELETE FROM characters WHERE account_id = :accountId AND name = :name")
    boolean deleteCharacter(@Bind("accountId") long accountId, @Bind("name") String name);

    @SqlQuery("SELECT COUNT(*) FROM characters WHERE account_id = :accountId")
    int getCharacterCount(@Bind("accountId") long accountId);
}
