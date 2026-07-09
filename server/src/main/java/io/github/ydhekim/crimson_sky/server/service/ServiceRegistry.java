package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.server.combat.BattleSessionRegistry;
import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.database.dao.*;

public class ServiceRegistry {
    private final UserService userService;
    private final CharacterService characterService;
    private final CombatService combatService;
    private final MatchmakingService matchmakingService;
    private final LocalizationService localizationService;
    private final AchievementService achievementService;
    private final AccountService accountService;


    public ServiceRegistry(DatabaseManager dbManager) {
        UserDao userDao = dbManager.getJdbi().onDemand(UserDao.class);
        this.userService = new UserService(userDao);

        // One registry of live battles, shared by the service that creates them (matchmaking) and
        // the one that ticks them (combat).
        BattleSessionRegistry battleRegistry = new BattleSessionRegistry();

        CharacterDao characterDao = dbManager.getJdbi().onDemand(CharacterDao.class);
        this.characterService = new CharacterService(characterDao);
        this.combatService = new CombatService(characterDao, battleRegistry);
        this.matchmakingService = new MatchmakingService(characterService, battleRegistry);

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

    public CombatService getCombatService() {
        return combatService;
    }

    public MatchmakingService getMatchmakingService() {
        return matchmakingService;
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
