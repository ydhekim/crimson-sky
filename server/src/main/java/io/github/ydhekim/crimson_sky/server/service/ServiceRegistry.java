package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.database.dao.*;

public class ServiceRegistry {
    private final UserService userService;
    private final CharacterService characterService;
    private final AttackService attackService;
    private final RewardService rewardService;
    private final SkillTreeService skillTreeService;
    private final ShopService shopService;
    private final QuestService questService;
    private final LocalizationService localizationService;
    private final AchievementService achievementService;
    private final AccountService accountService;


    public ServiceRegistry(DatabaseManager dbManager) {
        UserDao userDao = dbManager.getJdbi().onDemand(UserDao.class);
        this.userService = new UserService(userDao);

        CharacterDao characterDao = dbManager.getJdbi().onDemand(CharacterDao.class);
        this.characterService = new CharacterService(characterDao);
        BattleHistoryDao battleHistoryDao = dbManager.getJdbi().onDemand(BattleHistoryDao.class);
        this.attackService = new AttackService(characterService, new BotFactory(), battleHistoryDao);

        // The odd one out: it takes the Jdbi itself, not an onDemand DAO. A reward spans `characters`,
        // `accounts` and `battle_history`, and onDemand proxies open a connection per call — so the only
        // way to get one transaction across all three is to attach the DAOs to a handle it owns.
        this.rewardService = new RewardService(dbManager.getJdbi(), characterService);

        // Same reason as RewardService: a learn/upgrade spans `characters` (skill points, skill tree,
        // inventory) and `accounts` (gold) atomically, so it needs the raw Jdbi, not onDemand proxies.
        this.skillTreeService = new SkillTreeService(dbManager.getJdbi(), characterService);

        // Same reason again: a repair or purchase spans `characters` (inventory) and `accounts` (gold)
        // atomically, so it needs the raw Jdbi, not onDemand proxies.
        this.shopService = new ShopService(dbManager.getJdbi(), characterService);

        // Same reason again (system design §19): a claim spans `quest_claims` and either
        // `accounts.global_currency` or `characters.inventory` atomically, so it needs the raw Jdbi, not
        // onDemand proxies. It attaches CharacterDao/AccountDao/BattleHistoryDao/QuestClaimDao to that Jdbi.
        this.questService = new QuestService(dbManager.getJdbi(), characterService);

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

    public SkillTreeService getSkillTreeService() {
        return skillTreeService;
    }

    public ShopService getShopService() {
        return shopService;
    }

    public QuestService getQuestService() {
        return questService;
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
