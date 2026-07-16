package io.github.ydhekim.crimson_sky.server.network.handler;

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
import io.github.ydhekim.crimson_sky.common.model.StatName;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveLoadoutRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveLoadoutResponse;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.FakeGameConnection;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System design §4.4/§16 — the loadout-save handler. Ownership/authentication failures are drops (the
 * same posture as the other character-scoped handlers); actionable validation failures (an unowned item,
 * too many skill slots) are answered so the client can explain the refusal. A valid save persists and
 * round-trips.
 */
class SaveLoadoutRequestHandlerTest {

    private static final long ACCOUNT_A = 10L;
    private static final long ACCOUNT_B = 20L;
    private static final long CHARACTER_A = 1L;
    private static final long CHARACTER_B = 2L;

    private static final Weapon SWORD = new Weapon(1L, "Sword", "", Rarity.COMMON, 2f, 10, 20, 5);
    private static final Skill IRON_GRIP = new Skill(1000L, "Iron Grip", "", SkillType.PASSIVE, 0,
        Difficulty.EASY, 0, 0, PassiveEffectType.STAT_BONUS, 2, StatName.STRENGTH);

    private FakeCharacterDao dao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        dao = new FakeCharacterDao();
    }

    /** A character owning exactly {@link #SWORD} and {@link #IRON_GRIP}, with nothing yet equipped. */
    private void seed(long characterId, long accountId) {
        Inventory inventory = new Inventory(Array.with(SWORD), Array.with(IRON_GRIP), new Array<>());
        Character c = new Character(characterId, accountId, "Ayla", Faction.A, 5, 0, 100, 100, 100, 10, 10,
            new Stats(5, 5, 5, 5, 5, 5, 5, 5), inventory,
            new Loadout(new Array<>(), new Array<>(), new Array<>()), new HashMap<>());
        dao.with(c, accountId, 1000);
    }

    private SaveLoadoutRequestHandler handler() {
        return new SaveLoadoutRequestHandler(new CharacterService(dao));
    }

    @Test
    void dropsASaveOnACharacterTheAccountDoesNotOwn() {
        seed(CHARACTER_B, ACCOUNT_B);
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        handler().handle(connection, new SaveLoadoutRequest(CHARACTER_B,
            new Loadout(Array.with(SWORD), new Array<>(), new Array<>())));

        assertTrue(connection.sentNothing(), "a non-owned character's loadout can never be saved — and it is not answered");
    }

    @Test
    void dropsAnUnauthenticatedSave() {
        seed(CHARACTER_A, ACCOUNT_A);
        FakeGameConnection anonymous = FakeGameConnection.unauthenticated(9);

        handler().handle(anonymous, new SaveLoadoutRequest(CHARACTER_A,
            new Loadout(Array.with(SWORD), new Array<>(), new Array<>())));

        assertTrue(anonymous.sentNothing(), "an unauthenticated save is dropped, never answered");
    }

    @Test
    void answersALoadoutReferencingAnUnownedItem() {
        seed(CHARACTER_A, ACCOUNT_A);
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        Weapon notOwned = new Weapon(99L, "Excalibur", "", Rarity.RARE, 2f, 50, 60, 5);
        handler().handle(connection, new SaveLoadoutRequest(CHARACTER_A,
            new Loadout(Array.with(notOwned), new Array<>(), new Array<>())));

        SaveLoadoutResponse response = connection.onlySentPacket(SaveLoadoutResponse.class);
        assertFalse(response.success());
        assertEquals(MessageCode.LOADOUT_ITEM_NOT_OWNED.name(), response.message());
    }

    @Test
    void answersALoadoutWithTooManySkillSlots() {
        seed(CHARACTER_A, ACCOUNT_A);
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        Array<Skill> sixSkills = new Array<>();
        for (int i = 0; i < 6; i++) {
            sixSkills.add(new Skill(2000L + i, "S" + i, "", SkillType.PASSIVE, 0, Difficulty.EASY, 0, 0,
                PassiveEffectType.CRIT_CHANCE_BONUS, 1, null));
        }
        handler().handle(connection, new SaveLoadoutRequest(CHARACTER_A,
            new Loadout(new Array<>(), sixSkills, new Array<>())));

        SaveLoadoutResponse response = connection.onlySentPacket(SaveLoadoutResponse.class);
        assertFalse(response.success());
        assertEquals(MessageCode.LOADOUT_SKILL_SLOTS_EXCEEDED.name(), response.message());
    }

    @Test
    void aValidSavePersistsAndRoundTrips() {
        seed(CHARACTER_A, ACCOUNT_A);
        FakeGameConnection connection = FakeGameConnection.authenticated(1, ACCOUNT_A);

        Loadout submitted = new Loadout(Array.with(SWORD), Array.with(IRON_GRIP), new Array<>());
        handler().handle(connection, new SaveLoadoutRequest(CHARACTER_A, submitted));

        SaveLoadoutResponse response = connection.onlySentPacket(SaveLoadoutResponse.class);
        assertTrue(response.success());
        assertEquals(1, response.savedLoadout().weapons().size);
        assertEquals(1000L, response.savedLoadout().skills().first().id());

        // Round-trips: the persisted loadout is what a subsequent character list would return.
        Character reloaded = new CharacterService(dao).getCharacters(ACCOUNT_A).data().get(0);
        assertEquals(1L, reloaded.loadout().weapons().first().id());
        assertEquals(1000L, reloaded.loadout().skills().first().id(), "the equipped passive persisted");
    }
}
