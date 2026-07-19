package io.github.ydhekim.crimson_sky.server.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.jackson2.Jackson2Config;
import org.jdbi.v3.jackson2.Jackson2Plugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A throwaway in-memory database for the reward-persistence tests (story C1).
 *
 * <p>{@code RewardService} takes a raw {@link Jdbi} precisely so its three writes share one
 * transaction — that property cannot be tested against a fake DAO, only against something that can
 * actually roll back. H2 stands in for Postgres here; production still runs the Flyway migrations
 * against Postgres and nothing else.
 *
 * <p>The schema below is a deliberate <b>subset</b> of the real one: only the tables and columns a
 * reward reads or writes, plus {@code inventory}/{@code loadout} so the C2 regression test can watch
 * them stay untouched. The Flyway migrations can't be replayed here — V1 needs {@code citext} and
 * {@code jsonb} — so any column added to the reward path must be added here too, or its test will not
 * see it. Everything the reward path touches is plain scalar columns, which H2 models faithfully.
 */
public final class TestDatabase {

    private static final AtomicInteger NEXT_DB = new AtomicInteger();

    private final Jdbi jdbi;

    private TestDatabase(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** A fresh, isolated database. Each call gets its own, so tests never see each other's rows. */
    public static TestDatabase create() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:crimsonsky_" + NEXT_DB.incrementAndGet()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
            .installPlugin(new SqlObjectPlugin())
            .installPlugin(new Jackson2Plugin());

        // Match production's mapper closely enough for the @Json inventory read-modify-write (Epic L's
        // bonus grant): ParameterNamesModule lets Jackson build the Inventory/Weapon records via their
        // canonical constructors, exactly as DatabaseManager configures it against Postgres.
        jdbi.getConfig(Jackson2Config.class).setMapper(new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new ParameterNamesModule())
            .registerModule(new io.github.ydhekim.crimson_sky.server.database.GdxArrayJacksonModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

        jdbi.useHandle(handle -> {
            handle.execute("CREATE TABLE accounts ("
                + "id INTEGER PRIMARY KEY, "
                + "global_currency BIGINT NOT NULL DEFAULT 0)");
            handle.execute("CREATE TABLE characters ("
                + "id INTEGER PRIMARY KEY, "
                + "account_id INTEGER NOT NULL REFERENCES accounts (id), "
                + "name VARCHAR(50) NOT NULL, "
                + "level INTEGER NOT NULL DEFAULT 1, "
                + "experience BIGINT NOT NULL DEFAULT 0, "
                + "elo INTEGER NOT NULL DEFAULT 1000, "
                + "unspent_stat_points INTEGER NOT NULL DEFAULT 0, "
                + "skill_points INTEGER NOT NULL DEFAULT 0, "
                + "stats VARCHAR(2000), "
                + "inventory VARCHAR(2000) NOT NULL, "
                + "loadout VARCHAR(2000) NOT NULL, "
                + "skill_tree VARCHAR(2000) NOT NULL DEFAULT '{}')");
            handle.execute("CREATE TABLE battle_history ("
                + "id SERIAL PRIMARY KEY, "
                + "character_id INTEGER NOT NULL REFERENCES characters (id), "
                + "opponent_character_id INTEGER REFERENCES characters (id), "
                + "opponent_is_bot BOOLEAN NOT NULL DEFAULT FALSE, "
                + "won BOOLEAN NOT NULL DEFAULT FALSE, "
                + "gold_delta INTEGER NOT NULL, "
                + "experience_delta BIGINT NOT NULL, "
                + "elo_delta INTEGER NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            // Quest claim ledger (Epic P / system design §19, V11). The real UNIQUE triple is modelled so a
            // second daily/weekly claim of the same period collides exactly as it would in production.
            handle.execute("CREATE TABLE quest_claims ("
                + "id SERIAL PRIMARY KEY, "
                + "character_id INTEGER NOT NULL REFERENCES characters (id), "
                + "quest_id VARCHAR(64) NOT NULL, "
                + "period_start TIMESTAMP NOT NULL, "
                + "claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE (character_id, quest_id, period_start))");
        });

        return new TestDatabase(jdbi);
    }

    public Jdbi jdbi() {
        return jdbi;
    }

    /** Registers an account holding {@code gold} in its wallet. */
    public TestDatabase withAccount(long accountId, long gold) {
        jdbi.useHandle(handle -> handle
            .execute("INSERT INTO accounts (id, global_currency) VALUES (?, ?)", accountId, gold));
        return this;
    }

    /**
     * Registers a character row at level 1. {@code inventoryJson}/{@code loadoutJson} are stored verbatim
     * so a test can compare the stored text before and after a battle, byte for byte.
     */
    public TestDatabase withCharacter(long characterId, long accountId, String name, long experience,
                                      int elo, String inventoryJson, String loadoutJson) {
        return withCharacter(characterId, accountId, name, 1, experience, elo, inventoryJson, loadoutJson);
    }

    /** As above, but seeding an explicit starting {@code level} (Epic L tests that begin near a milestone). */
    public TestDatabase withCharacter(long characterId, long accountId, String name, int level, long experience,
                                      int elo, String inventoryJson, String loadoutJson) {
        jdbi.useHandle(handle -> handle.execute(
            "INSERT INTO characters (id, account_id, name, level, experience, elo, inventory, loadout) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            characterId, accountId, name, level, experience, elo, inventoryJson, loadoutJson));
        return this;
    }

    /** Sets a character's skill-point balance (system design §16 spend tests). */
    public TestDatabase withSkillPoints(long characterId, int skillPoints) {
        jdbi.useHandle(handle -> handle
            .execute("UPDATE characters SET skill_points = ? WHERE id = ?", skillPoints, characterId));
        return this;
    }

    /** Seeds a character's skill-tree map verbatim (e.g. {@code {"physical.t1.n1":1}}) for upgrade tests. */
    public TestDatabase withSkillTree(long characterId, String skillTreeJson) {
        jdbi.useHandle(handle -> handle
            .execute("UPDATE characters SET skill_tree = ? WHERE id = ?", skillTreeJson, characterId));
        return this;
    }

    public long goldOf(long accountId) {
        return queryOne("SELECT global_currency FROM accounts WHERE id = ?", Long.class, accountId);
    }

    public String skillTreeJsonOf(long characterId) {
        return queryOne("SELECT skill_tree FROM characters WHERE id = ?", String.class, characterId);
    }

    public long experienceOf(long characterId) {
        return queryOne("SELECT experience FROM characters WHERE id = ?", Long.class, characterId);
    }

    public int eloOf(long characterId) {
        return queryOne("SELECT elo FROM characters WHERE id = ?", Integer.class, characterId);
    }

    public int levelOf(long characterId) {
        return queryOne("SELECT level FROM characters WHERE id = ?", Integer.class, characterId);
    }

    public int unspentStatPointsOf(long characterId) {
        return queryOne("SELECT unspent_stat_points FROM characters WHERE id = ?", Integer.class, characterId);
    }

    public int skillPointsOf(long characterId) {
        return queryOne("SELECT skill_points FROM characters WHERE id = ?", Integer.class, characterId);
    }

    public String inventoryJsonOf(long characterId) {
        return queryOne("SELECT inventory FROM characters WHERE id = ?", String.class, characterId);
    }

    public String loadoutJsonOf(long characterId) {
        return queryOne("SELECT loadout FROM characters WHERE id = ?", String.class, characterId);
    }

    public int battleHistoryRowCount() {
        return queryOne("SELECT COUNT(*) FROM battle_history", Integer.class);
    }

    /** The single {@code battle_history} row, for the tests that write exactly one. */
    public BattleHistoryRow onlyBattleHistoryRow() {
        return jdbi.withHandle(handle -> handle
            .select("SELECT character_id, opponent_character_id, opponent_is_bot, won, gold_delta, "
                + "experience_delta, elo_delta FROM battle_history")
            .map((rs, ctx) -> {
                long opponentId = rs.getLong("opponent_character_id");
                boolean opponentWasNull = rs.wasNull(); // must be read before any other column is
                return new BattleHistoryRow(
                    rs.getLong("character_id"),
                    opponentWasNull ? null : opponentId,
                    rs.getBoolean("opponent_is_bot"),
                    rs.getBoolean("won"),
                    rs.getInt("gold_delta"),
                    rs.getLong("experience_delta"),
                    rs.getInt("elo_delta"));
            })
            .one());
    }

    /**
     * Seeds one {@code battle_history} row directly (Epic P / system design §19 quest-counting tests), with
     * an explicit {@code won} outcome and {@code createdAt} instant so a test can place wins and losses on
     * either side of a period boundary. Bound as a bot fight with zero deltas — only {@code won} and
     * {@code created_at} matter to a win count.
     */
    public TestDatabase withBattleHistory(long characterId, boolean won, Instant createdAt) {
        jdbi.useHandle(handle -> handle.execute(
            "INSERT INTO battle_history (character_id, opponent_is_bot, won, gold_delta, experience_delta, "
                + "elo_delta, created_at) VALUES (?, TRUE, ?, 0, 0, 0, ?)",
            characterId, won, Timestamp.from(createdAt)));
        return this;
    }

    /**
     * Seeds one {@code quest_claims} row (Epic P / system design §19). {@code claimed_at} defaults to now, so
     * a claim seeded this way counts toward today's repeatable cap; pass an explicit {@code periodStart} that
     * differs per call for repeatable claims so they never collide on the {@code UNIQUE} triple.
     */
    public TestDatabase withQuestClaim(long characterId, String questId, Instant periodStart) {
        jdbi.useHandle(handle -> handle.execute(
            "INSERT INTO quest_claims (character_id, quest_id, period_start) VALUES (?, ?, ?)",
            characterId, questId, Timestamp.from(periodStart)));
        return this;
    }

    /** How many {@code quest_claims} rows a character holds for {@code questId} (any period). */
    public int questClaimCountOf(long characterId, String questId) {
        return queryOne("SELECT COUNT(*) FROM quest_claims WHERE character_id = ? AND quest_id = ?",
            Integer.class, characterId, questId);
    }

    /**
     * How many <b>distinct</b> {@code period_start} values a character's claims of {@code questId} carry.
     * The repeatable quest gives each claim its own moment, so two same-day repeatable claims must show two
     * distinct period_starts here while {@link #questClaimCountOf} shows two rows under the one quest id.
     */
    public int distinctQuestClaimPeriodCountOf(long characterId, String questId) {
        return queryOne("SELECT COUNT(DISTINCT period_start) FROM quest_claims WHERE character_id = ? AND quest_id = ?",
            Integer.class, characterId, questId);
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        return jdbi.withHandle(handle -> {
            Handle h = handle;
            Optional<T> value = h.select(sql, args).mapTo(type).findOne();
            return value.orElseThrow(() -> new AssertionError("no row for: " + sql));
        });
    }

    /** {@code opponentCharacterId} is {@code null} exactly for a bot fight, mirroring the real column. */
    public record BattleHistoryRow(long characterId, Long opponentCharacterId, boolean opponentIsBot,
                                   boolean won, int goldDelta, long experienceDelta, int eloDelta) {
    }
}
