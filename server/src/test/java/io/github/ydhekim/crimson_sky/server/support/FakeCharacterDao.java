package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.common.model.Character;
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

    private record Row(Character character, long accountId, int elo) {
    }

    private final Map<Long, Row> rows = new LinkedHashMap<>();
    private boolean leakRequesterIntoCandidates;

    /** Registers {@code character} as owned by {@code accountId}, rated at {@code elo}. */
    public FakeCharacterDao with(Character character, long accountId, int elo) {
        rows.put(character.id(), new Row(character, accountId, elo));
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
    public void addExperienceAndElo(long characterId, long expDelta, int eloDelta) {
        throw new UnsupportedOperationException("rewards are written through the transaction's own DAO handle");
    }

    @Override
    public long createCharacter(CharacterEntity characterEntity) {
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
