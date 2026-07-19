package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.entity.CharacterEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link CharacterDao} so the service/handler layer can be tested without Postgres.
 * Characters are registered with the account that owns them and the Elo they queue at; every other
 * DAO method is unused by the combat/matchmaking path and throws rather than pretending to work.
 */
public class FakeCharacterDao implements CharacterDao {

    private record Row(Character character, long accountId, int elo, int unspentStatPoints) {
    }

    private final Map<Long, Row> rows = new LinkedHashMap<>();
    private boolean leakRequesterIntoCandidates;

    /** Registers {@code character} as owned by {@code accountId}, rated at {@code elo}, with no stat points. */
    public FakeCharacterDao with(Character character, long accountId, int elo) {
        return with(character, accountId, elo, 0);
    }

    /** As {@link #with(Character, long, int)}, but seeding an unspent-stat-point balance (Epic L tests). */
    public FakeCharacterDao with(Character character, long accountId, int elo, int unspentStatPoints) {
        rows.put(character.id(), new Row(character, accountId, elo, unspentStatPoints));
        return this;
    }

    /**
     * Makes the candidate queries return the requester too, as a broken/changed {@code WHERE id <>}
     * clause would. Lets a test prove {@code AttackService} re-excludes the requester itself rather
     * than trusting one SQL string to do it.
     */
    public FakeCharacterDao leakingRequesterIntoCandidates() {
        this.leakRequesterIntoCandidates = true;
        return this;
    }

    @Override
    public List<CharacterEntity> findOpponentCandidatesInEloRange(long characterId, int minElo, int maxElo) {
        return candidates(characterId, row -> row.elo() >= minElo && row.elo() <= maxElo);
    }

    @Override
    public List<CharacterEntity> findAllOpponentCandidates(long characterId) {
        return candidates(characterId, row -> true);
    }

    private List<CharacterEntity> candidates(long characterId, java.util.function.Predicate<Row> filter) {
        List<CharacterEntity> result = new ArrayList<>();
        for (Row row : rows.values()) {
            boolean isRequester = row.character().id() == characterId;
            if (isRequester && !leakRequesterIntoCandidates) {
                continue;
            }
            if (filter.test(row)) {
                result.add(CharacterEntity.fromCommonModel(row.accountId(), row.character()));
            }
        }
        return result;
    }

    @Override
    public Optional<CharacterEntity> findById(long characterId) {
        Row row = rows.get(characterId);
        return Optional.ofNullable(row).map(r -> CharacterEntity.fromCommonModel(r.accountId(), r.character()));
    }

    @Override
    public Optional<Integer> getElo(long characterId) {
        Row row = rows.get(characterId);
        return Optional.ofNullable(row).map(Row::elo);
    }

    @Override
    public boolean isOwnedByAccount(long accountId, long characterId) {
        Row row = rows.get(characterId);
        return row != null && row.accountId() == accountId;
    }

    @Override
    public List<CharacterEntity> getCharactersByAccountId(long accountId) {
        List<CharacterEntity> result = new ArrayList<>();
        for (Row row : rows.values()) {
            if (row.accountId() == accountId) {
                result.add(CharacterEntity.fromCommonModel(row.accountId(), row.character()));
            }
        }
        return result;
    }

    @Override
    public boolean isNameTaken(String name) {
        throw new UnsupportedOperationException("not exercised by combat/matchmaking tests");
    }

    /**
     * Throwing here is the point: a reward's writes must go through DAOs attached to {@code
     * RewardService}'s own transaction handle, never through the {@code onDemand} proxy a service holds.
     * If this ever fires, a reward has escaped its transaction.
     */
    @Override
    public void applyBattleProgress(long characterId, long expDelta, int eloDelta, int newLevel,
                                    int statPointsGained, int skillPointsGained) {
        throw new UnsupportedOperationException("rewards are written through the transaction's own DAO handle");
    }

