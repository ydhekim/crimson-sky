package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.PassiveEffectType;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.StatName;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.ecs.component.PassiveModifiersComponent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * System design §16: equipped {@code PASSIVE} skills are read once at
 * {@link BattleParticipant#fromCharacter}. {@code STAT_BONUS} passives fold into the {@code Stats}
 * block; the flat-knob effects accumulate onto {@link PassiveModifiersComponent}. A character with no
 * passives equipped is unchanged from before this pass.
 */
class BattleParticipantPassivesTest {

    private static final Stats BASE = new Stats(10, 10, 10, 10, 10, 10, 10, 10);

    private static Skill statPassive(long id, StatName stat, int magnitude) {
        return new Skill(id, "Passive " + id, "", SkillType.PASSIVE, 0, Difficulty.EASY, 0, 0,
            PassiveEffectType.STAT_BONUS, magnitude, stat);
    }

    private static Skill flatPassive(long id, PassiveEffectType effect, int magnitude) {
        return new Skill(id, "Passive " + id, "", SkillType.PASSIVE, 0, Difficulty.EASY, 0, 0,
            effect, magnitude, null);
    }

    private static Character character(Array<Skill> skills) {
        return new Character(1L, 1L, "Ayla", Faction.A, 1, 0, 100, 100, 100, 0, 0, BASE,
            new Inventory(new Array<>(), new Array<>(), new Array<>()),
            new Loadout(new Array<Weapon>(), skills, new Array<Pet>()),
            new HashMap<>());
    }

    @Test
    void equippedPassivesFoldIntoStatsAndModifiers() {
        // Two STAT_BONUS passives on different stats, plus one DODGE_CHANCE_BONUS.
        Array<Skill> skills = Array.with(
            statPassive(1000L, StatName.STRENGTH, 6),
            statPassive(1001L, StatName.VITALITY, 4),
            flatPassive(1002L, PassiveEffectType.DODGE_CHANCE_BONUS, 3));

        BattleParticipant p = BattleParticipant.fromCharacter(new Engine(), character(skills));

        Stats stats = p.statsComponent().stats;
        assertEquals(16, stats.strength(), "STR passive folded in (10 + 6)");
        assertEquals(14, stats.vitality(), "VIT passive folded in (10 + 4)");
        assertEquals(10, stats.dexterity(), "an untouched stat is unchanged");

        PassiveModifiersComponent mods = p.passiveModifiers();
        assertEquals(3, mods.dodgeChanceBonus, "the dodge passive accumulated");
        assertEquals(0, mods.critChanceBonus, "no crit passive equipped");
    }

    @Test
    void critAndDodgePassivesAccumulateIndependently() {
        Array<Skill> skills = Array.with(
            flatPassive(1003L, PassiveEffectType.CRIT_CHANCE_BONUS, 5),
            flatPassive(1004L, PassiveEffectType.CRIT_CHANCE_BONUS, 3),
            flatPassive(1005L, PassiveEffectType.DODGE_CHANCE_BONUS, 5));

        BattleParticipant p = BattleParticipant.fromCharacter(new Engine(), character(skills));

        PassiveModifiersComponent mods = p.passiveModifiers();
        assertEquals(8, mods.critChanceBonus, "two crit passives sum (5 + 3)");
        assertEquals(5, mods.dodgeChanceBonus);
    }

    @Test
    void noPassivesEquippedGivesAnAllZeroComponentAndUnchangedStats() {
        BattleParticipant p = BattleParticipant.fromCharacter(new Engine(), character(new Array<>()));

        PassiveModifiersComponent mods = p.passiveModifiers();
        assertEquals(0, mods.dodgeChanceBonus);
        assertEquals(0, mods.critChanceBonus);
        assertEquals(0, mods.resourceCostReduction);
        assertEquals(0, mods.weightCapacityBonus);
        assertEquals(BASE, p.statsComponent().stats, "no passives → stats identical to before this pass");
    }
}
