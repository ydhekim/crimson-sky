package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.BotFactory;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story N2 / system design §17 — a weapon that fired loses <b>one</b> point of durability per battle,
 * written into the attacker's persisted {@code Inventory} (the single source of truth) inside the reward
 * transaction.
 *
 * <p>Split in two on purpose: {@link #firedWeaponIds} is a pure read of the battle log and is tested
 * directly against hand-built turn histories (the only way to pin "twice in one battle is still −1"
 * without a seed that happens to produce two hits), while the persistence half runs a real battle through
 * {@code RewardService} against H2.
 */
class RewardServiceDurabilityTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long ATTACKER = 1L;
    private static final long OPPONENT = 2L;

    private static final String LOADOUT_JSON =
        "{\"weapons\":[{\"id\":1,\"name\":\"Testing Hammer\",\"maxDurability\":20,\"currentDurability\":%d}],"
            + "\"skills\":[],\"pets\":[]}";

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
    }

    // --- the pure half: which weapons fired (§17's "at least once per battle") -------------------

    private static ResolvedAction weaponHit(long itemId) {
        return new ResolvedAction(ActionSource.WEAPON, "W" + itemId, 3, false, 40, itemId);
    }

    private static AttackResult resultWithTurns(Array<Array<ResolvedAction>> turns) {
        return new AttackResult(1L, ATTACKER, OPPONENT, "Boran", false, true, turns);
    }

    @Test
    void aWeaponFiringOnEveryTurnIsStillCountedOnce() {
        // The rule that makes this a Set and not a tally: durability is per battle, not per hit or per
        // repeat, so a long fight must not shred a weapon (§17). Three turns, frequency 3 each → still one.
        Array<Array<ResolvedAction>> turns = Array.with(
            Array.with(weaponHit(1L)), Array.with(weaponHit(1L)), Array.with(weaponHit(1L)));

        assertEquals(Set.of(1L), RewardService.firedWeaponIds(resultWithTurns(turns)));
    }

    @Test
    void everyDistinctWeaponThatFiredIsCounted() {
        // A pouch rotating on Stamina fires more than one weapon in a battle; each wears independently.
        Array<Array<ResolvedAction>> turns = Array.with(
            Array.with(weaponHit(3L)), Array.with(weaponHit(2L)), Array.with(weaponHit(3L)));

        assertEquals(Set.of(3L, 2L), RewardService.firedWeaponIds(resultWithTurns(turns)));
    }

    @Test
    void nonWeaponEntriesNeverWearAnything() {
        // Skills and pets carry an item id too (§17 populates it uniformly for Epic O), so the source
        // filter — not the presence of an id — is what keeps this weapons-only. Punch/Burned carry 0.
        Array<Array<ResolvedAction>> turns = Array.with(Array.with(
            new ResolvedAction(ActionSource.SKILL, "Spark", 2, false, 30, 5L),
            new ResolvedAction(ActionSource.PET, "Wolf", 1, false, 12, 9L),
            new ResolvedAction(ActionSource.PUNCH, "Punch", 1, false, 3, 0L),
            new ResolvedAction(ActionSource.SKILL, "FAILED_CAST", 1, true, 0, 0L)));

        assertTrue(RewardService.firedWeaponIds(resultWithTurns(turns)).isEmpty(),
            "a battle of skills, pets and punches wears no weapon");
    }

    // --- the persistence half: it lands in the stored inventory, inside the transaction ----------

    /**
     * A real attack + reward round trip, with the attacker's stored weapon seeded at
     * {@code startingDurability}. {@code CombatFixtures.character} has STR 100, so its weapon draws every
     * turn — the battle always fires id 1.
     *
     * @return the attacker's persisted inventory JSON afterwards
     */
    private String battleWithStoredDurability(int startingDurability) {
        String inventoryJson = String.format(LOADOUT_JSON, startingDurability);

        FakeCharacterDao dao = new FakeCharacterDao()
            .with(CombatFixtures.character(ATTACKER, ACCOUNT_A, "Ayla"), ACCOUNT_A, 1000)
            .with(CombatFixtures.character(OPPONENT, ACCOUNT_B, "Boran"), ACCOUNT_B, 1000);
        TestDatabase db = TestDatabase.create()
            .withAccount(ACCOUNT_A, 0L).withAccount(ACCOUNT_B, 0L)
            .withCharacter(ATTACKER, ACCOUNT_A, "Ayla", 0L, 1000, inventoryJson, inventoryJson)
            .withCharacter(OPPONENT, ACCOUNT_B, "Boran", 0L, 1000, inventoryJson, inventoryJson);

        CharacterService characterService = new CharacterService(dao);
        AttackService attackService = new AttackService(
            characterService, new BotFactory(new Random(42L)), db.jdbi().onDemand(BattleHistoryDao.class), new Random(42L));
        RewardService rewardService = new RewardService(db.jdbi(), characterService);

        Optional<AttackResult> result = attackService.attack(ATTACKER);
        assertTrue(result.isPresent(), "precondition: the battle resolves");
        assertEquals(Set.of(1L), RewardService.firedWeaponIds(result.get()),
            "precondition: the attacker's weapon really did fire this battle");
        rewardService.applyRewards(result.get());

        return db.inventoryJsonOf(ATTACKER);
    }

    @Test
    void aFiredWeaponWearsByExactlyOne() {
        assertTrue(battleWithStoredDurability(20).contains("\"currentDurability\":19"),
            "20 → 19 in the persisted inventory, however many hits the battle took");
    }

    @Test
    void durabilityNeverGoesBelowZero() {
        // The floor that matters: a weapon on its last point lands at 0 and stops, never −1, so
        // `currentDurability > 0` stays a meaningful "is it broken" check forever.
        assertTrue(battleWithStoredDurability(1).contains("\"currentDurability\":0"),
            "1 → 0, the weapon breaks");
        assertTrue(battleWithStoredDurability(1).contains("\"maxDurability\":20"),
            "and it remains repairable — max durability is untouched (Epic O prices against it)");
    }

    @Test
    void anAlreadyBrokenWeaponIsNotDrivenNegative() {
        // At 0 the weapon is unaffordable, so it never fires and there is nothing to decrement — but if it
        // somehow did, worn() floors at 0. Asserting the stored value directly covers both paths.
        assertTrue(battleWithStoredDurability(0).contains("\"currentDurability\":0"),
            "a broken weapon stays at 0 — never −1");
    }
}
