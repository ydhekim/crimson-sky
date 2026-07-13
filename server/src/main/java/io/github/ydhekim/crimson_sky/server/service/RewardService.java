package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.RewardOutcome;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import org.jdbi.v3.core.Jdbi;

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
 * <p><b>C1 accumulates raw numbers only.</b> Exp is credited to {@code characters.experience} and read
 * back by nothing — there is no level-up threshold or stat-growth consequence anywhere in this
 * codebase, and this service does not invent one.
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

    private final Jdbi jdbi;
    private final CharacterService characterService;

    public RewardService(Jdbi jdbi, CharacterService characterService) {
        this.jdbi = jdbi;
        this.characterService = characterService;
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

        RewardOutcome outcome = computeRewards(result.won(), attackerElo.data(), opponentElo);
        long accountId = attacker.data().accountId();

        try {
            jdbi.useTransaction(handle -> {
                handle.attach(CharacterDao.class)
                    .addExperienceAndElo(result.characterId(), outcome.expDelta(), outcome.eloDelta());
                handle.attach(AccountDao.class)
                    .addGlobalCurrency(accountId, outcome.goldDelta());
                handle.attach(BattleHistoryDao.class).insert(
                    result.characterId(), result.opponentCharacterId(), result.opponentIsBot(),
                    outcome.goldDelta(), outcome.expDelta(), outcome.eloDelta());
            });
        } catch (Exception e) {
            log.error("Reward transaction rolled back for battle " + result.battleId() + " / character "
                + result.characterId() + " — the battle stands, but it paid nothing.", e);
            return RewardOutcome.none();
        }

        log.info("Battle " + result.battleId() + " paid character " + result.characterId()
            + ": " + outcome.goldDelta() + " gold, " + outcome.expDelta() + " exp, "
            + outcome.eloDelta() + " elo.");
        return outcome;
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

        return new RewardOutcome(goldDelta, expDelta, eloDelta);
    }
}
