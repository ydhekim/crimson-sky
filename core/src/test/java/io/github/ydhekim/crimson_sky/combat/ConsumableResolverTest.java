package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.ResourceType;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story O2 / system design §18 — the potion check: the first equipped potion, in priority order, whose
 * resource has dropped to its threshold and which still has a charge left. Every pool is passed explicitly
 * so each case pins exactly one reason to trigger (or not).
 */
class ConsumableResolverTest {

    /** A potion is authored, never computed: name, threshold and flat restore amount are all its own (§18). */
    private static Skill potion(long id, String name, ResourceType resource, int thresholdPercent,
                                int restoreAmount, int charges) {
        return new Skill(id, name, "", SkillType.CONSUMABLE, 0, Difficulty.EASY, 0, 0,
            null, 0, null, resource, thresholdPercent, restoreAmount, charges);
    }

    private static Skill healthPotion(int thresholdPercent, int charges) {
        return potion(100L, "Small Health Potion", ResourceType.HEALTH, thresholdPercent, 100, charges);
    }

    private static Skill manaPotion(int thresholdPercent, int charges) {
        return potion(200L, "Small Mana Potion", ResourceType.MANA, thresholdPercent, 50, charges);
    }

    /** Full pools throughout except the ones a case deliberately drains: HP 500/500, MP 100/100, SP 100/100. */
    private static ConsumableActionResolution choose(int currentHealth, int currentMana, int currentStamina,
                                                     Array<Skill> equipped, IntArray charges) {
        return ConsumableResolver.chooseConsumable(currentHealth, 500, currentMana, 100,
            currentStamina, 100, equipped, charges);
    }

    @Test
    void aPotionFiresWhenItsResourceHitsTheThreshold() {
        ConsumableActionResolution res = choose(150, 100, 100,
            Array.with(healthPotion(50, 3)), IntArray.with(3));

        assertNotNull(res, "150/500 is below 50% → the potion drinks");
        assertEquals(0, res.equippedIndex(), "the pouch slot, so the caller knows whose charge to burn");
        assertEquals(ResourceType.HEALTH, res.resource());
        assertEquals(100, res.restoreAmount(), "flat, straight off the potion — nothing rolled (§18)");

        assertEquals(ActionSource.CONSUMABLE, res.action().source());
        assertEquals("Small Health Potion", res.action().label());
        assertEquals(1, res.action().frequency(), "a potion is drunk once, never repeated by DEX");
        assertFalse(res.action().failed());
        assertEquals(100, res.action().damage(),
            "for CONSUMABLE, `damage` is the amount restored — filled in immediately, not by applyEntry");
        assertEquals(100L, res.action().itemId(), "the entry names which potion drank, for the charge tally");
    }

    @Test
    void aHealthyResourceTriggersNothing() {
        assertNull(choose(500, 100, 100, Array.with(healthPotion(50, 3)), IntArray.with(3)),
            "full HP, so the cascade proceeds completely unchanged");
    }

    @Test
    void noPotionEquippedTriggersNothing() {
        assertNull(choose(1, 0, 0, new Array<>(), new IntArray()),
            "every pool at rock bottom, but an empty pouch has nothing to offer");
    }

    @Test
    void theFirstTriggeringPotionWinsEvenWhenItIsNotSlotZero() {
        // Priority order is what the pouch means (§18/§4.4) — but a potion whose resource is fine is not a
        // candidate at all, so slot 1 fires here despite slot 0 sitting ahead of it.
        Array<Skill> pouch = Array.with(healthPotion(50, 3), manaPotion(50, 3));

        ConsumableActionResolution res = choose(500 /* HP full → slot 0 idle */, 10 /* MP 10% */, 100,
            pouch, IntArray.with(3, 3));

        assertNotNull(res);
        assertEquals(1, res.equippedIndex());
        assertEquals(ResourceType.MANA, res.resource());
        assertEquals("Small Mana Potion", res.action().label());
    }

    @Test
    void whenTwoPotionsCouldFireThePriorityOrderDecides() {
        // Both resources are low; only slot 0 drinks. The turn's action is singular, so priority is a real
        // choice and not a tiebreak nobody notices.
        Array<Skill> pouch = Array.with(healthPotion(50, 3), manaPotion(50, 3));

        ConsumableActionResolution res = choose(10, 10, 100, pouch, IntArray.with(3, 3));

        assertEquals(0, res.equippedIndex(), "slot 0 is checked first and it triggered");
        assertEquals(ResourceType.HEALTH, res.resource());
    }

