package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.BattleMode;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story O2 / system design §18 — a triggered potion's charge decrement lands in the attacker's persisted
 * {@code Inventory} (the single source of truth), inside the reward transaction, through the one sanctioned
 * {@code updateInventory} write every other inventory mutation already funnels through.
 *
 * <p><b>Why the turn log is hand-built here</b>, unlike its durability/pet-health siblings' persistence
 * halves: the fact under test is a <i>count</i> — three triggers must cost three charges — and driving a real
 * battle to trigger a potion exactly three times means hunting a seed that happens to produce three, which
 * would pin the seed rather than the rule. {@code applyRewards} reads the battle's effects entirely off
 * {@code result.turns()}, so handing it the log directly exercises the real transaction, the real
 * read-modify-write, and the real stored blob — everything except the battle that produced the log, which
 * {@code BattleEnginePotionTest} covers on its own.
 */
class RewardServiceConsumablePersistenceTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long ATTACKER = 1L;
    private static final long OPPONENT = 2L;
    private static final long POTION_ID = 100L;
    private static final long PET_ID = 7L;
    private static final long WEAPON_ID = 1L;

    /**
     * A stored inventory holding a weapon at full durability, a partly worn pet, and a potion with
     * {@code charges}. The unrelated fields Jackson fills with defaults are left out, exactly as the
     * sibling fixtures' weapon/pet blobs do.
     */
    private static String inventoryJson(int charges) {
        return "{\"weapons\":[{\"id\":1,\"name\":\"Testing Hammer\",\"maxDurability\":20,"
            + "\"currentDurability\":20}],"
            + "\"skills\":[{\"id\":100,\"name\":\"Small Health Potion\",\"type\":\"CONSUMABLE\","
            + "\"restoresResource\":\"HEALTH\",\"thresholdPercent\":50,\"restoreAmount\":100,"
            + "\"charges\":" + charges + "}],"
            + "\"pets\":[{\"id\":7,\"name\":\"Bear\",\"tameness\":\"LOYAL\",\"healthPoint\":80,"
            + "\"currentHealth\":40}],"
            + "\"consumables\":{\"repair_token\":2}}";
    }

    private TestDatabase db;
    private RewardService rewardService;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        seedWithCharges(5);
    }

    private void seedWithCharges(int charges) {
        String inventory = inventoryJson(charges);
        FakeCharacterDao dao = new FakeCharacterDao()
            .with(CombatFixtures.character(ATTACKER, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(OPPONENT, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);
        db = TestDatabase.create()
            .withAccount(ACCOUNT_A, 0L).withAccount(ACCOUNT_B, 0L)
            .withCharacter(ATTACKER, ACCOUNT_A, "Ayla", 0L, 1000, inventory, inventory)
            .withCharacter(OPPONENT, ACCOUNT_B, "Boran", 0L, 1000, inventory, inventory);
        rewardService = new RewardService(db.jdbi(), new CharacterService(dao),
            db.jdbi().onDemand(BattleHistoryDao.class), new AchievementUnlockService());
    }

    private static ResolvedAction potionSip() {
        return new ResolvedAction(ActionSource.CONSUMABLE, "Small Health Potion", 1, false, 100, POTION_ID);
    }

    private static ResolvedAction weaponHit() {
        return new ResolvedAction(ActionSource.WEAPON, "Testing Hammer", 1, false, 150, WEAPON_ID);
    }

    private static ResolvedAction petHit() {
        return new ResolvedAction(ActionSource.PET, "Bear", 3, false, 40, PET_ID);
    }

    /** Applies a won battle whose turn log is exactly {@code turns}, and returns the stored inventory after. */
    @SafeVarargs
    private String rewardBattleOf(Array<ResolvedAction>... turns) {
        AttackResult result = new AttackResult(
            1L, ATTACKER, OPPONENT, "Boran", false, true, Array.with(turns), BattleMode.NORMAL);
        rewardService.applyRewards(result);
        return db.inventoryJsonOf(ATTACKER);
    }

    @Test
    void aPotionTriggeringThreeTimesLosesExactlyThreeCharges() {
        // §0/§18's tally-vs-set distinction, made observable at the only layer that can show it: a weapon
        // firing on three turns loses 1, a potion drunk on three turns loses 3.
        String inventory = rewardBattleOf(
            Array.with(potionSip()), Array.with(potionSip()), Array.with(potionSip()));

        assertTrue(inventory.contains("\"charges\":2"), "5 → 2, one per actual trigger");
        assertFalse(inventory.contains("\"charges\":4"), "not 5 → 4, which is what a Set would have written");
    }

    @Test
    void aPotionThatNeverTriggeredKeepsItsCharges() {
        // The other half of the rule: charges are spent by *drinking*, so a battle the potion sat out — a
        // fight the character never dropped below its threshold in — costs it nothing.
        assertTrue(rewardBattleOf(Array.with(weaponHit())).contains("\"charges\":5"),
            "a potion equipped but never drunk is untouched");
    }

    @Test
    void chargesNeverGoBelowZero() {
        // The floor that keeps `charges > 0` a meaningful "can this still fire" check forever. Combat won't
        // produce more triggers than charges, but the write must not depend on combat being right.
        seedWithCharges(2);
        String inventory = rewardBattleOf(
            Array.with(potionSip()), Array.with(potionSip()), Array.with(potionSip()));

        assertTrue(inventory.contains("\"charges\":0"), "2 charges, 3 triggers → 0, never −1");
    }

    @Test
    void anAlreadySpentPotionIsNotDrivenNegative() {
        seedWithCharges(0);

        assertTrue(rewardBattleOf(Array.with(potionSip())).contains("\"charges\":0"),
            "a spent potion stays at 0");
    }

    @Test
    void aFiringWeaponAnActingPetAndADrunkPotionAllLandInTheOneWrite() {
        // §18's standing rule at full stretch: three features mutating `inventory` are three in-memory
        // transformations before one `updateInventory` call, so all three changes are in the stored blob —
        // and no new sanctioned writer was needed for any of them (see BattleLeavesInventoryAloneTest).
        String inventory = rewardBattleOf(
            Array.with(weaponHit(), petHit()),
            Array.with(potionSip(), petHit()));

        assertTrue(inventory.contains("\"currentDurability\":19"), "the fired weapon wore: 20 → 19 (§17)");
        assertTrue(inventory.contains("\"currentHealth\":39"), "the acting pet wore: 40 → 39 (§18)");
        assertTrue(inventory.contains("\"charges\":4"), "and the potion was drunk once: 5 → 4 (§18)");
        assertTrue(inventory.contains("\"repair_token\":2"),
            "while the shop's consumables rode through untouched — a battle never spends them, and they are "
                + "unrelated to a potion's charges despite the name");
        assertTrue(inventory.contains("Testing Hammer"), "and nothing was ever taken away (§8/C2)");
        assertTrue(inventory.contains("\"name\":\"Bear\""));
        assertTrue(inventory.contains("Small Health Potion"));
    }
}
