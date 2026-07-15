package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.RewardOutcome;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import org.jdbi.v3.core.Jdbi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Applies the Elo/Gold/Exp payout for an already-resolved attack and records it in
 * {@code battle_history} (story C1, system design §8/§8.1). Wraps {@link AttackService} rather than
 * living inside it: nothing here influences how a battle is fought, only what it pays.
 *
 * <p><b>Only the attacker is rewarded</b>, win or lose. An async attack is one-sided — the opponent
 * (real or bot) is a persisted snapshot that never consented to the fight, so its Gold/Exp/Elo are
 * never written, and it gets no {@code battle_history} row of its own.
 *
 * <p><b>Why the raw {@link Jdbi}, not the other services:</b> a reward spans two tables
 * ({@code characters} for Exp/Elo, {@code accounts} for Gold) plus the history insert.
 * {@code CharacterService}/{@code AccountService} wrap {@code onDemand()} DAO proxies, where every
 * method call takes its own connection — calling them in sequence would leave a partially applied
 * reward behind if the second write failed. All three writes here go through DAOs attached to one
 * {@link Jdbi#useTransaction} handle, so they commit or roll back together.
 *
 * <p><b>Epic L layers progression on top of the C1 currencies.</b> Exp still accumulates in
 * {@code characters.experience}, but is now also read against the level curve (system design §15): a
 * battle can push a character across one or more level thresholds, granting stat points, always grants
 * per-battle skill points, and — on crossing a 10/20/30/40/50 milestone — can roll a bonus item into the
 * attacker's inventory. All of it commits inside the same transaction as the currency writes.
 */
public class RewardService {

    private static final Logger log = new Logger("RewardService", Logger.DEBUG);

    /** Elo K-factor: the maximum rating a single battle can move (system design §8.1). */
    static final int K_FACTOR = 32;

    static final int WIN_BASE_GOLD = 25;
    static final int WIN_BASE_EXP = 50;

    /** Consolation payout on a loss — a flat amount, with no Elo-gap bonus term (system design §8.1). */
    static final int LOSS_GOLD = 5;
    static final int LOSS_EXP = 10;

    /** Skill points, per battle, win/loss branch shape mirroring gold/exp — no Elo-gap term (§15). */
    static final int WIN_SKILL_POINTS = 3;
    static final int LOSS_SKILL_POINTS = 1;

    /** Stat points granted per level gained (system design §15). */
    static final int STAT_POINTS_PER_LEVEL = 3;

    /** Hard level cap — no character advances past it regardless of exp (system design §15). */
    static final int LEVEL_CAP = 50;

    /** First-pass chance of a bonus reward on crossing each 10/20/30/40/50 milestone (system design §15). */
    static final double BONUS_ROLL_CHANCE = 0.10;

    /**
     * The curated bonus-reward table (system design §15): the three starter weapons, duplicated from
     * {@code BotFactory}'s constants rather than reached into (they are private there, and Epic E will
     * make content data-driven for both anyway). Pets and skill-restoration scrolls are named in §15 as
     * future table entries but have no acquisition mechanic in code yet, so this v1.0 pass grants weapons
     * only — no placeholder item types invented ahead of the epics that define them.
     */
    private static final Weapon TWIN_DAGGERS = new Weapon(1L, "Twin Daggers", "", Rarity.COMMON, 2f, 8, 18, 8);
    private static final Weapon STEEL_LONGSWORD = new Weapon(2L, "Steel Longsword", "", Rarity.UNCOMMON, 15f, 12, 28, 15);
    private static final Weapon WARHAMMER = new Weapon(3L, "Warhammer", "", Rarity.RARE, 40f, 15, 45, 25);
    private static final List<Weapon> BONUS_WEAPONS = List.of(TWIN_DAGGERS, STEEL_LONGSWORD, WARHAMMER);

    private final Jdbi jdbi;
    private final CharacterService characterService;
    private final Random random;

    public RewardService(Jdbi jdbi, CharacterService characterService) {
        this(jdbi, characterService, new Random());
    }

    /** Test seam: a seeded/stubbed {@link Random} makes the every-10 milestone roll reproducible. */
    public RewardService(Jdbi jdbi, CharacterService characterService, Random random) {
        this.jdbi = jdbi;
        this.characterService = characterService;
        this.random = random;
    }

    /**
     * Computes and atomically applies the rewards for the attacker's side of an already-resolved attack.
     *
     * <p>The battle is over by the time this runs, so a persistence failure must not cost the player the
     * fight they just watched: any error is logged loudly (character id, battle id) and reported as a
     * zero {@link RewardOutcome#none() payout} rather than propagating and dropping the whole attack
     * response. A silent zero-reward battle is a bug to notice in the logs, not a reason to hide a
     * battle that already happened.
     */
    public RewardOutcome applyRewards(AttackResult result) {
        ServiceResult<Character> attacker = characterService.getCharacter(result.characterId());
        if (!attacker.success()) {
            log.error("No reward applied for battle " + result.battleId() + ": character "
                + result.characterId() + " could not be loaded.");
            return RewardOutcome.none();
        }

        ServiceResult<Integer> attackerElo = characterService.getElo(result.characterId());
        if (!attackerElo.success()) {
            log.error("No reward applied for battle " + result.battleId() + ": Elo lookup failed for character "
                + result.characterId() + ".");
            return RewardOutcome.none();
        }

        Integer opponentElo = opponentElo(result, attackerElo.data());
        if (opponentElo == null) {
            log.error("No reward applied for battle " + result.battleId() + ": Elo lookup failed for opponent "
                + result.opponentCharacterId() + " of character " + result.characterId() + ".");
            return RewardOutcome.none();
        }

        RewardOutcome base = computeRewards(result.won(), attackerElo.data(), opponentElo);
        long accountId = attacker.data().accountId();

        // Leveling (L1): loop from the character's current level over the new cumulative experience,
        // granting stat points per level gained (system design §15). `experience`/`level` come off the
        // record loaded above; the SQL write below re-derives the new totals atomically.
        int currentLevel = attacker.data().level();
        long newExperience = attacker.data().experience() + base.expDelta();
        int newLevel = levelAfter(currentLevel, newExperience);
        int levelsGained = newLevel - currentLevel;
        int statPointsGained = STAT_POINTS_PER_LEVEL * levelsGained;

        // Every-10 milestone bonus roll (L3): weapons to append to the attacker's inventory, if any rolls
        // hit. Rolled here (not inside the transaction) so the whole outcome is decided before any write.
        List<Weapon> bonusWeapons = rollMilestoneBonus(currentLevel, newLevel, random);
        String bonusRewardGranted = bonusWeapons.isEmpty()
            ? null : bonusWeapons.get(bonusWeapons.size() - 1).name();

        RewardOutcome outcome = new RewardOutcome(
            base.goldDelta(), base.expDelta(), base.eloDelta(),
            base.skillPointsGained(), levelsGained, statPointsGained, bonusRewardGranted);

        try {
            jdbi.useTransaction(handle -> {
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                characterDao.applyBattleProgress(result.characterId(), outcome.expDelta(), outcome.eloDelta(),
                    newLevel, outcome.statPointsGained(), outcome.skillPointsGained());
                handle.attach(AccountDao.class)
                    .addGlobalCurrency(accountId, outcome.goldDelta());
                handle.attach(BattleHistoryDao.class).insert(
                    result.characterId(), result.opponentCharacterId(), result.opponentIsBot(),
                    outcome.goldDelta(), outcome.expDelta(), outcome.eloDelta());

                // Only touch the inventory column when a bonus actually fired — a read-modify-write under
                // the row lock, in the same transaction, so a granted item commits or rolls back with the
                // rest of the reward (system design §15, and the C2 write-path exception).
                if (!bonusWeapons.isEmpty()) {
                    grantBonusWeapons(characterDao, result.characterId(), bonusWeapons);
                }
            });
        } catch (Exception e) {
            log.error("Reward transaction rolled back for battle " + result.battleId() + " / character "
                + result.characterId() + " — the battle stands, but it paid nothing.", e);
            return RewardOutcome.none();
        }

        log.info("Battle " + result.battleId() + " paid character " + result.characterId()
            + ": " + outcome.goldDelta() + " gold, " + outcome.expDelta() + " exp, "
            + outcome.eloDelta() + " elo, " + outcome.skillPointsGained() + " skill points"
            + (levelsGained > 0 ? ", +" + levelsGained + " level(s) → " + newLevel + " (+"
                + statPointsGained + " stat points)" : "")
            + (bonusRewardGranted != null ? ", bonus reward: " + bonusRewardGranted : "") + ".");
        return outcome;
    }

    /**
     * Total cumulative {@code characters.experience} needed to have reached {@code level}. 8×level²
     * anchored so level 1 needs 0 (not 8) cumulative exp — matches §15's own worked example ("24 exp for
     * level 1→2"), which a literal reading of its {@code 8×L²} statement wouldn't. By construction the
     * increment {@code expNeededForLevel(L+1) − expNeededForLevel(L)} equals {@code 8×(2L+1)} (24 for
     * L=1), the growth formula §15 actually trusts.
     */
    static long expNeededForLevel(int level) {
        return 8L * level * level - 8L;
    }

    /**
     * The level a character ends at given its new cumulative {@code experience}, looping so a single
     * battle's exp can cross more than one threshold (system design §15). Never advances past
     * {@link #LEVEL_CAP} regardless of how much exp is banked.
     */
    static int levelAfter(int currentLevel, long newExperience) {
        int level = currentLevel;
        while (level < LEVEL_CAP && newExperience >= expNeededForLevel(level + 1)) {
            level++;
        }
        return level;
    }

    /** A level is a bonus-roll milestone when it is one of 10/20/30/40/50 (system design §15). */
    static boolean isMilestone(int level) {
        return level > 0 && level <= LEVEL_CAP && level % 10 == 0;
    }

    /**
     * Rolls the every-10 bonus for each milestone crossed while advancing {@code fromLevel → toLevel}
     * (system design §15). A multi-level jump can cross more than one, so each is an independent 10%
     * roll; a level-up that crosses no multiple of 10 rolls nothing. Returns the weapons to grant,
     * possibly empty. Pure (takes its {@link Random}) so the roll rate is unit-testable without a battle.
     */
    static List<Weapon> rollMilestoneBonus(int fromLevel, int toLevel, Random random) {
        List<Weapon> granted = new ArrayList<>();
        for (int level = fromLevel + 1; level <= toLevel; level++) {
            if (isMilestone(level) && random.nextDouble() < BONUS_ROLL_CHANCE) {
                granted.add(BONUS_WEAPONS.get(random.nextInt(BONUS_WEAPONS.size())));
            }
        }
        return granted;
    }

    /**
     * Appends granted weapons onto the attacker's stored inventory inside the reward transaction. Tolerates
     * a {@code null} weapons array — every character created to date persists inventory in the null form
     * (creation stores {@code new Inventory(null, null, null)}), so this grant is the first code to write a
     * real array and must not NPE on the existing shape.
     */
    private void grantBonusWeapons(CharacterDao characterDao, long characterId, List<Weapon> bonusWeapons) {
        Inventory inventory = characterDao.getInventoryForUpdate(characterId)
            .orElseGet(() -> new Inventory(new Array<>(), new Array<>(), new Array<>()));
        Array<Weapon> weapons = inventory.weapons() != null ? inventory.weapons() : new Array<>();
        for (Weapon weapon : bonusWeapons) {
            weapons.add(weapon);
        }
        characterDao.updateInventory(characterId,
            new Inventory(weapons, inventory.skills(), inventory.pets()));
    }

    /**
     * A bot's Elo is defined as the attacker's own (system design §8.1), matching how {@code BotFactory}
     * already calibrates the bot's stat budget to the attacker's rating: the fight is a coinflip on
     * paper, so it pays the flat base with no Elo-gap bonus and moves the rating by the full ±K/2 on a
     * surprise. No separate bot-Elo tracking exists or is needed.
     *
     * @return the opponent's rating, or {@code null} when a real opponent's rating can't be read
     */
    private Integer opponentElo(AttackResult result, int attackerElo) {
        if (result.opponentIsBot()) {
            return attackerElo;
        }
        ServiceResult<Integer> elo = characterService.getElo(result.opponentCharacterId());
        return elo.success() ? elo.data() : null;
    }

    /**
     * The payout formulas, verbatim from system design §8.1 — first-pass numbers, explicitly awaiting a
     * tuning pass once Gold has a sink and Exp has a level curve to validate against. Pure and package
     * -private so the numbers can be tested without a database.
     *
     * <p>The standard Elo expectation produces a negative delta on a loss on its own, so the loss branch
     * only special-cases Gold/Exp. The win-side bonus terms are floored at zero: beating a lower-rated
     * opponent pays the base, never less.
     *
     * <p>Carries the flat per-battle skill points (L2, {@code won ? 3 : 1}), which have no Elo-gap term.
     * The level fields are left zero here — leveling depends on the character's current level/experience,
     * which this pure numbers method deliberately doesn't see; {@code applyRewards} fills them in.
     */
    static RewardOutcome computeRewards(boolean won, int attackerElo, int opponentElo) {
        int eloGap = opponentElo - attackerElo;

        double expectedScore = 1.0 / (1.0 + Math.pow(10.0, eloGap / 400.0));
        int eloDelta = (int) Math.round(K_FACTOR * ((won ? 1.0 : 0.0) - expectedScore));

        int goldDelta = won
            ? WIN_BASE_GOLD + Math.max(0, (int) Math.round(eloGap * 0.1))
            : LOSS_GOLD;
        long expDelta = won
            ? WIN_BASE_EXP + Math.max(0, Math.round(eloGap * 0.2))
            : LOSS_EXP;
        int skillPointsGained = won ? WIN_SKILL_POINTS : LOSS_SKILL_POINTS;

        return new RewardOutcome(goldDelta, expDelta, eloDelta, skillPointsGained, 0, 0, null);
    }
}
