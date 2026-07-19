package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story O2 / system design §18 — how many times each potion triggered this battle, read back off the
 * attacker's turn log. The sibling of {@code RewardServiceDurabilityTest}/{@code RewardServicePetHealthTest}'s
 * pure halves, and deliberately the odd one out: those two ask <b>whether</b> an item acted (a set, because
 * wear is per battle), this one asks <b>how many times</b> (a tally, because a charge is spent per trigger).
 * That distinction is the whole reason potions needed their own read rather than reusing
 * {@code firedWeaponIds}' shape, so it is what these cases pin.
 *
 * <p>Hand-built turn histories, for the same reason the siblings use them: pinning "three triggers cost
 * three charges" against a real battle would mean finding a seed that happens to produce exactly three.
 */
class RewardServiceConsumableChargesTest {

    private static final long ATTACKER = 1L;
    private static final long OPPONENT = 2L;
    private static final long SMALL_POTION = 100L;
    private static final long LARGE_POTION = 101L;

    private static ResolvedAction potionSip(long itemId) {
        return new ResolvedAction(ActionSource.CONSUMABLE, "Small Health Potion", 1, false, 100, itemId);
    }

    private static AttackResult resultWithTurns(Array<Array<ResolvedAction>> turns) {
        return new AttackResult(1L, ATTACKER, OPPONENT, "Boran", false, true, turns);
    }

    @Test
    void aPotionTriggeringOnEveryTurnIsCountedEveryTime() {
        // The rule that makes this a tally and not a Set — §18's one deliberate divergence from the shape
        // durability and pet health share. Get this wrong and a potion is effectively infinite.
        Array<Array<ResolvedAction>> turns = Array.with(
            Array.with(potionSip(SMALL_POTION)),
            Array.with(potionSip(SMALL_POTION)),
            Array.with(potionSip(SMALL_POTION)));

        assertEquals(Map.of(SMALL_POTION, 3), RewardService.consumableTriggerCounts(resultWithTurns(turns)));
    }

    @Test
    void eachPotionIsTalliedAgainstItsOwnId() {
        // Two potions in one pouch, drunk a different number of times — a tally keyed by id, not a single
        // running count that would spend the wrong flask.
        Array<Array<ResolvedAction>> turns = Array.with(
            Array.with(potionSip(SMALL_POTION)),
            Array.with(potionSip(LARGE_POTION)),
            Array.with(potionSip(SMALL_POTION)));

        assertEquals(Map.of(SMALL_POTION, 2, LARGE_POTION, 1),
            RewardService.consumableTriggerCounts(resultWithTurns(turns)));
    }

    @Test
    void aBattleWithNoPotionTurnsTalliesNothing() {
        // Weapons, skills and pets all carry an item id too (§17's ResolvedAction.itemId), so the source
        // filter — not the presence of an id — is what keeps this potions-only.
        Array<Array<ResolvedAction>> turns = Array.with(Array.with(
            new ResolvedAction(ActionSource.WEAPON, "Hammer", 1, false, 40, 1L),
            new ResolvedAction(ActionSource.SKILL, "Spark", 2, false, 30, 5L),
            new ResolvedAction(ActionSource.PET, "Bear", 3, false, 40, 7L),
            new ResolvedAction(ActionSource.PUNCH, "Punch", 1, false, 3, 0L)));

        assertTrue(RewardService.consumableTriggerCounts(resultWithTurns(turns)).isEmpty(),
            "a battle of weapons, skills, pets and punches spends no charges");
    }

    @Test
    void aBattleWithNoTurnsAtAllTalliesNothing() {
        assertTrue(RewardService.consumableTriggerCounts(
            new AttackResult(1L, ATTACKER, OPPONENT, "Boran", false, true, null)).isEmpty(),
            "a null turn log is empty, not an NPE — the same tolerance its two siblings carry");
    }
}
