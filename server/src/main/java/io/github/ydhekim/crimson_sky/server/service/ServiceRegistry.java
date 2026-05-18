package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.database.dao.*;

public class ServiceRegistry {
    private final UserService userService;
    private final CharacterService characterService;
    private final LocalizationService localizationService;
    private final AchievementService achievementService;
    private final AccountService accountService;


    public ServiceRegistry(DatabaseManager dbManager) {
        UserDao userDao = dbManager.getJdbi().onDemand(UserDao.class);
        this.userService = new UserService(userDao);

        CharacterDao characterDao = dbManager.getJdbi().onDemand(CharacterDao.class);
        this.characterService = new CharacterService(characterDao);

        LocalizationDao localizationDao = dbManager.getJdbi().onDemand(LocalizationDao.class);
        this.localizationService = new LocalizationService(localizationDao);

        AchievementDao achievementDao = dbManager.getJdbi().onDemand(AchievementDao.class);
        this.achievementService = new AchievementService(achievementDao);

        AccountDao accountDao = dbManager.getJdbi().onDemand(AccountDao.class);
        this.accountService = new AccountService(accountDao);
    }

    public UserService getUserService() {
        return userService;
    }

    public CharacterService getCharacterService() {
        return characterService;
    }

    public LocalizationService getLocalizationService() {
        return localizationService;
    }

    public AchievementService getAchievementService() {
        return achievementService;
    }

    public AccountService getAccountService() {
        return accountService;
    }
}
