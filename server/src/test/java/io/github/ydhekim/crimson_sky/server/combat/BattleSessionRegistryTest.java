package io.github.ydhekim.crimson_sky.server.combat;

import com.badlogic.ashley.core.Engine;
import io.github.ydhekim.crimson_sky.combat.BattleEngine;
import io.github.ydhekim.crimson_sky.combat.BattleParticipant;
import io.github.ydhekim.crimson_sky.combat.BattleSession;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story B1 / system design §7: register → look up → end → look up again returns nothing. Ending a
 * battle must both release the session's participants and evict it, so a finished battle can never
 * be ticked again and nothing leaks.
 */
class BattleSessionRegistryTest {

    private final BattleSessionRegistry registry = new BattleSessionRegistry();

    @BeforeEach
    void installHeadlessGdx() {
        HeadlessGdx.install();
    }

    private ActiveBattle registerBattle(long firstCharacterId, long secondCharacterId) {
        Engine engine = new Engine();
        BattleSession session = new BattleSession(42L);

        BattleParticipant first = BattleParticipant.fromCharacter(
            engine, CombatFixtures.character(firstCharacterId, 10L, "Ayla"));
        BattleParticipant second = BattleParticipant.fromCharacter(
            engine, CombatFixtures.character(secondCharacterId, 20L, "Boran"));
        session.addParticipant(first);
        session.addParticipant(second);

        Map<Long, BattleParticipant> participants = new LinkedHashMap<>();
        participants.put(firstCharacterId, first);
        participants.put(secondCharacterId, second);
        return registry.register(session, new BattleEngine(engine, session), participants);
    }

    @Test
    void registersLooksUpAndEndsABattle() {
        ActiveBattle battle = registerBattle(1L, 2L);

        assertEquals(1, registry.activeCount());
        assertSame(battle, registry.get(battle.battleId()), "a live battle is retrievable by its id");
        assertNotNull(battle.participantFor(1L));
        assertTrue(battle.hasParticipant(2L));
        assertEquals(2, battle.session().participants().size);

        registry.end(battle.battleId());

        assertNull(registry.get(battle.battleId()), "an ended battle is no longer retrievable");
        assertEquals(0, registry.activeCount(), "ended battles are not leaked");
        assertEquals(0, battle.session().participants().size, "BattleSession.end() released participants");
    }

    @Test
    void rejectsCharactersThatAreNotCombatants() {
        ActiveBattle battle = registerBattle(1L, 2L);

        assertFalse(battle.hasParticipant(99L), "a character in no battle is not a participant here");
        assertNull(battle.participantFor(99L));
        assertEquals(2L, battle.opponentCharacterIdOf(1L));
        assertEquals(1L, battle.opponentCharacterIdOf(2L));
    }

    @Test
    void issuesDistinctIdsAndEndingIsIdempotent() {
        ActiveBattle first = registerBattle(1L, 2L);
        ActiveBattle second = registerBattle(3L, 4L);

        assertEquals(2, registry.activeCount());
        assertTrue(first.battleId() != second.battleId(), "each battle gets its own id");

        registry.end(first.battleId());
        registry.end(first.battleId()); // ending twice must not blow up or disturb the other battle

        assertEquals(1, registry.activeCount());
        assertSame(second, registry.get(second.battleId()));
    }

    @Test
    void unknownBattleIdResolvesToNothing() {
        assertNull(registry.get(404L));
    }
}
