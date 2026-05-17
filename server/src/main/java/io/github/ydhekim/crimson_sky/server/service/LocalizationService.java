package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.server.database.dao.LocalizationDao;

import java.util.Map;

public class LocalizationService {
    private static final Logger log = new Logger("LocalizationService", Logger.DEBUG);
    private final LocalizationDao localizationDao;

    public LocalizationService(LocalizationDao localizationDao) {
        this.localizationDao = localizationDao;
    }

    public ServiceResult<Map<String, String>> getLanguageBundle(String langCode) {
        try {
            Map<String, String> translations = localizationDao.getTranslationsByLanguage(langCode);

            if (translations.isEmpty()) {
                log.info("Requested language bundle '" + langCode + "' returned 0 translations.");
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            log.info("Successfully loaded " + translations.size() + " translations for language code: " + langCode);
            return ServiceResult.success(MessageCode.SUCCESS, translations);
        } catch (Exception e) {
            log.error("Failed to load language bundle for code: " + langCode, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }
}