    @Test
    void aSpentPotionIsSkippedEvenWithItsResourceOnTheFloor() {
        // The `charges == 0` gate (§18): there is no repair for a spent potion, so it is simply out of the
        // running forever — exactly how a broken weapon is skipped rather than specially handled.
        assertNull(choose(1, 100, 100, Array.with(healthPotion(50, 0)), IntArray.with(0)),
            "1 HP of 500 and a Health potion equipped, but it has nothing left to give");
    }

    @Test
    void aSpentPotionIsSkippedInFavorOfTheNextOneThatCanStillFire() {
        // The rotation the empty case above can't show: an exhausted slot 0 doesn't block the pouch.
        Array<Skill> pouch = Array.with(
            healthPotion(50, 0), potion(101L, "Large Health Potion", ResourceType.HEALTH, 50, 300, 1));

        ConsumableActionResolution res = choose(100, 100, 100, pouch, IntArray.with(0, 1));

        assertEquals(1, res.equippedIndex(), "slot 0 is spent → walk on, like an unaffordable weapon");
        assertEquals(300, res.restoreAmount());
    }

    @Test
    void theBattleScopedChargeCountDecides_notTheRecordsOwn() {
        // `remainingCharges` is what mid-battle depletion writes to; the Skill record is immutable and still
        // says 3. If the resolver read the record, a 3-charge potion could heal forever in one long fight.
        assertNull(choose(1, 100, 100, Array.with(healthPotion(50, 3)), IntArray.with(0)),
            "the pouch's live count is authoritative within a battle, not Skill.charges()");
    }

    @Test
    void everyResourceTypeIsCheckedAgainstItsOwnPool() {
        // A Stamina potion must read Stamina — a switch that fell through to HP would still "work" on the
        // health cases above, so this is the case that makes the mapping real.
        Skill staminaPotion = potion(300L, "Stamina Draught", ResourceType.STAMINA, 25, 40, 2);

        assertNull(choose(500, 100, 100, Array.with(staminaPotion), IntArray.with(2)), "full stamina → idle");
        assertNotNull(choose(500, 100, 25, Array.with(staminaPotion), IntArray.with(2)),
            "25/100 is exactly the 25% threshold → drinks");
    }

    @Test
    void aMalformedPotionNamingNoResourceRestoresNothingRatherThanThrowing() {
        // An equipped Skill arrives from a client-submitted Loadout, so a CONSUMABLE with a null
        // restoresResource is a shape the server can genuinely be handed. It must cost that player a potion
        // that never fires — not NPE the switch and take the whole battle down with it.
        Skill malformed = potion(400L, "Mystery Flask", null, 50, 100, 3);

        assertNull(choose(1, 1, 1, Array.with(malformed), IntArray.with(3)),
            "every pool on the floor, and it still declines");
    }

    @Test
    void aMalformedPotionDoesNotBlockAGoodOneBehindIt() {
        // …and it is skipped, not treated as the end of the pouch — otherwise one bad record would silently
        // disable every potion ranked below it.
        Array<Skill> pouch = Array.with(potion(400L, "Mystery Flask", null, 50, 100, 3), healthPotion(50, 3));

        ConsumableActionResolution res = choose(100, 100, 100, pouch, IntArray.with(3, 3));

        assertNotNull(res);
        assertEquals(1, res.equippedIndex());
    }

    @Test
    void resolvingAPotionNeedsNoRandomness() {
        // Structural, not behavioral (§18): a potion's threshold is a comparison and its restore amount is
        // flat, so this is the one branch of the cascade that draws nothing from the battle's shared RNG.
        // If a signature ever takes one, a potion-carrying build silently desyncs from a potion-less one.
        for (Method method : ConsumableResolver.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(SplittableRandom.class.isAssignableFrom(parameter),
                    "ConsumableResolver." + method.getName() + " takes an RNG — a potion must roll nothing (§18)");
            }
        }
        assertTrue(ConsumableResolver.class.getDeclaredMethods().length > 0,
            "otherwise this guard is vacuous");
    }
}
