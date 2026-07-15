package io.github.ydhekim.crimson_sky.server.support;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;

/**
 * Characters built so a turn's outcome is fixed regardless of the battle's (random) seed, which is
 * what lets these tests assert on damage without pinning a seed they don't own:
 * <ul>
 *   <li>STR 100 → the weapon-draw roll ({@code nextInt(100) < 100}) always succeeds;</li>
 *   <li>DEX 0 → frequency {@code 1 + 0/30} = exactly one hit;</li>
 *   <li>SPD 0 → 0% dodge, so the hit always lands;</li>
 *   <li>{@code baseDef} 0 and a flat {@code 100..100} weapon → mitigation factor 1, damage is
 *       {@code 100 + floor(100 * 0.5)} = 150 every time;</li>
 *   <li>500 HP → nobody dies on turn 1, so both sides resolve a Result Set.</li>
 * </ul>
 */
public final class CombatFixtures {

    /** Damage every hit deals under the fixture above: {@code 100 (flat range) + 50 (STR bonus)}. */
    public static final int EXPECTED_HIT_DAMAGE = 150;

    private CombatFixtures() {
    }

    /** A light (weight 2 → no STR draw penalty), affordable, flat-damage weapon. */
    public static Weapon flatWeapon() {
        return new Weapon(1L, "Testing Hammer", "", Rarity.COMMON, 2f, 100, 100, 5);
    }

    /**
     * A defenceless 1 HP opponent with no weapon, skill, or pet — it can only punch (1–5 damage), so
     * any fixture built by {@link #character} kills it on the first hit that lands.
     */
    public static Character frailCharacter(long id, long accountId, String name) {
        Stats stats = new Stats(10, 0, 1, 10, 0, 1, 0, 0);
        return new Character(
            id, accountId, name, Faction.A, 1, 0,
            1 /* maxHp */, 10 /* maxMp */, 10 /* maxStamina */, 0, 0,
            stats,
            new Inventory(new Array<>(), new Array<>(), new Array<>()),
            new Loadout(new Array<Weapon>(), new Array<Skill>(), new Array<Pet>()));
    }

    /**
     * The {@link #character} fixture, re-stamped at a given {@code level}/{@code experience} — for Epic L
     * tests that need a character starting near a level milestone without disturbing its (fixed-outcome)
     * combat stats.
     */
    public static Character characterAtLevel(long id, long accountId, String name, int level, long experience) {
        Character base = character(id, accountId, name);
        return new Character(base.id(), base.accountId(), base.name(), base.faction(), level, experience,
            base.maxHp(), base.maxMp(), base.maxStamina(), base.baseDef(), base.baseAtk(),
            base.stats(), base.inventory(), base.loadout());
    }

    /** A character guaranteed to land exactly one 150-damage weapon hit per turn. */
    public static Character character(long id, long accountId, String name) {
        Stats stats = new Stats(
            100 /* str → weapon draw always succeeds */,
            0 /* dex → frequency 1 */,
            50 /* vit */, 20 /* int */, 20 /* wis */, 50 /* spi */,
            0 /* spd → no dodge, no priority tiebreak ambiguity in damage terms */,
            0 /* ins → no pet roll matters; loadout has no pet anyway */);

        return new Character(
            id, accountId, name, Faction.A, 1, 0,
            500 /* maxHp */, 100 /* maxMp */, 100 /* maxStamina */,
            0 /* baseDef → full damage lands */, 0 /* baseAtk */,
            stats,
            new Inventory(new Array<>(), new Array<>(), new Array<>()),
            new Loadout(Array.with(flatWeapon()), new Array<Skill>(), new Array<Pet>()));
    }
}
