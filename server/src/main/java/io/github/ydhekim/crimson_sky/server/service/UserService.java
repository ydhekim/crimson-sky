package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.server.achievement.AccountAchievementFacts;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;
import io.github.ydhekim.crimson_sky.server.database.entity.Account;
import io.github.ydhekim.crimson_sky.server.database.entity.User;
import org.jdbi.v3.core.Jdbi;

import java.util.Optional;

public class UserService {
    private static final Logger log = new Logger("UserService", Logger.DEBUG);
    private final UserDao userDao;

    /**
     * The one genuinely standalone achievement checkpoint (system design §22): account creation isn't a
     * consequence of a battle, so account-scope achievements are evaluated here in their own small
     * transaction, never inside a reward.
     */
    private final Jdbi jdbi;
    private final AchievementUnlockService achievementUnlockService;

    public UserService(UserDao userDao, Jdbi jdbi, AchievementUnlockService achievementUnlockService) {
        this.userDao = userDao;
        this.jdbi = jdbi;
        this.achievementUnlockService = achievementUnlockService;
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
                Account created = userDao.createUserAndAccount(platformType, identityToken);
                account = created;
                log.info("Created new test user and account. Account ID: " + created.id() + ", Platform: " + platformType);

                // A failure here must not fail the login — the account already exists, mirroring
                // RewardService's "the underlying thing already happened, don't let a side-effect's failure
                // hide it" posture (§22). The unlock is discovered on the next achievement read regardless.
                // `created` (not the reassigned `account`) is the effectively-final capture the lambda needs.
                try {
                    jdbi.useTransaction(handle -> achievementUnlockService.evaluateAccountAchievements(
                        handle, created.id(), new AccountAchievementFacts(created.createdAt())));
                } catch (Exception e) {
                    log.error("Account-creation achievement evaluation failed for account " + created.id()
                        + " — account was still created successfully.", e);
                }
            }

            return ServiceResult.success(MessageCode.SUCCESS, account);
        } catch (Exception e) {
            log.error("Exception occurred during login for platform " + platformType, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }
}
