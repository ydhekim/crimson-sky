package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.entity.CharacterEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CharacterService {
    private static final Logger log = new Logger("CharacterService", Logger.DEBUG);
    private final CharacterDao characterDao;

    public CharacterService(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    public ServiceResult<List<Character>> getCharacters(long accountId) {
        try {
            List<Character> characters = characterDao.getCharactersByAccountId(accountId)
                .stream()
                .map(CharacterEntity::toCommonModel)
                .collect(Collectors.toList());
            log.info("Fetched " + characters.size() + " characters for account ID: " + accountId);
            return ServiceResult.success(MessageCode.SUCCESS, characters);
        } catch (Exception e) {
            log.error("Failed to fetch characters for account ID: " + accountId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * Ownership guardrail (system design §6/§13): true only when {@code characterId} belongs to
     * {@code accountId}. Fails closed — any DB error is logged and treated as "not owned", so a lookup
     * failure can never accidentally authorize an action on someone else's character.
     */
    public boolean isCharacterOwnedBy(long accountId, long characterId) {
        try {
            return characterDao.isOwnedByAccount(accountId, characterId);
        } catch (Exception e) {
            log.error("Ownership check failed for character " + characterId + " / account " + accountId, e);
            return false;
        }
    }

    /**
     * Opponent candidates within {@code ±eloRange} of {@code characterId}'s own rating, excluding
     * itself (story B4). Empty when nothing qualifies — the caller widens, then falls back to a bot.
     */
    public ServiceResult<List<Character>> findOpponentCandidates(long characterId, int elo, int eloRange) {
        try {
            return toCharacters(characterDao.findOpponentCandidatesInEloRange(
                characterId, elo - eloRange, elo + eloRange));
        } catch (Exception e) {
            log.error("Opponent candidate lookup failed for character ID: " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /** The unbounded-Elo widening step (story B4): any persisted character but the requester. */
    public ServiceResult<List<Character>> findAllOpponentCandidates(long characterId) {
        try {
            return toCharacters(characterDao.findAllOpponentCandidates(characterId));
        } catch (Exception e) {
            log.error("Unbounded opponent candidate lookup failed for character ID: " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    private ServiceResult<List<Character>> toCharacters(List<CharacterEntity> entities) {
        return ServiceResult.success(MessageCode.SUCCESS, entities.stream()
            .map(CharacterEntity::toCommonModel)
            .collect(Collectors.toList()));
    }

    /**
     * Loads a single character by id, without an owner scope — an attack (B4) needs to build a
     * {@code BattleParticipant} for the *opponent*, whose account is by definition not the caller's.
     * Callers acting on behalf of a connection must still run {@link #isCharacterOwnedBy} on the
     * caller's own character id first.
     */
    public ServiceResult<Character> getCharacter(long characterId) {
        try {
            return characterDao.findById(characterId)
                .map(entity -> ServiceResult.success(MessageCode.SUCCESS, entity.toCommonModel()))
                .orElseGet(() -> {
                    log.info("Character not found for ID: " + characterId);
                    return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
                });
        } catch (Exception e) {
            log.error("Failed to fetch character for ID: " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /** Opponent-selection rating for a character. Absent/failed lookups return a failure result. */
    public ServiceResult<Integer> getElo(long characterId) {
        try {
            return characterDao.getElo(characterId)
                .map(elo -> ServiceResult.success(MessageCode.SUCCESS, elo))
                .orElseGet(() -> {
                    log.info("Elo lookup found no character with ID: " + characterId);
                    return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
                });
        } catch (Exception e) {
            log.error("Elo lookup failed for character ID: " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    public ServiceResult<Long> createCharacter(long accountId, Character character) {
        try {
            if (characterDao.getCharacterCount(accountId) >= 3) {
                log.info("Character creation failed for account ID " + accountId + ": Maximum character slots reached.");
                return ServiceResult.failure(MessageCode.CHAR_MAX_SLOTS_REACHED);
            }

            if (characterDao.isNameTaken(character.name())) {
                log.info("Character creation failed for account ID " + accountId + ": Name '" + character.name() + "' is already taken.");
                return ServiceResult.failure(MessageCode.CHAR_NAME_TAKEN);
            }

            CharacterEntity newEntity = CharacterEntity.fromCommonModel(accountId, character);
            long newId = characterDao.createCharacter(newEntity);

            if (newId > 0) {
                log.info("Successfully created character '" + character.name() + "' (ID: " + newId + ") for account ID: " + accountId);
                return ServiceResult.success(MessageCode.CHAR_CREATE_SUCCESS, newId);
            } else {
                log.error("Failed to create character for account ID " + accountId + ". Database returned ID 0.");
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
        } catch (Exception e) {
            log.error("Exception occurred while creating character '" + character.name() + "' for account ID: " + accountId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * The merged stats and remaining balance after a successful stat-point spend (Epic L / §15). Kept as
     * a small server-side result so {@code CharacterService} stays free of network-packet types; the
     * handler folds this into an {@code AllocateStatPointsResponse}.
     */
    public record AllocateStatPointsResult(Stats newStats, int unspentStatPoints) {
    }

    /**
     * Spends stat points on behalf of {@code accountId} (Epic L / system design §15). Validates, in order:
     * ownership (the same guardrail as every character-scoped action, failing closed), that no delta
     * component is negative, that {@code sum(delta)} fits the unspent balance
     * ({@code STAT_POINTS_INSUFFICIENT}), and that no merged stat exceeds {@link Stats#MAX_STAT_VALUE}
     * ({@code STAT_CAP_EXCEEDED}). The merged write itself is atomic and guarded
     * ({@link CharacterDao#spendStatPoints}); if it reports zero rows the balance lost a race and this
     * still fails with {@code STAT_POINTS_INSUFFICIENT} rather than silently succeeding.
     *
     * <p>There is a small TOCTOU gap between reading the current stats/balance and the guarded write —
     * accepted deliberately, the same simplification durability's concurrency model takes (§17): one
     * account running two simultaneous spends for the same character is not a realistic case.
     */
    public ServiceResult<AllocateStatPointsResult> allocateStatPoints(long accountId, long characterId, Stats delta) {
        try {
            if (!isCharacterOwnedBy(accountId, characterId)) {
                log.info("Stat-point allocation rejected: character " + characterId
                    + " is not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            if (delta.hasNegativeComponent()) {
                log.info("Stat-point allocation rejected for character " + characterId
                    + ": a delta component was negative.");
                return ServiceResult.failure(MessageCode.STAT_POINTS_INSUFFICIENT);
            }

            Optional<CharacterEntity> entity = characterDao.findById(characterId);
            Optional<Integer> balance = characterDao.getUnspentStatPoints(characterId);
            if (entity.isEmpty() || balance.isEmpty()) {
                log.info("Stat-point allocation failed: character " + characterId + " could not be loaded.");
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            int spent = delta.total();
            if (spent > balance.get()) {
                log.info("Stat-point allocation rejected for character " + characterId + ": spend of "
                    + spent + " exceeds balance of " + balance.get() + ".");
                return ServiceResult.failure(MessageCode.STAT_POINTS_INSUFFICIENT);
            }

            Stats merged = entity.get().stats().plus(delta);
            if (merged.max() > Stats.MAX_STAT_VALUE) {
                log.info("Stat-point allocation rejected for character " + characterId
                    + ": a resulting stat would exceed the cap of " + Stats.MAX_STAT_VALUE + ".");
                return ServiceResult.failure(MessageCode.STAT_CAP_EXCEEDED);
            }

            if (characterDao.spendStatPoints(characterId, merged, spent) == 0) {
                log.info("Stat-point allocation lost a race on the balance for character " + characterId + ".");
                return ServiceResult.failure(MessageCode.STAT_POINTS_INSUFFICIENT);
            }

            log.info("Character " + characterId + " spent " + spent + " stat points; "
                + (balance.get() - spent) + " remaining.");
            return ServiceResult.success(MessageCode.SUCCESS,
                new AllocateStatPointsResult(merged, balance.get() - spent));
        } catch (Exception e) {
            log.error("Exception during stat-point allocation for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    public ServiceResult<Void> deleteCharacter(long accountId, String name) {
        try {
            boolean deleted = characterDao.deleteCharacter(accountId, name);

            if (deleted) {
                log.info("Successfully deleted character '" + name + "' for account ID: " + accountId);
                return ServiceResult.success(MessageCode.SUCCESS);
            } else {
                log.info("Failed to delete character '" + name + "' for account ID: " + accountId + " (Character may not exist or doesn't belong to the account).");
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
        } catch (Exception e) {
            log.error("Exception occurred while deleting character '" + name + "' for account ID: " + accountId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

}
