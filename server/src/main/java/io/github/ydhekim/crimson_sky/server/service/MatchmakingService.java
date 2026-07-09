package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.combat.BattleEngine;
import io.github.ydhekim.crimson_sky.combat.BattleParticipant;
import io.github.ydhekim.crimson_sky.combat.BattleSession;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.network.packet.MatchmakingFoundResponse;
import io.github.ydhekim.crimson_sky.server.combat.ActiveBattle;
import io.github.ydhekim.crimson_sky.server.combat.BattleSessionRegistry;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * The matchmaking queue (system design §7, story B1): an in-memory, transient list of characters
 * waiting for an opponent — no DB table, nothing survives a restart. On each incoming request the
 * queue is scanned for the closest Elo match; a pairing removes both entries, builds a live
 * {@link BattleSession}, and tells both connections about it.
 *
 * <p><b>Elo range and widening.</b> A pairing is allowed when the two entries' Elo differ by no more
 * than {@link #BASE_ELO_RANGE}; among all allowed candidates the <i>closest</i> Elo wins. An entry
 * that has waited at least {@link #WIDEN_AFTER_MILLIS} drops its range restriction entirely, so a
 * long-waiting player is matched with whoever shows up next rather than waiting forever. Both values
 * are first-guess placeholders (B1's acceptance criteria explicitly leave the timeout to be defined
 * when implementing) — expect them to move once real queue volume exists.
 *
 * <p><b>Widening is evaluated lazily</b>, when the next request scans the queue, rather than by a
 * background timer. With no other player queued there is nobody to widen *into*, so a timer would
 * only re-check an empty queue; the moment a candidate appears, the waiting entry's elapsed time is
 * what decides whether the range applies. This keeps the service free of its own thread.
 *
 * <p>All queue mutation is {@code synchronized}: KryoNet dispatches packets from its own network
 * thread, so two simultaneous requests must not both pair against the same waiting entry.
 */
public class MatchmakingService {

    private static final Logger log = new Logger("MatchmakingService", Logger.DEBUG);

    /** Maximum Elo gap for an ordinary pairing. */
    static final int BASE_ELO_RANGE = 100;

    /** After this long in the queue, an entry accepts any opponent (range restriction lifted). */
    static final long WIDEN_AFTER_MILLIS = 15_000L;

    private final CharacterService characterService;
    private final BattleSessionRegistry battleRegistry;
    private final LongSupplier clock;

    private final List<QueueEntry> queue = new ArrayList<>();

    public MatchmakingService(CharacterService characterService, BattleSessionRegistry battleRegistry) {
        this(characterService, battleRegistry, System::currentTimeMillis);
    }

    /** Test seam: an injectable clock so queue-widening can be exercised without sleeping. */
    MatchmakingService(CharacterService characterService, BattleSessionRegistry battleRegistry, LongSupplier clock) {
        this.characterService = characterService;
        this.battleRegistry = battleRegistry;
        this.clock = clock;
    }

    /** One character waiting for an opponent, with the Elo it was queued at and when it joined. */
    private record QueueEntry(GameConnection connection, long characterId, int elo, long enqueuedAtMillis) {
    }

    /**
     * Queues {@code characterId} for a match, pairing it immediately if a suitable opponent is already
     * waiting. The caller ({@code MatchmakingRequestHandler}) must have already validated that the
     * character belongs to {@code connection}'s account.
     *
     * @return the battle that was created if this request completed a pairing, otherwise {@code null}
     *     (the character is now waiting in the queue)
     */
    public synchronized ActiveBattle enqueue(GameConnection connection, long characterId) {
        if (isQueued(characterId)) {
            log.info("Ignoring duplicate matchmaking request for already-queued character " + characterId);
            return null;
        }

        ServiceResult<Integer> elo = characterService.getElo(characterId);
        if (!elo.success()) {
            log.info("Refusing to queue character " + characterId + ": Elo lookup failed ("
                + elo.code().name() + ").");
            return null;
        }

        long now = clock.getAsLong();
        QueueEntry newcomer = new QueueEntry(connection, characterId, elo.data(), now);
        QueueEntry opponent = findClosestMatch(newcomer, now);

        if (opponent == null) {
            queue.add(newcomer);
            log.info("Queued character " + characterId + " (Elo " + newcomer.elo() + "). Queue size: " + queue.size());
            return null;
        }

        queue.remove(opponent);
        return startBattle(opponent, newcomer);
    }

    /** Removes any queued entry belonging to {@code connection} (e.g. on disconnect or cancel). */
    public synchronized void dequeue(GameConnection connection) {
        queue.removeIf(entry -> entry.connection() == connection);
    }

    /** True while {@code characterId} is waiting in the queue. */
    public synchronized boolean isQueued(long characterId) {
        return queue.stream().anyMatch(entry -> entry.characterId() == characterId);
    }

    /** Number of characters currently waiting for an opponent. */
    public synchronized int queueSize() {
        return queue.size();
    }

    /**
     * The waiting entry with the smallest Elo gap that either side's range permits, or {@code null}
     * when nothing is close enough. An entry that has waited past {@link #WIDEN_AFTER_MILLIS} permits
     * any gap, so one patient player is enough to make a pairing happen.
     */
    private QueueEntry findClosestMatch(QueueEntry newcomer, long now) {
        QueueEntry best = null;
        int bestDiff = Integer.MAX_VALUE;

        for (QueueEntry candidate : queue) {
            if (candidate.connection() == newcomer.connection()) {
                continue; // one connection must never be matched against itself
            }
            int diff = Math.abs(candidate.elo() - newcomer.elo());
            if (diff > allowedEloRange(candidate, now) && diff > allowedEloRange(newcomer, now)) {
                continue;
            }
            if (diff < bestDiff) {
                best = candidate;
                bestDiff = diff;
            }
        }
        return best;
    }

    private int allowedEloRange(QueueEntry entry, long now) {
        boolean waitedLongEnough = now - entry.enqueuedAtMillis() >= WIDEN_AFTER_MILLIS;
        return waitedLongEnough ? Integer.MAX_VALUE : BASE_ELO_RANGE;
    }

    /**
     * Builds the live battle for a pairing: a throwaway Ashley {@link Engine}, a {@link BattleSession}
     * seeded fresh per battle (reproducible after the fact from the stored seed, but not fixed), one
     * {@link BattleParticipant} per side, and a {@link BattleEngine} over the two. Both connections
     * are then told the battle id and the *other* side's character id.
     *
     * <p>If either character fails to load, the pairing is abandoned and neither side is re-queued —
     * both clients are free to send another {@code MatchmakingRequest}.
     */
    private ActiveBattle startBattle(QueueEntry first, QueueEntry second) {
        ServiceResult<Character> firstCharacter = characterService.getCharacter(first.characterId());
        ServiceResult<Character> secondCharacter = characterService.getCharacter(second.characterId());
        if (!firstCharacter.success() || !secondCharacter.success()) {
            log.error("Abandoning pairing of characters " + first.characterId() + " / " + second.characterId()
                + ": character load failed. Both clients must re-queue.");
            return null;
        }

        Engine engine = new Engine();
        BattleSession session = new BattleSession(ThreadLocalRandom.current().nextLong());

        BattleParticipant firstParticipant = BattleParticipant.fromCharacter(engine, firstCharacter.data());
        BattleParticipant secondParticipant = BattleParticipant.fromCharacter(engine, secondCharacter.data());
        session.addParticipant(firstParticipant);
        session.addParticipant(secondParticipant);

        // Constructed after both participants are added: BattleEngine fixes priority order up front.
        BattleEngine battleEngine = new BattleEngine(engine, session);

        Map<Long, BattleParticipant> participants = new LinkedHashMap<>();
        participants.put(first.characterId(), firstParticipant);
        participants.put(second.characterId(), secondParticipant);
        ActiveBattle battle = battleRegistry.register(session, battleEngine, participants);

        log.info("Matched character " + first.characterId() + " (Elo " + first.elo() + ") with character "
            + second.characterId() + " (Elo " + second.elo() + ") in battle " + battle.battleId() + ".");

        first.connection().sendTCP(new MatchmakingFoundResponse(battle.battleId(), second.characterId()));
        second.connection().sendTCP(new MatchmakingFoundResponse(battle.battleId(), first.characterId()));
        return battle;
    }
}
