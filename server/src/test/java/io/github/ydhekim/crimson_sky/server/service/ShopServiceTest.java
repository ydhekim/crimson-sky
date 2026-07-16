package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Tameness;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System design §18 — the gold-only shop: weapon/pet repair (gold-priced or token-redeemed) and the two
 * catalog purchases. Like {@code SkillTreeServiceTest}, the writes run against a real (in-memory) database,
 * since "the gold spend and the inventory write commit or roll back together" is a property only a real
 * transaction can demonstrate. Character reads (ownership, the pre-check snapshot) come from
 * {@link FakeCharacterDao}; what the service mutates lives in the database, seeded consistently by
 * {@link #seed} so the pre-check and the locked read agree.
 */
class ShopServiceTest {

    private static final long ACCOUNT = 10L;
    private static final long OTHER_ACCOUNT = 20L;
    private static final long CHARACTER = 1L;
    private static final long WEAPON_ID = 1L;
    private static final long PET_ID = 2L;
    private static final int ELO = 1000;
    private static final String EMPTY_LOADOUT = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private ShopService service;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
    }

    private static Weapon hammer(int currentDurability) {
        return new Weapon(WEAPON_ID, "Hammer", "", Rarity.COMMON, 2f, 10, 20, 5, 20, currentDurability);
    }

    private static Pet wolf(int currentHealth) {
        return new Pet(PET_ID, "Wolf", "", Tameness.TRACEABLE, 50, 8, 15, 25, currentHealth);
    }

    /**
     * Seeds one character owning {@code weapon} and {@code pet} with {@code consumables} in stock, plus its
     * account's wallet — in <b>both</b> views, deliberately: the fake backs the service's pre-check read and
     * the database backs the locked read it actually acts on, so a divergence between them would be testing
     * a state that can't exist in production.
     */
    private void seed(long gold, Weapon weapon, Pet pet, Map<String, Integer> consumables) {
        Inventory inventory = new Inventory(Array.with(weapon), new Array<Skill>(), Array.with(pet),
            new HashMap<>(consumables));
        Character character = new Character(CHARACTER, ACCOUNT, "Ayla", Faction.A, 5, 0L, 100, 100, 100, 0, 0,
            new Stats(10, 10, 10, 10, 10, 10, 10, 10), inventory,
            new Loadout(new Array<>(), new Array<>(), new Array<>()), new HashMap<>());

        db = TestDatabase.create()
            .withAccount(ACCOUNT, gold)
            .withCharacter(CHARACTER, ACCOUNT, "Ayla", 5, 0L, ELO, inventoryJson(weapon, pet, consumables),
                EMPTY_LOADOUT);
        service = new ShopService(db.jdbi(), new CharacterService(new FakeCharacterDao()
            .with(character, ACCOUNT, ELO)));
    }

    /** The stored blob, spelled out field by field so the durability/health under test is never a default. */
    private static String inventoryJson(Weapon weapon, Pet pet, Map<String, Integer> consumables) {
        StringBuilder consumablesJson = new StringBuilder("{");
        for (Map.Entry<String, Integer> entry : consumables.entrySet()) {
            if (consumablesJson.length() > 1) {
                consumablesJson.append(",");
            }
            consumablesJson.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        consumablesJson.append("}");

        return "{\"weapons\":[{\"id\":" + weapon.id() + ",\"name\":\"Hammer\",\"maxDurability\":"
            + weapon.maxDurability() + ",\"currentDurability\":" + weapon.currentDurability() + "}],"
            + "\"skills\":[],"
            + "\"pets\":[{\"id\":" + pet.id() + ",\"name\":\"Wolf\",\"tameness\":\"TRACEABLE\",\"healthPoint\":"
            + pet.healthPoint() + ",\"currentHealth\":" + pet.currentHealth() + "}],"
            + "\"consumables\":" + consumablesJson + "}";
    }

    private static Map<String, Integer> stock(String key, int count) {
        Map<String, Integer> consumables = new HashMap<>();
        consumables.put(key, count);
        return consumables;
    }

    // --- weapon repair -----------------------------------------------------------------------------

    @Test
    void aGoldPaidRepairRestoresFullDurabilityAndChargesFivePerMissingPoint() {
        seed(1000L, hammer(6), wolf(50), Map.of()); // 14 missing → 70 gold

        var result = service.repairWeapon(ACCOUNT, CHARACTER, WEAPON_ID, false);

        assertTrue(result.success());
        assertEquals(20, result.data().weapon().currentDurability());
        assertEquals(930L, result.data().remainingGold(), "1000 − 5 × 14");
        assertEquals(930L, db.goldOf(ACCOUNT));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"currentDurability\":20"),
            "the repair lands in the persisted inventory, not just the response");
    }

    @Test
    void aFullyRepairedWeaponIsRejectedWithoutCharging() {
        // A no-op repair must not quietly take 0 gold (or, worse, a token) for doing nothing (§18).
        seed(1000L, hammer(20), wolf(50), stock(ShopService.REPAIR_TOKEN, 1));

        var result = service.repairWeapon(ACCOUNT, CHARACTER, WEAPON_ID, false);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_NOTHING_TO_REPAIR, result.code());
        assertEquals(1000L, db.goldOf(ACCOUNT), "nothing was charged");
    }

    @Test
    void anUnaffordableRepairLeavesGoldAndInventoryUntouched() {
        seed(10L, hammer(0), wolf(50), Map.of()); // 20 missing → 100 gold, but only 10 in the wallet

        var result = service.repairWeapon(ACCOUNT, CHARACTER, WEAPON_ID, false);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_GOLD_INSUFFICIENT, result.code());
        assertEquals(10L, db.goldOf(ACCOUNT));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"currentDurability\":0"),
            "the weapon is still broken — a rejected repair writes nothing");
    }

    @Test
    void aWeaponTheCharacterDoesNotOwnIsRejected() {
        seed(1000L, hammer(6), wolf(50), Map.of());

        var result = service.repairWeapon(ACCOUNT, CHARACTER, 999L, false);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_ITEM_NOT_FOUND, result.code());
    }

    @Test
    void aTokenPaidRepairRestoresTheWeaponAndSpendsNoGold() {
        // The O4 half that has to work before anything can grant a token (§18): the redemption path is real
        // and callable today, and it must not touch the wallet at all.
        seed(1000L, hammer(0), wolf(50), stock(ShopService.REPAIR_TOKEN, 2));

        var result = service.repairWeapon(ACCOUNT, CHARACTER, WEAPON_ID, true);

        assertTrue(result.success());
        assertEquals(20, result.data().weapon().currentDurability());
        assertEquals(1000L, result.data().remainingGold(), "the token path never charges gold");
        assertEquals(1, result.data().remainingRepairTokens(), "2 − 1");
        assertEquals(1000L, db.goldOf(ACCOUNT));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"repair_token\":1"),
            "exactly one token was redeemed, in the same write as the repair");
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"currentDurability\":20"));
    }

    @Test
    void aTokenPaidRepairWithNoTokensIsRejected() {
        seed(1000L, hammer(0), wolf(50), Map.of()); // ample gold, but that is not what was offered

        var result = service.repairWeapon(ACCOUNT, CHARACTER, WEAPON_ID, true);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_TOKEN_INSUFFICIENT, result.code());
        assertEquals(1000L, db.goldOf(ACCOUNT), "and a missing token never silently falls back to gold");
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"currentDurability\":0"));
    }

    // --- pet repair, the mirror of the above (§18) --------------------------------------------------

    @Test
    void aGoldPaidPetRestoreRefillsHealthAndChargesFivePerMissingPoint() {
        seed(1000L, hammer(20), wolf(30), Map.of()); // 20 missing → 100 gold

        var result = service.repairPet(ACCOUNT, CHARACTER, PET_ID, false);

        assertTrue(result.success());
        assertEquals(50, result.data().pet().currentHealth());
        assertEquals(900L, result.data().remainingGold(), "1000 − 5 × 20");
        assertEquals(900L, db.goldOf(ACCOUNT));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"currentHealth\":50"));
    }

    @Test
    void aFullyHealthyPetIsRejectedWithoutCharging() {
        seed(1000L, hammer(20), wolf(50), Map.of());

        var result = service.repairPet(ACCOUNT, CHARACTER, PET_ID, false);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_NOTHING_TO_REPAIR, result.code());
        assertEquals(1000L, db.goldOf(ACCOUNT));
    }

    @Test
    void anUnaffordablePetRestoreLeavesGoldAndInventoryUntouched() {
        seed(10L, hammer(20), wolf(0), Map.of()); // 50 missing → 250 gold

        var result = service.repairPet(ACCOUNT, CHARACTER, PET_ID, false);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_GOLD_INSUFFICIENT, result.code());
        assertEquals(10L, db.goldOf(ACCOUNT));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"currentHealth\":0"));
    }

    @Test
    void aPetTheCharacterDoesNotOwnIsRejected() {
        seed(1000L, hammer(20), wolf(10), Map.of());

        var result = service.repairPet(ACCOUNT, CHARACTER, 999L, false);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_ITEM_NOT_FOUND, result.code());
    }

    @Test
    void aKitPaidPetRestoreRefillsHealthAndSpendsNoGold() {
        seed(1000L, hammer(20), wolf(0), stock(ShopService.PET_CARE_KIT, 2));

        var result = service.repairPet(ACCOUNT, CHARACTER, PET_ID, true);

        assertTrue(result.success());
        assertEquals(50, result.data().pet().currentHealth());
        assertEquals(1000L, result.data().remainingGold());
        assertEquals(1, result.data().remainingPetCareKits(), "2 − 1");
        assertEquals(1000L, db.goldOf(ACCOUNT));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"pet_care_kit\":1"));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"currentHealth\":50"));
    }

    @Test
    void aKitPaidPetRestoreWithNoKitsIsRejected() {
        seed(1000L, hammer(20), wolf(0), Map.of());

        var result = service.repairPet(ACCOUNT, CHARACTER, PET_ID, true);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_TOKEN_INSUFFICIENT, result.code());
        assertEquals(1000L, db.goldOf(ACCOUNT));
    }

    /** A repair token does not pay for a pet: the two currencies are per-item-type and never fungible. */
    @Test
    void aRepairTokenCannotStandInForAPetCareKit() {
        seed(1000L, hammer(20), wolf(0), stock(ShopService.REPAIR_TOKEN, 5));

        var result = service.repairPet(ACCOUNT, CHARACTER, PET_ID, true);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_TOKEN_INSUFFICIENT, result.code());
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"repair_token\":5"), "and none was consumed");
    }

    // --- catalog purchases -------------------------------------------------------------------------

    @Test
    void buyingAScrollChargesFiftyGoldAndAddsExactlyOne() {
        seed(100L, hammer(20), wolf(50), Map.of());

        var result = service.buyScroll(ACCOUNT, CHARACTER);

        assertTrue(result.success());
        assertEquals(1, result.data().newCount());
        assertEquals(50L, result.data().remainingGold(), "100 − 50");
        assertEquals(50L, db.goldOf(ACCOUNT));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"skill_restoration_scroll\":1"));
    }

    @Test
    void buyingASecondScrollIncrementsRatherThanReplaces() {
        seed(100L, hammer(20), wolf(50), stock(ShopService.SKILL_RESTORATION_SCROLL, 3));

        var result = service.buyScroll(ACCOUNT, CHARACTER);

        assertTrue(result.success());
        assertEquals(4, result.data().newCount(), "3 + 1");
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"skill_restoration_scroll\":4"));
    }

    @Test
    void anUnaffordableScrollIsRejected() {
        seed(49L, hammer(20), wolf(50), Map.of());

        var result = service.buyScroll(ACCOUNT, CHARACTER);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_GOLD_INSUFFICIENT, result.code());
        assertEquals(49L, db.goldOf(ACCOUNT));
    }

    @Test
    void buyingAResetTokenChargesAThousandGoldAndAddsExactlyOne() {
        seed(1000L, hammer(20), wolf(50), Map.of());

        var result = service.buyResetToken(ACCOUNT, CHARACTER);

        assertTrue(result.success());
        assertEquals(1, result.data().newCount());
        assertEquals(0L, result.data().remainingGold(), "1000 − 1000: exactly affordable still buys");
        assertEquals(0L, db.goldOf(ACCOUNT));
        assertTrue(db.inventoryJsonOf(CHARACTER).contains("\"skill_tree_reset_token\":1"));
    }

    @Test
    void anUnaffordableResetTokenIsRejected() {
        seed(999L, hammer(20), wolf(50), Map.of());

        var result = service.buyResetToken(ACCOUNT, CHARACTER);

        assertFalse(result.success());
        assertEquals(MessageCode.SHOP_GOLD_INSUFFICIENT, result.code());
        assertEquals(999L, db.goldOf(ACCOUNT));
    }

    @Test
    void aPurchaseLeavesTheRestOfTheInventoryAlone() {
        // The blob is read-modify-written whole, so the one thing a purchase must prove is that it changed
        // only its own key — the same C2 posture every other inventory writer is held to.
        seed(100L, hammer(6), wolf(30), Map.of());

        assertTrue(service.buyScroll(ACCOUNT, CHARACTER).success());

        String inventory = db.inventoryJsonOf(CHARACTER);
        assertTrue(inventory.contains("\"currentDurability\":6"), "the weapon's wear is untouched");
        assertTrue(inventory.contains("\"currentHealth\":30"), "and so is the pet's");
    }

    // --- ownership ---------------------------------------------------------------------------------

    @Test
    void everyOperationRejectsACharacterTheAccountDoesNotOwn() {
        seed(10_000L, hammer(0), wolf(0), stock(ShopService.REPAIR_TOKEN, 5));

        assertEquals(MessageCode.ERROR_UNKNOWN,
            service.repairWeapon(OTHER_ACCOUNT, CHARACTER, WEAPON_ID, false).code());
        assertEquals(MessageCode.ERROR_UNKNOWN,
            service.repairPet(OTHER_ACCOUNT, CHARACTER, PET_ID, false).code());
        assertEquals(MessageCode.ERROR_UNKNOWN, service.buyScroll(OTHER_ACCOUNT, CHARACTER).code());
        assertEquals(MessageCode.ERROR_UNKNOWN, service.buyResetToken(OTHER_ACCOUNT, CHARACTER).code());
        assertEquals(10_000L, db.goldOf(ACCOUNT), "and none of them spent the owner's gold");
    }
}
