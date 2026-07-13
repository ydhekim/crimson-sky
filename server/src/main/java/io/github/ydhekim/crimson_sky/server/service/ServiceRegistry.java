package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.database.dao.*;

public class ServiceRegistry {
    private final UserService userService;
    private final CharacterService characterService;
    private final AttackService attackService;
    private final RewardService rewardService;
    private final LocalizationService localizationService;
    private final AchievementService achievementService;
    private final AccountService accountService;


    public ServiceRegistry(DatabaseManager dbManager) {
        UserDao userDao = dbManager.getJdbi().onDemand(UserDao.class);
        this.userService = new UserService(userDao);

        CharacterDao characterDao = dbManager.getJdbi().onDemand(CharacterDao.class);
        this.characterService = new CharacterService(characterDao);
        this.attackService = new AttackService(characterService, new BotFactory());

        // The odd one out: it takes the Jdbi itself, not an onDemand DAO. A reward spans `characters`,
        // `accounts` and `battle_history`, and onDemand proxies open a connection per call — so the only
        // way to get one transaction across all three is to attach the DAOs to a handle it owns.
        this.rewardService = new RewardService(dbManager.getJdbi(), characterService);

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

    public AttackService getAttackService() {
        return attackService;
    }

    public RewardService getRewardService() {
        return rewardService;
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
