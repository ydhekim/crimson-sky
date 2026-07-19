package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.PassiveEffectType;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.StatName;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * System design §16/§17 — the passive aggregation extracted out of {@code BattleParticipant.fromCharacter}
 * so combat and {@code CharacterService.saveLoadout} read one implementation. {@code BattleParticipantPassivesTest}
 * is the other half of this pass's regression check: it exercises the same sums through the participant.
 */
class PassiveEffectsTest {

    private static final Stats BASE = new Stats(10, 10, 10, 10, 10, 10, 10, 10);

    private static Skill passive(long id, PassiveEffectType effect, int magnitude, StatName stat) {
        return new Skill(id, "Passive " + id, "", SkillType.PASSIVE, 0, Difficulty.EASY, 0, 0,
            effect, magnitude, stat, null, 0, 0, 0);
    }

    private static Skill active(long id) {
        return new Skill(id, "Active " + id, "", SkillType.ACTIVE, 10, Difficulty.EASY, 5, 10, null, 0, null, null, 0, 0, 0);
    }

    private static Loadout loadout(Array<Skill> skills) {
        return new Loadout(new Array<Weapon>(), skills, new Array<Pet>());
    }

    @Test
    void sumsEveryEquippedPassiveOfTheSameEffect() {
        Loadout loadout = loadout(Array.with(
            passive(1L, PassiveEffectType.WEIGHT_CAPACITY_BONUS, 10, null),
            passive(2L, PassiveEffectType.WEIGHT_CAPACITY_BONUS, 5, null),
            passive(3L, PassiveEffectType.CRIT_CHANCE_BONUS, 4, null)));

        assertEquals(15, PassiveEffects.totalWeightCapacityBonus(loadout), "10 + 5");
        assertEquals(4, PassiveEffects.totalCritChanceBonus(loadout));
        assertEquals(0, PassiveEffects.totalDodgeChanceBonus(loadout), "none equipped → 0, not a default");
    }

    @Test
    void activeSkillsAndMalformedPassivesContributeNothing() {
        // An ACTIVE skill carries meaningless passive fields (§16's Skill shape) — reading them as bonuses
        // would silently inflate every loadout, so type() is what gates, not the magnitude being non-zero.
        Skill activeWithJunk = new Skill(9L, "Spark", "", SkillType.ACTIVE, 12, Difficulty.EASY, 20, 40,
            PassiveEffectType.WEIGHT_CAPACITY_BONUS, 999, null, null, 0, 0, 0);
        Skill malformed = passive(10L, null, 50, null); // no effect type — nothing to fold

        Loadout loadout = loadout(Array.with(active(8L), activeWithJunk, malformed));

        assertEquals(0, PassiveEffects.totalWeightCapacityBonus(loadout));
        assertEquals(BASE, PassiveEffects.applyStatBonuses(BASE, loadout));
    }

    @Test
    void statBonusesFoldIntoTheirNamedStatOnly() {
        Loadout loadout = loadout(Array.with(
            passive(1L, PassiveEffectType.STAT_BONUS, 6, StatName.STRENGTH),
            passive(2L, PassiveEffectType.STAT_BONUS, 4, StatName.STRENGTH),
            passive(3L, PassiveEffectType.STAT_BONUS, 3, StatName.VITALITY)));

        Stats folded = PassiveEffects.applyStatBonuses(BASE, loadout);

        assertEquals(20, folded.strength(), "two STR passives stack (10 + 6 + 4)");
        assertEquals(13, folded.vitality());
        assertEquals(10, folded.dexterity(), "an unnamed stat is untouched");
        assertEquals(BASE, BASE, "the input Stats record is never mutated — it's a record");
    }

    @Test
    void anEmptyOrNullLoadoutIsZeroEverywhere() {
        // saveLoadout passes whatever the client submitted, including a loadout with no skills array at all.
        Loadout nullSkills = new Loadout(new Array<>(), null, new Array<>());

        assertEquals(0, PassiveEffects.totalWeightCapacityBonus(nullSkills));
        assertEquals(0, PassiveEffects.totalWeightCapacityBonus(null));
        assertEquals(BASE, PassiveEffects.applyStatBonuses(BASE, nullSkills));
        assertEquals(0, PassiveEffects.totalDodgeChanceBonus(loadout(new Array<>())));
    }
}