    /** The bonus item-grant path is transactional too — it must never reach the fake's read models. */
    @Override
    public Optional<Inventory> getInventoryForUpdate(long characterId) {
        throw new UnsupportedOperationException("inventory grants are written through the transaction's own DAO handle");
    }

    @Override
    public void updateInventory(long characterId, Inventory inventory) {
        throw new UnsupportedOperationException("inventory grants are written through the transaction's own DAO handle");
    }

    @Override
    public Optional<Integer> getUnspentStatPoints(long characterId) {
        Row row = rows.get(characterId);
        return Optional.ofNullable(row).map(Row::unspentStatPoints);
    }

    /**
     * Mirrors the real guarded write (Epic L): fails (0 rows) when the balance can't cover {@code spent},
     * otherwise replaces the stored stats and decrements the balance so a follow-up read reflects it.
     */
    @Override
    public int spendStatPoints(long characterId, Stats stats, int spent) {
        Row row = rows.get(characterId);
        if (row == null || row.unspentStatPoints() < spent) {
            return 0;
        }
        Character c = row.character();
        Character updated = new Character(c.id(), c.accountId(), c.name(), c.faction(), c.level(),
            c.experience(), c.maxHp(), c.maxMp(), c.maxStamina(), c.baseDef(), c.baseAtk(),
            stats, c.inventory(), c.loadout(), c.skillTree());
        rows.put(characterId, new Row(updated, row.accountId(), row.elo(), row.unspentStatPoints() - spent));
        return 1;
    }

    /**
     * Persists the whole loadout so a follow-up {@code getCharacters} reflects it — the loadout-save path
     * (system design §4.4/§16) is a plain overwrite, not a transactional grant, so the fake models it
     * directly rather than throwing the way the reward/skill-tree write paths do.
     */
    @Override
    public void updateLoadout(long characterId, Loadout loadout) {
        Row row = rows.get(characterId);
        if (row == null) {
            return;
        }
        Character c = row.character();
        Character updated = new Character(c.id(), c.accountId(), c.name(), c.faction(), c.level(),
            c.experience(), c.maxHp(), c.maxMp(), c.maxStamina(), c.baseDef(), c.baseAtk(),
            c.stats(), c.inventory(), loadout, c.skillTree());
        rows.put(characterId, new Row(updated, row.accountId(), row.elo(), row.unspentStatPoints()));
    }

    /** Skill-tree spend goes through the transaction's own DAO handle, never the fake's read models. */
    @Override
    public Optional<Map<String, Integer>> getSkillTree(long characterId) {
        throw new UnsupportedOperationException("skill-tree spends are written through the transaction's own DAO handle");
    }

    @Override
    public void updateSkillTree(long characterId, Map<String, Integer> skillTree) {
        throw new UnsupportedOperationException("skill-tree spends are written through the transaction's own DAO handle");
    }

    @Override
    public Optional<Integer> getSkillPoints(long characterId) {
        throw new UnsupportedOperationException("skill-tree spends are written through the transaction's own DAO handle");
    }

    @Override
    public int spendSkillPoints(long characterId, int cost) {
        throw new UnsupportedOperationException("skill-tree spends are written through the transaction's own DAO handle");
    }

    @Override
    public long createCharacter(CharacterEntity characterEntity) {
        throw new UnsupportedOperationException("not exercised by combat/matchmaking tests");
    }

    @Override
    public Optional<Integer> getBonusDailyBattles(long characterId) {
        return rows.containsKey(characterId) ? Optional.of(0) : Optional.empty();
    }

    @Override
    public void addBonusDailyBattles(long characterId, int delta) {
        throw new UnsupportedOperationException("not exercised by combat/matchmaking tests");
    }

    @Override
    public boolean deleteCharacter(long accountId, String name) {
        throw new UnsupportedOperationException("not exercised by combat/matchmaking tests");
    }

    @Override
    public int getCharacterCount(long accountId) {
        throw new UnsupportedOperationException("not exercised by combat/matchmaking tests");
    }
}
