package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.combat.RewardOutcome;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story C2 — <b>items "lost" in battle are never removed from the permanent inventory</b> (GDD rule,
 * system design §8). {@code BattleStateComponent} is what a battle consumes and discards; the DB is not.
 *
 * <p><b>Why this is shaped the way it is:</b> no skill or mechanic in the game currently causes item
 * loss at all — Break/Steal are deferred (Epic J) — so there is no item-loss scenario to fight through
 * end to end, and inventing one would test a mechanic that doesn't exist rather than the rule that does.
 * What C2 actually protects is narrower and testable today: <i>a battle, from resolution through reward
 * persistence, never issues a write to a character's stored items.</i> Both halves of that are asserted
 * below — empirically (the stored JSON is byte-for-byte identical across a full attack + reward round
 * trip that demonstrably did write other columns) and structurally (no {@code UPDATE} in
 * {@link CharacterDao} can touch those columns in the first place). When an item-loss skill does land,
 * this test is the one it has to keep passing.
 */
class BattleLeavesInventoryAloneTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long ATTACKER = 1L;
    private static final long OPPONENT = 2L;

    private static final String INVENTORY_JSON =
        "{\"weapons\":[{\"id\":1,\"name\":\"Testing Hammer\"}],\"skills\":[],\"pets\":[]}";
    private static final String LOADOUT_JSON =
        "{\"weapons\":[{\"id\":1,\"name\":\"Testing Hammer\"}],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private AttackService attackService;
    private RewardService rewardService;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();

        FakeCharacterDao characterDao = new FakeCharacterDao()
            .with(CombatFixtures.character(ATTACKER, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(OPPONENT, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);

        db = TestDatabase.create()
            .withAccount(ACCOUNT_A, 0L)
            .withAccount(ACCOUNT_B, 0L)
            .withCharacter(ATTACKER, ACCOUNT_A, "Ayla", 0L, 1000, INVENTORY_JSON, LOADOUT_JSON)
            .withCharacter(OPPONENT, ACCOUNT_B, "Boran", 0L, 1000, INVENTORY_JSON, LOADOUT_JSON);

        CharacterService characterService = new CharacterService(characterDao);
        attackService = new AttackService(characterService, new BotFactory(new Random(42L)), new Random(42L));
        rewardService = new RewardService(db.jdbi(), characterService);
    }

    @Test
    void aFullAttackAndItsRewardLeaveTheStoredItemsByteForByteIdentical() {
        String inventoryBefore = db.inventoryJsonOf(ATTACKER);
        String loadoutBefore = db.loadoutJsonOf(ATTACKER);

        Optional<AttackResult> result = attackService.attack(ATTACKER);
        assertTrue(result.isPresent(), "precondition: the battle resolves");
        RewardOutcome outcome = rewardService.applyRewards(result.get());

        assertEquals(inventoryBefore, db.inventoryJsonOf(ATTACKER),
            "a battle never writes to a character's inventory — items are not lost from storage (§8)");
        assertEquals(loadoutBefore, db.loadoutJsonOf(ATTACKER),
            "nor to their loadout: what a battle spends (stamina, mana) is in-memory state only");

        // Without this the assertions above would pass on a flow that wrote nothing whatsoever.
        assertNotEquals(RewardOutcome.none(), outcome, "precondition: this round trip really did persist");
        assertEquals(outcome.expDelta(), db.experienceOf(ATTACKER));
        assertEquals(1, db.battleHistoryRowCount());

        // The opponent is a snapshot: an attack against it must not write anything of theirs either.
        assertEquals(INVENTORY_JSON, db.inventoryJsonOf(OPPONENT));
        assertEquals(0L, db.experienceOf(OPPONENT));
    }

    @Test
    void noCharacterUpdateStatementCanEvenReachTheStoredItems() {
        // The structural half: the DAO layer offers no way to modify a stored inventory/loadout outside
        // character creation, so no future battle-side code can start doing it by accident. An item-loss
        // skill that needs to write items would have to add such a statement — and change this test
        // deliberately, which is exactly the conversation C2 exists to force.
        for (Method method : CharacterDao.class.getDeclaredMethods()) {
            SqlUpdate update = method.getAnnotation(SqlUpdate.class);
            if (update == null) {
                continue;
            }
            String sql = String.join(" ", update.value()).toLowerCase(Locale.ROOT);
            if (!sql.startsWith("update")) {
                continue; // INSERT (character creation) and DELETE are not what this rule is about.
            }
            assertFalse(sql.contains("inventory"),
                "CharacterDao." + method.getName() + " updates `inventory` — items are never lost from storage (§8)");
            assertFalse(sql.contains("loadout"),
                "CharacterDao." + method.getName() + " updates `loadout` — items are never lost from storage (§8)");
        }
    }
}
