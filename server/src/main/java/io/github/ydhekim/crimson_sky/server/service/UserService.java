package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;
import io.github.ydhekim.crimson_sky.server.database.entity.Account;
import io.github.ydhekim.crimson_sky.server.database.entity.User;

import java.util.Optional;

public class UserService {
    private static final Logger log = new Logger("UserService", Logger.DEBUG);
    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public ServiceResult<Account> loginTestUser(PlatformType platformType, String identityToken) {
        if (identityToken == null || identityToken.trim().isEmpty()) {
            log.info("Login failed: Invalid or empty identity token for platform " + platformType);
            return ServiceResult.failure(MessageCode.LOGIN_FAILED_INVALID_TOKEN);
        }

        try {
            Optional<User> existingUser = userDao.findUserByToken(platformType.name(), identityToken);

            Account account;
            if (existingUser.isPresent()) {
                account = userDao.findAccountByUserId(existingUser.get().id())
                    .orElseThrow(() -> new IllegalStateException("User exists but account is missing"));
                log.info("Existing test user logged in successfully. Account ID: " + account.id() + ", Platform: " + platformType);
            } else {
                account = userDao.createUserAndAccount(platformType, identityToken);
                log.info("Created new test user and account. Account ID: " + account.id() + ", Platform: " + platformType);
            }

            return ServiceResult.success(MessageCode.SUCCESS, account);
        } catch (Exception e) {
            log.error("Exception occurred during login for platform " + platformType + " with token " + identityToken, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }
}
