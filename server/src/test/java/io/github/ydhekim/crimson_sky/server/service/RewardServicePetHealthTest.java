package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Tameness;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story O3 / system design §18 — a pet that acted loses <b>one</b> point of health per battle, written
 * into the attacker's persisted {@code Inventory} (the single source of truth) inside the reward
 * transaction. The deliberate mirror of {@code RewardServiceDurabilityTest}, split the same way:
 * {@link #firedPetId} is a pure read of the battle log and is tested against hand-built turn histories
 * (the only way to pin "acting three times in one battle is still −1" without a seed that happens to
 * produce three), while the persistence half runs a real battle through {@code RewardService} against H2.
 */
class RewardServicePetHealthTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long ATTACKER = 1L;
    private static final long OPPONENT = 2L;
    private static final long PET_ID = 7L;

    /** LOYAL (+20): paired with Insight 90 the gate reads 110 > 100, so the pet acts every battle. */
    private static Pet bear(int currentHealth) {
        return new Pet(PET_ID, "Bear", "", Tameness.LOYAL, 80, 15, 20, 36, currentHealth);
    }

    /** WILD (−10): paired with Insight 0 the gate reads −10, which no d100 draw can beat. Never acts. */
    private static Pet skittishBear(int currentHealth) {
        return new Pet(PET_ID, "Bear", "", Tameness.WILD, 80, 15, 20, 36, currentHealth);
    }

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
    }

    // --- the pure half: did the pet act at all this battle (§18's "per battle, not per hit") ------

    private static ResolvedAction petHit(long itemId) {
        return new ResolvedAction(ActionSource.PET, "Bear", 3, false, 40, itemId);
    }

    private static AttackResult resultWithTurns(Array<Array<ResolvedAction>> turns) {
        return new AttackResult(1L, ATTACKER, OPPONENT, "Boran", false, true, turns);
    }

    @Test
    void aPetActingOnEveryTurnIsStillCountedOnce() {
        // The rule that makes this a Set and not a tally: health is per battle, not per hit or per repeat,
        // so a long fight must not exhaust a pet in one sitting (§18).
        Array<Array<ResolvedAction>> turns = Array.with(
            Array.with(petHit(PET_ID)), Array.with(petHit(PET_ID)), Array.with(petHit(PET_ID)));

        assertEquals(Set.of(PET_ID), RewardService.firedPetId(resultWithTurns(turns)));
    }

    @Test
    void nonPetEntriesNeverWearAPet() {
        // Weapons and skills carry an item id too, so the source filter — not the presence of an id — is
        // what keeps this pets-only. Punch/Burned carry 0 and would be filtered either way.
        Array<Array<ResolvedAction>> turns = Array.with(Array.with(
            new ResolvedAction(ActionSource.WEAPON, "Hammer", 1, false, 40, 1L),
            new ResolvedAction(ActionSource.SKILL, "Spark", 2, false, 30, 5L),
            new ResolvedAction(ActionSource.PUNCH, "Punch", 1, false, 3, 0L)));

        assertTrue(RewardService.firedPetId(resultWithTurns(turns)).isEmpty(),
            "a battle of weapons, skills and punches wears no pet");
    }

    // --- the persistence half: it lands in the stored inventory, inside the transaction -----------

    private static String inventoryJson(Pet pet) {
        return "{\"weapons\":[{\"id\":1,\"name\":\"Testing Hammer\",\"maxDurability\":20,\"currentDurability\":20}],"
            + "\"skills\":[],"
            + "\"pets\":[{\"id\":" + pet.id() + ",\"name\":\"Bear\",\"tameness\":\"" + pet.tameness()
            + "\",\"healthPoint\":80,\"currentHealth\":" + pet.currentHealth() + "}],"
            + "\"consumables\":{\"repair_token\":2}}";
    }

    /**
     * A real attack + reward round trip with {@code pet} equipped and owned at its given health.
     * {@code insight} decides whether the pet acts at all, per {@link CombatFixtures#characterWithPet}.
     *
     * @return the attacker's persisted inventory JSON afterwards
     */
    private String battleWithStoredPet(Pet pet, int insight, boolean expectPetToAct) {
        String inventory = inventoryJson(pet);

        FakeCharacterDao dao = new FakeCharacterDao()
            .with(CombatFixtures.characterWithPet(ATTACKER, ACCOUNT_A, "Ayla", pet, insight), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(OPPONENT, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);
        TestDatabase db = TestDatabase.create()
            .withAccount(ACCOUNT_A, 0L).withAccount(ACCOUNT_B, 0L)
            .withCharacter(ATTACKER, ACCOUNT_A, "Ayla", 0L, 1000, inventory, inventory)
            .withCharacter(OPPONENT, ACCOUNT_B, "Boran", 0L, 1000, inventory, inventory);

        CharacterService characterService = new CharacterService(dao);
        AttackService attackService = new AttackService(
            characterService, new BotFactory(new Random(42L)), new Random(42L));
        RewardService rewardService = new RewardService(db.jdbi(), characterService);

        Optional<AttackResult> result = attackService.attack(ATTACKER);
        assertTrue(result.isPresent(), "precondition: the battle resolves");
        assertEquals(expectPetToAct, RewardService.firedPetId(result.get()).contains(PET_ID),
            "precondition: the fixture decides whether the pet acted, not the seed");
        rewardService.applyRewards(result.get());

        return db.inventoryJsonOf(ATTACKER);
    }

    @Test
    void anActingPetWearsByExactlyOne() {
        assertTrue(battleWithStoredPet(bear(80), 90, true).contains("\"currentHealth\":79"),
            "80 → 79 in the persisted inventory, however many times the pet swung");
    }

    @Test
    void aPetThatNeverActedKeepsItsHealth() {
        // The other half of §18's rule: health is spent by *being used*, so a pet whose gate roll never
        // succeeded costs nothing — a battle it sat out is not a battle it worked.
        assertTrue(battleWithStoredPet(skittishBear(80), 0, false).contains("\"currentHealth\":80"),
            "a pet that never rolled a success is untouched");
    }

    @Test
    void petHealthNeverGoesBelowZero() {
        // The floor that matters: a pet on its last point lands at 0 and stops, never −1, so
        // `currentHealth > 0` stays a meaningful "is it worn out" check forever.
        assertTrue(battleWithStoredPet(bear(1), 90, true).contains("\"currentHealth\":0"),
            "1 → 0, the pet is spent");
        assertTrue(battleWithStoredPet(bear(1), 90, true).contains("\"healthPoint\":80"),
            "and it remains restorable — max health is untouched (Epic O prices against it)");
    }

    @Test
    void anAlreadyWornOutPetIsNotDrivenNegative() {
        // At 0 the pet never acts, so there is nothing to decrement — but if it somehow did, worn() floors
        // at 0. Asserting the stored value directly covers both paths.
        assertTrue(battleWithStoredPet(bear(0), 90, false).contains("\"currentHealth\":0"),
            "a worn-out pet stays at 0 — never −1");
    }

    @Test
    void aFiringWeaponAndAnActingPetBothLandInTheOneWrite() {
        // §18's standing rule made observable: two features mutating `inventory` are two in-memory
        // transformations before one `updateInventory` call, so both changes are in the stored blob.
        String inventory = battleWithStoredPet(bear(80), 90, true);

        assertTrue(inventory.contains("\"currentDurability\":19"), "the fired weapon wore: 20 → 19 (§17)");
        assertTrue(inventory.contains("\"currentHealth\":79"), "and the acting pet wore: 80 → 79 (§18)");
        assertTrue(inventory.contains("\"repair_token\":2"),
            "while the shop's consumables rode through untouched — a battle never spends them");
        assertFalse(inventory.contains("\"currentDurability\":20"), "the pre-battle durability is gone");
    }
}
