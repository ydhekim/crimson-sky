package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.server.database.dao.LocalizationDao;

import java.util.Map;

public class LocalizationService {
    private final LocalizationDao localizationDao;

    public LocalizationService(LocalizationDao localizationDao) {
        this.localizationDao = localizationDao;
    }

    public ServiceResult<Map<String, String>> getLanguageBundle(String langCode) {
        Map<String, String> translations = localizationDao.getTranslationsByLanguage(langCode);

        if (translations.isEmpty()) {
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }

        return ServiceResult.success(MessageCode.SUCCESS, translations);
    }
}
