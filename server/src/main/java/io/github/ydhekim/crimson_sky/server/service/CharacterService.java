package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.entity.CharacterEntity;

import java.util.List;
import java.util.stream.Collectors;

public class CharacterService {
    private final CharacterDao characterDao;

    public CharacterService(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    public ServiceResult<List<Character>> getCharacters(long accountId) {
        List<Character> characters = characterDao.getCharactersByAccountId(accountId)
            .stream()
            .map(CharacterEntity::toCommonModel)
            .collect(Collectors.toList());
        return ServiceResult.success(MessageCode.SUCCESS, characters);
    }

    public ServiceResult<Long> createCharacter(long accountId, Character character) {
        if (characterDao.getCharacterCount(accountId) >= 3) {
            return ServiceResult.failure(MessageCode.CHAR_MAX_SLOTS_REACHED);
        }

        if (characterDao.isNameTaken(character.name())) {
            return ServiceResult.failure(MessageCode.CHAR_NAME_TAKEN);
        }

        CharacterEntity newEntity = CharacterEntity.fromCommonModel(accountId, character);
        long newId = characterDao.createCharacter(newEntity);

        if (newId > 0) {
            return ServiceResult.success(MessageCode.CHAR_CREATE_SUCCESS, newId);
        } else {
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    public ServiceResult<Void> deleteCharacter(long accountId, String name) {
        boolean deleted = characterDao.deleteCharacter(accountId, name);

        if (deleted) {
            return ServiceResult.success(MessageCode.SUCCESS);
        } else {
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

}
