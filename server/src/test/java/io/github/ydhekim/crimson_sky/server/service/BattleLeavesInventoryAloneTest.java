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
 * persistence, never takes a stored item away from a character.</i> Both halves of that are asserted
 * below — empirically (across a full attack + reward round trip that demonstrably did write other
 * columns, every stored item is still there) and structurally (only two named {@code UPDATE}s in
 * {@link CharacterDao} can touch those columns at all). When an item-loss skill does land, this test is
 * the one it has to keep passing.
 *
 * <p><b>Narrowed at Epic N, deliberately (§17):</b> this test used to assert the stored {@code inventory}
 * JSON was <i>byte-for-byte</i> identical after a battle. Durability made that assertion wrong rather
 * than violated — a battle now legitimately writes one field of one item (a fired weapon's
 * {@code currentDurability}), through the same sanctioned {@code updateInventory} path, and
 * {@link #aFiredWeaponsDurabilityDecrementLandsInThePersistedInventory} pins that it does. What C2 was
 * always defending is the item's <i>existence</i>, not the immutability of every byte in the column, so
 * that is what the round-trip test below now asserts. {@code loadout} is still byte-for-byte checked: a
 * battle has no business writing there at all.
 */
class BattleLeavesInventoryAloneTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long ATTACKER = 1L;
    private static final long OPPONENT = 2L;

    /**
     * The attacker owns and equips the weapon {@link CombatFixtures#character} fights with (id 1), at full
     * durability — so a battle really does fire it, and §17's decrement has something real to land on.
     * Spelling the durability fields out matters: a stored weapon defaulting to {@code currentDurability
     * 0} would read as broken and never fire, quietly making the decrement assertions vacuous.
     */
    private static final String INVENTORY_JSON =
        "{\"weapons\":[{\"id\":1,\"name\":\"Testing Hammer\",\"maxDurability\":20,\"currentDurability\":20}],"
            + "\"skills\":[],\"pets\":[]}";
    private static final String LOADOUT_JSON =
        "{\"weapons\":[{\"id\":1,\"name\":\"Testing Hammer\",\"maxDurability\":20,\"currentDurability\":20}],"
            + "\"skills\":[],\"pets\":[]}";

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
    void aFullAttackAndItsRewardNeverTakeAStoredItemAway() {
        String loadoutBefore = db.loadoutJsonOf(ATTACKER);

        Optional<AttackResult> result = attackService.attack(ATTACKER);
        assertTrue(result.isPresent(), "precondition: the battle resolves");
        RewardOutcome outcome = rewardService.applyRewards(result.get());

        String inventoryAfter = db.inventoryJsonOf(ATTACKER);
        assertTrue(inventoryAfter.contains("Testing Hammer"),
            "a battle never removes an item from storage — items are not lost in battle (§8)");
        assertEquals(1, weaponCountOf(inventoryAfter),
            "and never adds one either, absent a milestone bonus: the item set is exactly what it was");
        assertEquals(loadoutBefore, db.loadoutJsonOf(ATTACKER),
            "the loadout is byte-for-byte untouched: what a battle spends (stamina, mana) is in-memory only");

        // Without this the assertions above would pass on a flow that wrote nothing whatsoever.
        assertNotEquals(RewardOutcome.none(), outcome, "precondition: this round trip really did persist");
        assertEquals(outcome.expDelta(), db.experienceOf(ATTACKER));
        assertEquals(1, db.battleHistoryRowCount());

        // The opponent is a snapshot: an attack against it must not write anything of theirs — including
        // durability. Only the attacker is rewarded, so only the attacker's weapons ever wear (§17).
        assertEquals(INVENTORY_JSON, db.inventoryJsonOf(OPPONENT));
        assertEquals(0L, db.experienceOf(OPPONENT));
    }

    @Test
    void aFiredWeaponsDurabilityDecrementLandsInThePersistedInventory() {
        // The positive half of the rule this test narrowed for (§17): the battle's *one* sanctioned write
        // to `inventory` is a fired weapon's durability, and it commits with the rest of the reward.
        // CombatFixtures.character always draws its weapon (STR 100), so id 1 fires every battle.
        Optional<AttackResult> result = attackService.attack(ATTACKER);
        assertTrue(result.isPresent(), "precondition: the battle resolves");
        rewardService.applyRewards(result.get());

        assertTrue(db.inventoryJsonOf(ATTACKER).contains("\"currentDurability\":19"),
            "the fired weapon wore by exactly 1 (20 → 19), written through updateInventory");
        assertTrue(db.inventoryJsonOf(ATTACKER).contains("\"maxDurability\":20"),
            "wear touches current durability only — max is what repair restores to (Epic O)");
    }

    /** Weapon entries in a stored inventory blob, counted by the one key every weapon has. */
    private static int weaponCountOf(String inventoryJson) {
        return inventoryJson.split("\"id\":", -1).length - 1;
    }

    @Test
    void onlyTheSanctionedGrantPathCanReachTheStoredItems() {
        // The structural half: exactly TWO sanctioned, named UPDATEs may reach the stored item columns —
        // `updateInventory` (Epic L's bonus item-grant / §16's skill grant) for `inventory`, and
        // `updateLoadout` (§16's loadout-save capability) for `loadout` — and neither may touch the other's
        // column. Every other UPDATE is still barred from `inventory`/`loadout` entirely, so no battle-side
        // code can start writing there by accident. These are the deliberate, named exceptions C2's
        // docstring anticipated — not regressions.
        //
        // Still exactly two after Epic N: §17's durability decrement needed NO new exception, because it is
        // a different in-memory transformation before the same `updateInventory` call, not a new write path
        // (§18 makes that the standing rule for every feature that mutates `inventory` from here on — pet
        // health, consumable charges). If a future pass adds a third name to this list, that is the design
        // going wrong, not the test.
        boolean sawSanctionedInventoryWriter = false;
        boolean sawSanctionedLoadoutWriter = false;
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
            if (method.getName().equals("updateLoadout")) {
                sawSanctionedLoadoutWriter = true;
                assertTrue(sql.contains("loadout"), "updateLoadout must be the loadout-save path (§16)");
                assertFalse(sql.contains("inventory"),
                    "even the sanctioned loadout writer must not touch `inventory` (§8)");
                continue;
            }
            assertFalse(sql.contains("inventory"),
                "CharacterDao." + method.getName() + " updates `inventory` — only updateInventory may (§8/§15)");
            assertFalse(sql.contains("loadout"),
                "CharacterDao." + method.getName() + " updates `loadout` — only updateLoadout may (§8/§16)");
        }
        assertTrue(sawSanctionedInventoryWriter,
            "the one sanctioned inventory writer (updateInventory) must exist — otherwise this guard is vacuous");
        assertTrue(sawSanctionedLoadoutWriter,
            "the one sanctioned loadout writer (updateLoadout) must exist — otherwise this guard is vacuous");
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
