package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.common.model.AccountSettings;

public class AccountService {
    private static final Logger log = new Logger("AccountService", Logger.DEBUG);
    private final AccountDao accountDao;
    private final ObjectMapper objectMapper;

    public AccountService(AccountDao accountDao, ObjectMapper objectMapper) {
        this.accountDao = accountDao;
        this.objectMapper = objectMapper;
    }

    public ServiceResult<Void> saveAccountSettings(long accountId, AccountSettings accountSettings) {
        try {
            String settingsJson = objectMapper.writeValueAsString(accountSettings);

            boolean success = accountDao.updateSettings(accountId, settingsJson);

            if (success) {
                log.info("Successfully saved account settings for Account ID " + accountId);
                return ServiceResult.success(MessageCode.SUCCESS, null);
            } else {
                log.info("Failed to save account settings for Account ID "+ accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
        } catch (Exception e) {
            log.info("Failed to save account settings for Account ID "+ accountId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }
}
