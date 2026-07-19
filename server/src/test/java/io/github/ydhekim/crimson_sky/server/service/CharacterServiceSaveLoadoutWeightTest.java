package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.PassiveEffectType;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story N1 / system design §17 — the <b>hard</b> weight gate on loadout saves:
 * {@code maxCarryWeight = STR × 3 + WEIGHT_CAPACITY_BONUS}, enforced across the whole weapon pouch.
 *
 * <p>Distinct from §4.3's soft per-item {@code comfortableWeight} penalty in
 * {@code CombatMath.effectiveStrength}, which is untouched by this pass: that one still reduces the draw
 * roll for an individual heavy weapon <i>inside</i> a pouch that fits this total budget. This one refuses
 * the save outright.
 *
 * <p>STR 10 throughout → a 30.0 budget, so the arithmetic in each case is checkable by eye.
 */
class CharacterServiceSaveLoadoutWeightTest {

    private static final long ACCOUNT = 10L;
    private static final long CHARACTER = 1L;

    /** Three weapons at weight 12: two fit a STR-10 budget of 30, three (36) do not. */
    private static Weapon weapon(long id) {
        return new Weapon(id, "Weapon " + id, "", Rarity.COMMON, 12f, 10, 20, 5, 20, 20);
    }

    /** A passive worth +10 carry capacity — enough to admit a third 12-weight weapon (30 → 40 vs 36). */
    private static final Skill PACK_MULE = new Skill(1000L, "Pack Mule", "", SkillType.PASSIVE, 0,
        Difficulty.EASY, 0, 0, PassiveEffectType.WEIGHT_CAPACITY_BONUS, 10, null, null, 0, 0, 0);

    private FakeCharacterDao dao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        dao = new FakeCharacterDao();

        // Owns everything it could equip, so the ownership check (which runs *after* the weight gate)
        // can never be what rejects a save here.
        Inventory inventory = new Inventory(
            Array.with(weapon(1L), weapon(2L), weapon(3L)), Array.with(PACK_MULE), new Array<>(),
            new HashMap<>());
        Character c = new Character(CHARACTER, ACCOUNT, "Ayla", Faction.A, 5, 0, 100, 100, 100, 0, 0,
            new Stats(10 /* STR → 30.0 carry budget */, 5, 5, 5, 5, 5, 5, 5), inventory,
            new Loadout(new Array<>(), new Array<>(), new Array<>()), new HashMap<>());
        dao.with(c, ACCOUNT, 1000);
    }

    private ServiceResult<Loadout> save(Loadout loadout) {
        return new CharacterService(dao).saveLoadout(ACCOUNT, CHARACTER, loadout);
    }

    private static Loadout loadout(Array<Weapon> weapons, Array<Skill> skills) {
        return new Loadout(weapons, skills, new Array<Pet>());
    }

    @Test
    void rejectsAPouchHeavierThanStrengthAllows() {
        // 3 × 12 = 36 > 30. Each weapon fits alone; it is the *total* that busts the budget (§17).
        ServiceResult<Loadout> result = save(
            loadout(Array.with(weapon(1L), weapon(2L), weapon(3L)), new Array<>()));

        assertFalse(result.success());
        assertEquals(MessageCode.LOADOUT_WEIGHT_EXCEEDED, result.code());
    }

    @Test
    void savesAPouchAtOrUnderTheCap() {
        // 2 × 12 = 24 ≤ 30.
        ServiceResult<Loadout> result = save(loadout(Array.with(weapon(1L), weapon(2L)), new Array<>()));

        assertTrue(result.success());
        assertEquals(2, result.data().weapons().size);
    }

    @Test
    void aWeightCapacityPassiveAdmitsAPouchTheStrengthOnlyCapWouldRefuse() {
        // The exact pouch rejected above (36), now saved — the only difference is the equipped passive
        // funding it (30 + 10 = 40). This is what makes PassiveEffects shared rather than combat-only:
        // the bonus has to be readable with no Ashley Engine in sight.
        ServiceResult<Loadout> result = save(
            loadout(Array.with(weapon(1L), weapon(2L), weapon(3L)), Array.with(PACK_MULE)));

        assertTrue(result.success(), "an equipped WEIGHT_CAPACITY_BONUS raises the budget past the pouch");
        assertEquals(3, result.data().weapons().size);
    }

    @Test
    void weightIsCheckedOnWeaponsOnly() {
        // Skills and pets have no weight dimension in the data model — five skills can never bust the
        // budget, so an empty-weapon loadout always fits regardless of what else is equipped (§17).
        Array<Skill> skills = new Array<>();
        for (int i = 0; i < 5; i++) {
            skills.add(new Skill(2000L + i, "S" + i, "", SkillType.PASSIVE, 0, Difficulty.EASY, 0, 0,
                PassiveEffectType.CRIT_CHANCE_BONUS, 1, null, null, 0, 0, 0));
        }
        // Not owned, so this would fail the *ownership* check — proving weight didn't reject it first.
        ServiceResult<Loadout> result = save(loadout(new Array<>(), skills));

        assertEquals(MessageCode.LOADOUT_ITEM_NOT_OWNED, result.code(),
            "a weaponless loadout passes the weight gate and falls through to the next check");
    }

    @Test
    void theCapIsInclusive() {
        // Exactly 30.0 against a 30 budget must save: the gate is `>`, not `>=` — a character who can
        // carry 30 can carry 30.
        Weapon exact = new Weapon(1L, "Exact", "", Rarity.COMMON, 30f, 10, 20, 5, 20, 20);
        dao = new FakeCharacterDao();
        Character c = new Character(CHARACTER, ACCOUNT, "Ayla", Faction.A, 5, 0, 100, 100, 100, 0, 0,
            new Stats(10, 5, 5, 5, 5, 5, 5, 5),
            new Inventory(Array.with(exact), new Array<>(), new Array<>(), new java.util.HashMap<>()),
            new Loadout(new Array<>(), new Array<>(), new Array<>()), new HashMap<>());
        dao.with(c, ACCOUNT, 1000);

        assertTrue(save(loadout(Array.with(exact), new Array<>())).success());
    }
}
