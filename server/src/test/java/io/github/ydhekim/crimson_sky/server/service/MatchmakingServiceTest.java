package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.network.packet.MatchmakingFoundResponse;
import io.github.ydhekim.crimson_sky.server.combat.ActiveBattle;
import io.github.ydhekim.crimson_sky.server.combat.BattleSessionRegistry;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.FakeGameConnection;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story B1: the matchmaking queue pairs by closest Elo within a range, widens that range for an entry
 * that has waited past the timeout, otherwise leaves the character queued — and a pairing removes both
 * sides from the queue while telling both connections about the battle.
 */
class MatchmakingServiceTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long ACCOUNT_C = 30L;

    private final long[] now = {0L};
    private FakeCharacterDao characterDao;
    private BattleSessionRegistry battleRegistry;
    private MatchmakingService matchmaking;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        characterDao = new FakeCharacterDao()
            .with(CombatFixtures.character(1L, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(2L, ACCOUNT_B, "Boran"), ACCOUNT_B, 1050)
            .with(CombatFixtures.character(3L, ACCOUNT_C, "Cem"), ACCOUNT_C, 1400);
        battleRegistry = new BattleSessionRegistry();
        matchmaking = new MatchmakingService(new CharacterService(characterDao), battleRegistry, () -> now[0]);
    }

    @Test
    void pairsTwoCharactersWithinTheEloRange() {
        FakeGameConnection first = FakeGameConnection.authenticated(1, ACCOUNT_A);
        FakeGameConnection second = FakeGameConnection.authenticated(2, ACCOUNT_B);

        assertNull(matchmaking.enqueue(first, 1L), "first request has nobody to pair with yet");
        ActiveBattle battle = matchmaking.enqueue(second, 2L);

        assertNotNull(battle, "an Elo gap of 50 is inside the ±100 range");
        assertEquals(1, battleRegistry.activeCount(), "the pairing registered exactly one battle");

        // Each side is told the battle id and the *other* character's id.
        MatchmakingFoundResponse toFirst = first.onlySentPacket(MatchmakingFoundResponse.class);
        MatchmakingFoundResponse toSecond = second.onlySentPacket(MatchmakingFoundResponse.class);
        assertEquals(battle.battleId(), toFirst.battleId());
        assertEquals(battle.battleId(), toSecond.battleId());
        assertEquals(2L, toFirst.opponentCharacterId());
        assertEquals(1L, toSecond.opponentCharacterId());
    }

    @Test
    void pairedCharactersLeaveTheQueue() {
        matchmaking.enqueue(FakeGameConnection.authenticated(1, ACCOUNT_A), 1L);
        matchmaking.enqueue(FakeGameConnection.authenticated(2, ACCOUNT_B), 2L);

        assertEquals(0, matchmaking.queueSize(), "both entries are removed once matched");
        assertFalse(matchmaking.isQueued(1L));
        assertFalse(matchmaking.isQueued(2L));
    }

    @Test
    void queuesWhenNoOpponentIsWithinTheEloRange() {
        FakeGameConnection first = FakeGameConnection.authenticated(1, ACCOUNT_A);
        FakeGameConnection farApart = FakeGameConnection.authenticated(3, ACCOUNT_C);

        matchmaking.enqueue(first, 1L);
        assertNull(matchmaking.enqueue(farApart, 3L), "a 400-point Elo gap is outside the ±100 range");

        assertEquals(2, matchmaking.queueSize(), "both characters wait rather than being paired");
        assertEquals(0, battleRegistry.activeCount());
        assertTrue(first.sentNothing());
        assertTrue(farApart.sentNothing());
    }

    @Test
    void widensTheEloRangeForAnEntryThatHasWaitedPastTheTimeout() {
        FakeGameConnection waiting = FakeGameConnection.authenticated(1, ACCOUNT_A);
        matchmaking.enqueue(waiting, 1L);

        now[0] = MatchmakingService.WIDEN_AFTER_MILLIS; // the queued entry has now waited long enough

        FakeGameConnection farApart = FakeGameConnection.authenticated(3, ACCOUNT_C);
        ActiveBattle battle = matchmaking.enqueue(farApart, 3L);

        assertNotNull(battle, "a long-waiting entry accepts an opponent outside the base range");
        assertEquals(0, matchmaking.queueSize());
        assertNotNull(waiting.onlySentPacket(MatchmakingFoundResponse.class));
        assertNotNull(farApart.onlySentPacket(MatchmakingFoundResponse.class));
    }

    @Test
    void doesNotWidenBeforeTheTimeoutElapses() {
        matchmaking.enqueue(FakeGameConnection.authenticated(1, ACCOUNT_A), 1L);

        now[0] = MatchmakingService.WIDEN_AFTER_MILLIS - 1;

        assertNull(matchmaking.enqueue(FakeGameConnection.authenticated(3, ACCOUNT_C), 3L),
            "one millisecond short of the timeout, the base range still applies");
        assertEquals(2, matchmaking.queueSize());
    }

    @Test
    void pairsWithTheClosestEloAmongSeveralCandidates() {
        // Two candidates wait at Elo 1000 and 1150 — 150 apart, so they cannot pair with each other.
        // A newcomer at 1100 is inside the ±100 range of both, so the choice is purely "closest Elo".
        // The closer candidate (1150) is deliberately the *later* arrival, so FIFO would pick wrong.
        characterDao.with(CombatFixtures.character(4L, 40L, "Deniz"), 40L, 1100);
        characterDao.with(CombatFixtures.character(5L, 50L, "Efe"), 50L, 1150);
        matchmaking.enqueue(FakeGameConnection.authenticated(1, ACCOUNT_A), 1L); // Elo 1000, gap 100
        matchmaking.enqueue(FakeGameConnection.authenticated(5, 50L), 5L);       // Elo 1150, gap 50
        assertEquals(2, matchmaking.queueSize(), "the two candidates are too far apart to pair");

        FakeGameConnection newcomer = FakeGameConnection.authenticated(4, 40L);
        matchmaking.enqueue(newcomer, 4L);

        assertEquals(5L, newcomer.onlySentPacket(MatchmakingFoundResponse.class).opponentCharacterId(),
            "the closest Elo wins, not the longest-waiting entry");
        assertTrue(matchmaking.isQueued(1L), "the further candidate stays in the queue");
    }

    @Test
    void ignoresADuplicateRequestForAnAlreadyQueuedCharacter() {
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);
        matchmaking.enqueue(connection, 1L);
        matchmaking.enqueue(connection, 1L);

        assertEquals(1, matchmaking.queueSize(), "the character is queued once, not twice");
        assertEquals(0, battleRegistry.activeCount());
    }

    @Test
    void neverMatchesOneConnectionAgainstItself() {
        // Same connection queueing two of its own characters, both well inside the Elo range.
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);
        characterDao.with(CombatFixtures.character(5L, ACCOUNT_A, "Efe"), ACCOUNT_A, 1010);

        matchmaking.enqueue(connection, 1L);
        assertNull(matchmaking.enqueue(connection, 5L), "a connection cannot fight itself");
        assertEquals(2, matchmaking.queueSize());
    }

    @Test
    void refusesToQueueACharacterWhoseEloCannotBeRead() {
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        assertNull(matchmaking.enqueue(connection, 999L), "unknown character → no Elo → not queued");
        assertEquals(0, matchmaking.queueSize());
    }
}
