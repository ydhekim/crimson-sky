package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.database.dao.AchievementDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.dao.LocalizationDao;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;

public class ServiceRegistry {
    private final UserService userService;
    private final CharacterService characterService;
    private final LocalizationService localizationService;
    private final AchievementService achievementService;

    public ServiceRegistry(DatabaseManager dbManager) {
        UserDao userDao = dbManager.getJdbi().onDemand(UserDao.class);
        this.userService = new UserService(userDao);

        CharacterDao characterDao = dbManager.getJdbi().onDemand(CharacterDao.class);
        this.characterService = new CharacterService(characterDao);

        LocalizationDao localizationDao = dbManager.getJdbi().onDemand(LocalizationDao.class);
        this.localizationService = new LocalizationService(localizationDao);

        AchievementDao achievementDao = dbManager.getJdbi().onDemand(AchievementDao.class);
        this.achievementService = new AchievementService(achievementDao);
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
}
