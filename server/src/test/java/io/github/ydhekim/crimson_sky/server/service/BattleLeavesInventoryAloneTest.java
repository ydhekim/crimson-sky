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
    void onlyTheSanctionedGrantPathCanReachTheStoredItems() {
        // The structural half: exactly ONE UPDATE — `updateInventory`, Epic L's bonus item-grant — may
        // reach the stored items, and even it must not touch `loadout`. Every other UPDATE is still barred
        // from `inventory`/`loadout` entirely, so no battle-side code can start writing there by accident.
        // This is the deliberate, named exception C2's docstring anticipated — not a regression. The next
        // capability that needs to write items (durability, §N) adds its method here the same way.
        boolean sawSanctionedInventoryWriter = false;
        for (Method method : CharacterDao.class.getDeclaredMethods()) {
            SqlUpdate update = method.getAnnotation(SqlUpdate.class);
            if (update == null) {
                continue;
            }
            String sql = String.join(" ", update.value()).toLowerCase(Locale.ROOT);
            if (!sql.startsWith("update")) {
                continue; // INSERT (character creation) and DELETE are not what this rule is about.
            }
            if (method.getName().equals("updateInventory")) {
                sawSanctionedInventoryWriter = true;
                assertTrue(sql.contains("inventory"), "updateInventory must be the inventory grant path");
                assertFalse(sql.contains("loadout"),
                    "even the sanctioned inventory writer must not touch `loadout` (§8)");
                continue;
            }
            assertFalse(sql.contains("inventory"),
                "CharacterDao." + method.getName() + " updates `inventory` — only updateInventory may (§8/§15)");
            assertFalse(sql.contains("loadout"),
                "CharacterDao." + method.getName() + " updates `loadout` — items are never lost from storage (§8)");
        }
        assertTrue(sawSanctionedInventoryWriter,
            "the one sanctioned inventory writer (updateInventory) must exist — otherwise this guard is vacuous");
    }

    @Test
    void aMilestoneBonusRollGrantsAWeaponIntoThePersistedInventory() {
        // The first real exercise of the new write path (Epic L / §15): a character seeded two exp below
        // the level-10 threshold (expNeededForLevel(10) == 792) crosses into level 10 on any battle, and a
        // Random stubbed to always pass the 10% roll and pick weapon index 0 grants Twin Daggers. The grant
        // must land in the character's *persisted* inventory, committed with the rest of the reward.
        long attacker = 1L;
        long opponent = 2L;
        long accountA = 10L;
        long accountB = 20L;
        String nullInventory = "{\"weapons\":null,\"skills\":null,\"pets\":null}";

        FakeCharacterDao dao = new FakeCharacterDao()
            .with(CombatFixtures.characterAtLevel(attacker, accountA, "Ayla", 9, 790L), accountA, 1000)
            .with(CombatFixtures.character(opponent, accountB, "Boran"), accountB, 1000);
        TestDatabase bonusDb = TestDatabase.create()
            .withAccount(accountA, 0L).withAccount(accountB, 0L)
            .withCharacter(attacker, accountA, "Ayla", 9, 790L, 1000, nullInventory, nullInventory)
            .withCharacter(opponent, accountB, "Boran", 1, 0L, 1000, nullInventory, nullInventory);

        CharacterService characterService = new CharacterService(dao);
        AttackService bonusAttack = new AttackService(characterService, new BotFactory(new Random(42L)), new Random(42L));
        RewardService bonusReward = new RewardService(bonusDb.jdbi(), characterService, alwaysRollsBonus());

        Optional<AttackResult> result = bonusAttack.attack(attacker);
        assertTrue(result.isPresent(), "precondition: the battle resolves");
        RewardOutcome outcome = bonusReward.applyRewards(result.get());

        assertEquals(10, bonusDb.levelOf(attacker), "790 + exp delta crosses the level-10 milestone");
        assertEquals("Twin Daggers", outcome.bonusRewardGranted(), "the granted weapon is reported to the client");
        assertTrue(bonusDb.inventoryJsonOf(attacker).contains("Twin Daggers"),
            "the bonus weapon is written into the character's persisted inventory");
    }

    /** A {@link Random} that always passes the 10% milestone roll and picks the first bonus weapon. */
    private static Random alwaysRollsBonus() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0; // strictly < BONUS_ROLL_CHANCE, so every milestone roll succeeds
            }

            @Override
            public int nextInt(int bound) {
                return 0; // Twin Daggers, the first entry in the bonus table
            }
        };
    }
}
