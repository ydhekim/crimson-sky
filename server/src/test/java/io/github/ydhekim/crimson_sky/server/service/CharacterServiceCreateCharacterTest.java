package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.Appearance;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Character creation with a cosmetic appearance (Epic T1 / system design §23), against a real (in-memory)
 * database — {@code createCharacter} had no dedicated test before this. Confirms a valid appearance persists
 * and round-trips, that a value outside a curated list (or a {@code null} appearance) is rejected with
 * {@code CHAR_INVALID_APPEARANCE} and inserts no row, and that the new check was <i>added</i> ahead of — not
 * substituted for — the pre-existing slot-cap check.
 */
class CharacterServiceCreateCharacterTest {

    private static final long ACCOUNT = 10L;
    private static final int MAX_SLOTS = 3;
    private static final Appearance VALID = new Appearance("MALE", "SHORT", "BLACK", "TAN");

    private TestDatabase db;
    private CharacterDao characterDao;
    private CharacterService service;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create().withAccount(ACCOUNT, 0L);
        characterDao = db.jdbi().onDemand(CharacterDao.class);
        service = new CharacterService(characterDao);
    }

    /** A fresh, level-1 character with an empty inventory/loadout — only its name varies across tests. */
    private static Character character(String name) {
        return new Character(0, 0, name, Faction.A, 1, 0, 100, 100, 100, 10, 10,
            new Stats(5, 5, 5, 5, 5, 5, 5, 5),
            new Inventory(null, null, null, new HashMap<>()),
            new Loadout(null, null, null),
            new HashMap<>());
    }

    @Test
    void aValidAppearancePersistsAndRoundTrips() {
        var result = service.createCharacter(ACCOUNT, MAX_SLOTS, character("Ayla"), VALID);

        assertTrue(result.success());
        long id = result.data();

        // Read straight from the column: all four chosen values are in the stored blob.
        String storedJson = db.appearanceOf(id);
        assertTrue(storedJson.contains("MALE"), storedJson);
        assertTrue(storedJson.contains("SHORT"), storedJson);
        assertTrue(storedJson.contains("BLACK"), storedJson);
        assertTrue(storedJson.contains("TAN"), storedJson);

        // And through the @Json read mapper, the same four values reconstruct the record exactly.
        assertEquals(VALID, characterDao.findById(id).orElseThrow().appearance());
    }

    @Test
    void rejectsAnAppearanceWithAFieldOutsideItsCuratedList() {
        Appearance bad = new Appearance("OTHER", "SHORT", "BLACK", "TAN");

        var result = service.createCharacter(ACCOUNT, MAX_SLOTS, character("Ayla"), bad);

        assertFalse(result.success());
        assertEquals(MessageCode.CHAR_INVALID_APPEARANCE, result.code());
        assertEquals(0, characterDao.getCharacterCount(ACCOUNT), "an invalid appearance inserts no row");
    }

    @Test
    void rejectsANullAppearanceRatherThanThrowing() {
        var result = service.createCharacter(ACCOUNT, MAX_SLOTS, character("Ayla"), null);

        assertFalse(result.success());
        assertEquals(MessageCode.CHAR_INVALID_APPEARANCE, result.code());
        assertEquals(0, characterDao.getCharacterCount(ACCOUNT), "a null appearance inserts no row");
    }

    @Test
    void aValidAppearanceStillHitsTheSlotCapCheck() {
        // Fill the account to its cap with valid creations, then one more with a valid appearance: it must
        // still fail on slots, proving the appearance check was added ahead of the slot check, not swapped in.
        for (int i = 0; i < MAX_SLOTS; i++) {
            assertTrue(service.createCharacter(ACCOUNT, MAX_SLOTS, character("Hero" + i), VALID).success());
        }

        var result = service.createCharacter(ACCOUNT, MAX_SLOTS, character("Overflow"), VALID);

        assertFalse(result.success());
        assertEquals(MessageCode.CHAR_MAX_SLOTS_REACHED, result.code());
    }
}
