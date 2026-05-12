package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;
import io.github.ydhekim.crimson_sky.server.database.entity.Account;
import io.github.ydhekim.crimson_sky.server.database.entity.User;

import java.util.Optional;

public class UserService {
    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public ServiceResult<Account> loginTestUser(PlatformType platformType, String identityToken) {
        if (identityToken == null || identityToken.trim().isEmpty()) {
            return ServiceResult.failure(MessageCode.LOGIN_FAILED_INVALID_TOKEN);
        }

        try {
            Optional<User> existingUser = userDao.findUserByToken(platformType.name(), identityToken);

            Account account;
            if (existingUser.isPresent()) {
                account = userDao.findAccountByUserId(existingUser.get().id())
                    .orElseThrow(() -> new IllegalStateException("User exists but account is missing"));
                System.out.println("Existing test user logged in. Account ID: " + account.id());
            } else {
                account = userDao.createUserAndAccount(platformType, identityToken);
                System.out.println("Created new test user and account. Account ID: " + account.id());
            }

            return ServiceResult.success(MessageCode.SUCCESS, account);
        } catch (Exception e) {
            e.printStackTrace();
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }
}
