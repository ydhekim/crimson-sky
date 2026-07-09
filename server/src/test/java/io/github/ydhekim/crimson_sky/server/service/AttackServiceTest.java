package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.network.packet.AttackResponse;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story B4 / system design §7: opponent selection (Elo band → unbounded → bot), whole-battle
 * resolution in one call, and the protocol-level guarantee that a bot opponent is invisible to the
 * client. Headless, no DB, no socket.
 */
class AttackServiceTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ATTACKER = 1L;

    private FakeCharacterDao characterDao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        characterDao = new FakeCharacterDao()
            .with(CombatFixtures.character(ATTACKER, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000);
    }

    /** A service with a seeded RNG so "pick randomly among candidates" is reproducible. */
    private AttackService service() {
        return new AttackService(new CharacterService(characterDao), new BotFactory(new Random(42L)), new Random(42L));
    }

    private AttackResult attack() {
        Optional<AttackResult> result = service().attack(ATTACKER);
        assertTrue(result.isPresent(), "an owned, loadable character always resolves a battle");
        return result.get();
    }

    @Test
    void picksAPersistedOpponentInsideTheEloBand() {
        characterDao.with(CombatFixtures.character(2L, 20L, "Boran"), 20L, 1050); // gap 50, inside ±100
        characterDao.with(CombatFixtures.character(3L, 30L, "Cem"), 30L, 1400);   // gap 400, outside

        AttackResult result = attack();

        assertFalse(result.opponentIsBot(), "a real opponent exists inside the band");
        assertEquals(2L, result.opponentCharacterId().longValue(), "the out-of-band character is never chosen");
        assertEquals("Boran", result.opponentDisplayName());
    }

    @Test
    void widensToAnUnboundedRangeWhenNobodyIsInsideTheBand() {
        characterDao.with(CombatFixtures.character(3L, 30L, "Cem"), 30L, 1400); // 400 Elo away

        AttackResult result = attack();

        assertFalse(result.opponentIsBot(), "widening finds the distant real opponent before any bot");
        assertEquals(3L, result.opponentCharacterId().longValue());
    }

    @Test
    void fallsBackToABotWhenNoPersistedOpponentExists() {
        // Only the attacker exists in the table.
        AttackResult result = attack();

        assertTrue(result.opponentIsBot());
        assertNull(result.opponentCharacterId(), "a bot has no row in `characters` (mirrors §8's nullable column)");
        assertNotNull(result.opponentDisplayName());
        assertFalse(result.opponentDisplayName().isBlank(), "a bot still needs a plausible display name");
    }

    @Test
    void neverSelectsTheRequesterAsItsOwnOpponent() {
        // The DAO hands back the requester, as a broken WHERE clause would; the service must re-exclude
        // it rather than fight itself.
        characterDao.leakingRequesterIntoCandidates();

        AttackResult result = attack();

        assertTrue(result.opponentIsBot(), "with only the requester in the table, a bot is the only option");
        assertNull(result.opponentCharacterId());
    }

    @Test
    void resolvesTheWholeBattleAndKeepsEveryTurn() {
        characterDao.with(CombatFixtures.character(2L, 20L, "Boran"), 20L, 1000);

        AttackResult result = attack();

        // Two 500 HP fixtures trading a guaranteed 150 damage per turn need four turns to settle, so
        // the log must hold more than the single final turn TurnResultComponent alone would leave.
        assertTrue(result.turns().size > 1,
            "the whole battle's turns are returned, not just the last one (got " + result.turns().size + ")");

        int totalDamage = 0;
        for (Array<ResolvedAction> turn : result.turns()) {
            assertFalse(turn.isEmpty(), "a recorded turn always has at least one Result Set entry");
            for (ResolvedAction action : turn) {
                totalDamage += action.damage();
            }
        }
        assertTrue(totalDamage > 0, "the log carries real applied damage, not decision-layer zeroes");
    }

    @Test
    void reportsWhetherTheAttackerWon() {
        // A defenceless 1 HP opponent: whoever strikes first ends it, and the attacker always strikes
        // for 150. The attacker can only lose if the opponent's own hit lands first, which it cannot —
        // the opponent has no weapon and punches for at most 5 against 500 HP.
        characterDao.with(CombatFixtures.frailCharacter(2L, 20L, "Boran"), 20L, 1000);

        AttackResult result = attack();

        assertTrue(result.won(), "the attacker one-shots a 1 HP opponent");
        assertTrue(result.toResponse().won());
    }

    @Test
    void refusesToAttackWithACharacterThatCannotBeLoaded() {
        assertTrue(service().attack(999L).isEmpty(), "an unknown character resolves no battle");
    }

    @Test
    void attackResponseCannotRevealThatTheOpponentWasABot() {
        AttackResult botFight = attack();
        assertTrue(botFight.opponentIsBot(), "precondition: this fight was against a bot");

        AttackResponse response = botFight.toResponse();

        // The internal result knows; the packet's wire format has nowhere to put it. Assert on the
        // record's shape, so adding an opponent id or bot flag later fails this test loudly.
        List<String> wireFields = Arrays.stream(AttackResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
        assertEquals(List.of("battleId", "opponentDisplayName", "won", "turns"), wireFields,
            "AttackResponse must expose no opponent id and no bot flag (system design §7)");
        assertNotNull(response.opponentDisplayName());
    }
}
