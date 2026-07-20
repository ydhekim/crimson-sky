package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.server.database.entity.CharacterEntity;
import org.jdbi.v3.json.Json;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    /**
     * Ranked opponent candidates within ±eloRange of a live-computed ranked Elo, restricted to level-25+
     * characters (system design §21). Ranked Elo isn't a stored column, so the correlated subquery computes
     * it inline per candidate — the same "compute live" rule §19/§20 already established, just inlined into
     * a WHERE clause instead of read separately.
     */
    @SqlQuery("SELECT c.* FROM characters c WHERE c.id <> :characterId AND c.level >= 25 " +
        "AND (1000 + COALESCE((SELECT SUM(bh.ranked_elo_delta) FROM battle_history bh " +
        "WHERE bh.character_id = c.id AND bh.battle_mode = 'RANKED'), 0)) BETWEEN :minElo AND :maxElo")
    List<CharacterEntity> findRankedOpponentCandidatesInEloRange(@Bind("characterId") long characterId,
                                                                 @Bind("minElo") int minElo,
                                                                 @Bind("maxElo") int maxElo);

    /**
     * The unbounded ranked widening step (system design §21) — every level-25+ opponent but the requester,
     * regardless of Elo. No live-elo computation needed here, unlike the banded query above: the widen step
     * never filtered by Elo even in the normal-mode version ({@link #findAllOpponentCandidates}).
     */
    @SqlQuery("SELECT * FROM characters WHERE id <> :characterId AND level >= 25")
    List<CharacterEntity> findAllRankedOpponentCandidates(@Bind("characterId") long characterId);

    /**
     * How many level-25+ characters (other than characterId) have a live-computed ranked Elo, as of asOf,
     * strictly greater than elo (system design §21) — one more than this count is the character's ladder
     * rank. Reuses the same correlated-subquery shape as findRankedOpponentCandidatesInEloRange.
     */
    @SqlQuery("SELECT COUNT(*) FROM characters c WHERE c.id <> :characterId AND c.level >= 25 " +
        "AND (1000 + COALESCE((SELECT SUM(bh.ranked_elo_delta) FROM battle_history bh " +
        "WHERE bh.character_id = c.id AND bh.battle_mode = 'RANKED' AND bh.created_at <= :asOf), 0)) > :elo")
    int countRankedCharactersAboveEloAsOf(@Bind("characterId") long characterId, @Bind("elo") int elo,
                                          @Bind("asOf") Instant asOf);

    @SqlQuery("SELECT EXISTS(SELECT 1 FROM characters WHERE name = :name)")
    boolean isNameTaken(@Bind("name") String name);

    /**
     * Ownership guardrail (system design §6/B3): true only when the character with {@code id}
     * belongs to {@code accountId}. Used to reject combat requests referencing a character the
     * connection's account does not own, never trusting the client-supplied id beyond this check.
     */
    @SqlQuery("SELECT EXISTS(SELECT 1 FROM characters WHERE id = :characterId AND account_id = :accountId)")
    boolean isOwnedByAccount(@Bind("accountId") long accountId, @Bind("characterId") long characterId);

    @SqlUpdate("INSERT INTO characters (account_id, name, faction, level, experience, max_hp, max_mp, max_stamina, base_def, base_atk, stats, inventory, loadout, skill_tree) " +
        "VALUES (:c.accountId, :c.name, :c.faction, :c.level, :c.experience, :c.maxHp, :c.maxMp, :c.maxStamina, :c.baseDef, :c.baseAtk, :c.stats, :c.inventory, :c.loadout, :c.skillTree)")
    @GetGeneratedKeys("id")
    long createCharacter(@BindMethods("c") CharacterEntity characterEntity);

    /**
     * Applies one battle's full progress in a single statement (story C1 + Epic L / system design §8.1,
     * §15). Exp, Elo, and the two progression currencies are atomic increments — the new totals are
     * computed by the database, so two concurrent battles for the same character can't overwrite each
     * other's reward. {@code eloDelta} is negative on a loss (system design §8.1).
     *
     * <p>{@code newLevel} is the <b>absolute</b> resulting level ({@code currentLevel + levelsGained}),
     * not a delta: unlike the other columns, {@code level} is derived from cumulative experience by the
     * caller's level-up loop, not additively accumulated in SQL.
     */
    @SqlUpdate("UPDATE characters SET experience = experience + :expDelta, elo = elo + :eloDelta, "
        + "level = :newLevel, unspent_stat_points = unspent_stat_points + :statPointsGained, "
        + "skill_points = skill_points + :skillPointsGained WHERE id = :characterId")
    void applyBattleProgress(@Bind("characterId") long characterId,
                             @Bind("expDelta") long expDelta,
                             @Bind("eloDelta") int eloDelta,
                             @Bind("newLevel") int newLevel,
                             @Bind("statPointsGained") int statPointsGained,
                             @Bind("skillPointsGained") int skillPointsGained);

    /**
     * The unspent stat-point balance for a character (Epic L / system design §15). Narrow read, in the
     * style of {@link #getElo}: only the stat-point spend path needs it, so it stays off the shared
     * {@code Character} record. Used to compute a friendly over-budget error and the remaining balance;
     * the authoritative guard against overspending is {@link #spendStatPoints}'s own {@code WHERE} clause.
     */
    @SqlQuery("SELECT unspent_stat_points FROM characters WHERE id = :characterId")
    Optional<Integer> getUnspentStatPoints(@Bind("characterId") long characterId);

    /**
     * Atomically spends stat points (Epic L / system design §15): writes the merged {@code stats} and
     * decrements the balance in one guarded statement. The {@code AND unspent_stat_points >= :spent}
     * clause is the real overspend guard — it makes the decrement fail rather than drive the balance
     * negative if the caller's read of the balance lost a race. Returns rows affected: {@code 1} on
     * success, {@code 0} when the guard rejected the spend.
     */
    @SqlUpdate("UPDATE characters SET stats = :stats, unspent_stat_points = unspent_stat_points - :spent "
        + "WHERE id = :characterId AND unspent_stat_points >= :spent")
    int spendStatPoints(@Bind("characterId") long characterId, @Bind("stats") @Json Stats stats, @Bind("spent") int spent);

    /**
     * Reads a character's inventory under a row lock (Epic L / system design §15, §17), for the bonus
     * item-grant's read-modify-write. {@code FOR UPDATE} holds the row for the rest of the enclosing
     * transaction so the write-back can't race a concurrent inventory change — the deliberately simple
     * durability approach §17 settled on, first built here. {@code @Json} lets Jackson (de)serialize the
     * embedded arrays; never hand-roll the JSON.
     */
    @SqlQuery("SELECT inventory FROM characters WHERE id = :characterId FOR UPDATE")
    @Json
    Optional<Inventory> getInventoryForUpdate(@Bind("characterId") long characterId);

    /**
     * Writes a character's whole inventory column back (Epic L / system design §15). The <b>only</b>
     * {@code UPDATE} on {@code characters} permitted to touch {@code inventory} — a deliberate, named
     * exception carved into {@code BattleLeavesInventoryAloneTest} (story C2), which continues to reject
     * any other update reaching the stored items.
     */
    @SqlUpdate("UPDATE characters SET inventory = :inventory WHERE id = :characterId")
    void updateInventory(@Bind("characterId") long characterId, @Bind("inventory") @Json Inventory inventory);

    /**
     * Writes a character's whole loadout column back (system design §4.4/§16). Unlike inventory's
     * append-style grant, a loadout save is unconditional — the client submits the entire new loadout
     * each time. The <b>only</b> {@code UPDATE} on {@code characters} permitted to touch {@code loadout};
     * a deliberate, named exception carved into {@code BattleLeavesInventoryAloneTest} (story C2), which
     * continues to reject any other update reaching {@code loadout} <i>or</i> {@code inventory}.
     */
    @SqlUpdate("UPDATE characters SET loadout = :loadout WHERE id = :characterId")
    void updateLoadout(@Bind("characterId") long characterId, @Bind("loadout") @Json Loadout loadout);

    /** The learned skill-tree map (node id → rank) for a character (system design §16). */
    @SqlQuery("SELECT skill_tree FROM characters WHERE id = :characterId")
    @Json
    Optional<Map<String, Integer>> getSkillTree(@Bind("characterId") long characterId);

    /** Writes the whole skill-tree map back after a learn/upgrade (system design §16). */
    @SqlUpdate("UPDATE characters SET skill_tree = :skillTree WHERE id = :characterId")
    void updateSkillTree(@Bind("characterId") long characterId, @Bind("skillTree") @Json Map<String, Integer> skillTree);

    /**
     * The unspent skill-point balance for a character (system design §15/§16). Narrow read in the style
     * of {@link #getUnspentStatPoints}: only the skill-tree spend path reads it, to compute a friendly
     * over-budget error and the remaining balance; the authoritative overspend guard is
     * {@link #spendSkillPoints}'s own {@code WHERE} clause.
     */
    @SqlQuery("SELECT skill_points FROM characters WHERE id = :characterId")
    Optional<Integer> getSkillPoints(@Bind("characterId") long characterId);

    /**
     * Atomically spends skill points on a tree node (system design §16). The
     * {@code AND skill_points >= :cost} clause is the real overspend guard — it makes the decrement fail
     * (0 rows) rather than drive the balance negative if the caller's read lost a race. Returns rows
     * affected: {@code 1} on success, {@code 0} when the guard rejected the spend.
     */
    @SqlUpdate("UPDATE characters SET skill_points = skill_points - :cost "
        + "WHERE id = :characterId AND skill_points >= :cost")
    int spendSkillPoints(@Bind("characterId") long characterId, @Bind("cost") int cost);

    /**
     * The daily-battle-cap bonus for a character (system design §20). Narrow read in the style of
     * {@link #getUnspentStatPoints}: only {@code AttackService}'s cap check needs it.
     */
    @SqlQuery("SELECT bonus_daily_battles FROM characters WHERE id = :characterId")
    Optional<Integer> getBonusDailyBattles(@Bind("characterId") long characterId);

    /**
     * Grants (or revokes, with a negative {@code delta}) daily-battle-cap bonus (system design §20/Q3).
     * An atomic increment, not a read-then-write — mirrors {@link AccountDao#addGlobalCurrency}. Nothing
     * calls this yet; it exists so a future IAP/achievement/quest grant path has somewhere to write.
     */
    @SqlUpdate("UPDATE characters SET bonus_daily_battles = bonus_daily_battles + :delta WHERE id = :characterId")
    void addBonusDailyBattles(@Bind("characterId") long characterId, @Bind("delta") int delta);

    /**
     * A pure additive XP grant (system design §22's achievement rewards) — deliberately does NOT recompute
     * level the way {@link #applyBattleProgress} does. An achievement's XP reward compounds into the
     * character's next real battle's level-up check rather than triggering an immediate cascading recompute
     * here; extremely rare in practice (would need an achievement's own XP to itself cross a level threshold
     * the triggering battle's XP didn't), and this keeps achievement evaluation from recursing into another
     * achievement evaluation. A documented first-pass simplification, not an oversight.
     */
    @SqlUpdate("UPDATE characters SET experience = experience + :xpDelta WHERE id = :characterId")
    void addExperience(@Bind("characterId") long characterId, @Bind("xpDelta") long xpDelta);

    @SqlUpdate("DELETE FROM characters WHERE account_id = :accountId AND name = :name")
    boolean deleteCharacter(@Bind("accountId") long accountId, @Bind("name") String name);

    @SqlQuery("SELECT COUNT(*) FROM characters WHERE account_id = :accountId")
    int getCharacterCount(@Bind("accountId") long accountId);
}
