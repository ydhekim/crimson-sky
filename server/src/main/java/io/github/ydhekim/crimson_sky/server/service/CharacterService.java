package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.entity.CharacterEntity;

import java.util.List;
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
     * Loads a single character by id, without an owner scope — matchmaking (B1) needs to build a
     * {@code BattleParticipant} for the *opponent*, whose account is by definition not the caller's.
     * Callers acting on behalf of a connection must still run the ownership guardrail
     * ({@link CombatService#isCharacterOwnedBy}) on the caller's own character id first.
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

    /** Matchmaking rating for a character (story B1). Absent/failed lookups fail as a failure result. */
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
