package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.combat.BattleEngine;
import io.github.ydhekim.crimson_sky.combat.BattleParticipant;
import io.github.ydhekim.crimson_sky.combat.BattleSession;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.quest.QuestPeriods;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resolves a whole battle inside one request (system design §7, story B4), replacing the retired
 * live-queue pair {@code MatchmakingService}/{@code CombatService}. There is no queue, no session that
 * outlives the call, and no live opposing client — so nothing to register, time out, or leak.
 *
 * <p>Opponent selection, in order: a random persisted character within {@value #BASE_ELO_RANGE} Elo of
 * the requester (random among candidates, not closest — an always-closest pick makes matchups
 * predictable); failing that, any persisted character at all; failing that, a synthesized bot.
 * Widening happens immediately rather than after a timeout, because everything resolves in this call.
 *
 * <p>The bot is invisible to the client by construction: {@link AttackResult} carries
 * {@code opponentIsBot} for C1's {@code battle_history} row, and {@link AttackResult#toResponse()}
 * drops it. Nothing else here branches on bot-ness — same engine, same battle, same response shape.
 *
 * <p><b>Reward persistence is deliberately not here</b> (story C1): no Elo/Gold/Exp delta is applied
 * and no {@code battle_history} row is written. C1 wraps a call to {@link #attack} with that logic,
 * reading everything it needs off the returned {@link AttackResult}.
 */
public class AttackService {

    private static final Logger log = new Logger("AttackService", Logger.DEBUG);

    /** Preferred Elo band for a real opponent (system design §7). */
    static final int BASE_ELO_RANGE = 100;

    /** Base battles a character may fight per UTC day before the cap rejects an attack (system design §20). */
    private static final int BASE_DAILY_BATTLE_CAP = 5;

    /**
     * Correlation id for a battle that exists only for the life of one request. Not a database key and
     * not addressable by the client — it exists so a fight can be traced through the logs.
     */
    private final AtomicLong nextBattleId = new AtomicLong(1);

    private final CharacterService characterService;
    private final BotFactory botFactory;
    private final BattleHistoryDao battleHistoryDao;
    private final Random random;

    public AttackService(CharacterService characterService, BotFactory botFactory, BattleHistoryDao battleHistoryDao) {
        this(characterService, botFactory, battleHistoryDao, ThreadLocalRandom.current());
    }

    /** Test seam: a seeded {@link Random} fixes which candidate is picked from the pool. */
    AttackService(CharacterService characterService, BotFactory botFactory, BattleHistoryDao battleHistoryDao, Random random) {
        this.characterService = characterService;
        this.botFactory = botFactory;
        this.battleHistoryDao = battleHistoryDao;
        this.random = random;
    }

    /**
     * How many more battles {@code characterId} may fight today (system design §20), floored at 0. Call
     * this before {@link #attack}, not as part of it — "daily cap reached" is a distinct rejection reason
     * the client must be able to tell apart from attack()'s existing failure modes, so it stays a separate
     * pre-check rather than widening attack()'s return type.
     *
     * <p>A failed bonus lookup falls back to bonus 0 (base cap only) rather than blocking the request: by
     * the time this is called the handler has already confirmed the character is owned by the connection's
     * account, so a lookup failure here is a rare data race, not a reason to spuriously cap a player at 0.
     */
    public int remainingDailyBattles(long characterId) {
        int bonus = 0;
        ServiceResult<Integer> bonusResult = characterService.getBonusDailyBattles(characterId);
        if (bonusResult.success()) {
            bonus = bonusResult.data();
        }
        int battlesToday = battleHistoryDao.countBattlesSince(characterId, QuestPeriods.startOfToday());
        return Math.max(0, (BASE_DAILY_BATTLE_CAP + bonus) - battlesToday);
    }

    /** Ownership guardrail, delegated — the only check {@code AttackRequest} needs (system design §6). */
    public boolean isCharacterOwnedBy(long accountId, long characterId) {
        return characterService.isCharacterOwnedBy(accountId, characterId);
    }

    /**
     * Fights {@code characterId} against a chosen opponent and resolves the entire battle before
     * returning. The caller must have already confirmed the character belongs to the connection's
     * account.
     *
     * @return the full internal outcome, or empty when the attacking character can't be loaded
     */
    public Optional<AttackResult> attack(long characterId) {
        ServiceResult<Character> attacker = characterService.getCharacter(characterId);
        if (!attacker.success()) {
            log.info("Refusing attack: character " + characterId + " could not be loaded.");
            return Optional.empty();
        }

        ServiceResult<Integer> elo = characterService.getElo(characterId);
        if (!elo.success()) {
            log.info("Refusing attack: Elo lookup failed for character " + characterId + ".");
            return Optional.empty();
        }

        Opponent opponent = selectOpponent(characterId, elo.data());
        return Optional.of(resolveBattle(attacker.data(), opponent));
    }

    /** A chosen opponent plus the one fact the client must never learn about it. */
    private record Opponent(Character character, boolean isBot) {

        /** {@code null} for a bot — it has no row in {@code characters} (mirrors §8's nullable column). */
        Long persistedId() {
            return isBot ? null : character.id();
        }
    }

    /** Elo band → unbounded → bot, resolving entirely within this call (system design §7). */
    private Opponent selectOpponent(long characterId, int elo) {
        Optional<Character> inRange = pickRandom(
            characterService.findOpponentCandidates(characterId, elo, BASE_ELO_RANGE), characterId);
        if (inRange.isPresent()) {
            return new Opponent(inRange.get(), false);
        }

        Optional<Character> anyOpponent = pickRandom(
            characterService.findAllOpponentCandidates(characterId), characterId);
        if (anyOpponent.isPresent()) {
            log.info("No opponent within ±" + BASE_ELO_RANGE + " Elo of character " + characterId
                + "; widened to an unbounded range.");
            return new Opponent(anyOpponent.get(), false);
        }

        log.info("No persisted opponent available for character " + characterId + "; synthesizing a bot.");
        return new Opponent(botFactory.createBot(elo), true);
    }

    /**
     * A uniformly random candidate, or empty. Re-excludes the requester defensively: the DAO's query
     * already does, but "you cannot fight yourself" is a correctness rule of this service, not an
     * incidental property of one SQL string.
     */
    private Optional<Character> pickRandom(ServiceResult<List<Character>> candidates, long characterId) {
        if (!candidates.success()) {
            return Optional.empty();
        }
        List<Character> eligible = candidates.data().stream()
            .filter(candidate -> candidate.id() != characterId)
            .toList();
        return eligible.isEmpty()
            ? Optional.empty()
            : Optional.of(eligible.get(random.nextInt(eligible.size())));
    }

    /**
     * Builds a throwaway engine/session/participant pair and runs the battle to completion. Nothing is
     * registered anywhere: the battle is over by the time this returns.
     */
    private AttackResult resolveBattle(Character attacker, Opponent opponent) {
        long battleId = nextBattleId.getAndIncrement();

        Engine engine = new Engine();
        BattleSession session = new BattleSession(ThreadLocalRandom.current().nextLong());

        BattleParticipant attackerParticipant = BattleParticipant.fromCharacter(engine, attacker);
        BattleParticipant opponentParticipant = BattleParticipant.fromCharacter(engine, opponent.character());
        session.addParticipant(attackerParticipant);
        session.addParticipant(opponentParticipant);

        BattleEngine battleEngine = new BattleEngine(engine, session);
        BattleParticipant winner = battleEngine.runToCompletion();
        boolean won = winner == attackerParticipant;

        AttackResult result = new AttackResult(
            battleId, attacker.id(), opponent.persistedId(), opponent.character().name(),
            opponent.isBot(), won,
            battleEngine.turnHistoryOf(attackerParticipant));

        log.info("Battle " + battleId + ": character " + attacker.id() + " "
            + (won ? "defeated " : "lost to ") + opponent.character().name()
            + " over " + battleEngine.turnNumber() + " turns.");

        session.end();
        return result;
    }
}
