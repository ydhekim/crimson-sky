package io.github.ydhekim.crimson_sky.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The curated-set validation behind character customization (system design §23). Pure — no DB, no Gdx: the
 * four constant lists on {@link Appearance} are the single source of truth and {@link Appearance#isValid()}
 * is the server-side gate that both the client's button UI and {@code CharacterService.createCharacter}
 * rely on, so it is pinned in isolation here.
 */
class AppearanceTest {

    @Test
    void validWhenEveryFieldIsAMemberOfItsCuratedList() {
        assertTrue(new Appearance("MALE", "SHORT", "BLACK", "TAN").isValid());
    }

    @Test
    void invalidWhenGenderIsOutsideItsList() {
        assertFalse(new Appearance("OTHER", "SHORT", "BLACK", "TAN").isValid());
    }

    @Test
    void invalidWhenHairTypeIsOutsideItsList() {
        assertFalse(new Appearance("MALE", "MOHAWK", "BLACK", "TAN").isValid());
    }

    @Test
    void invalidWhenHairColorIsOutsideItsList() {
        assertFalse(new Appearance("MALE", "SHORT", "PURPLE", "TAN").isValid());
    }

    @Test
    void invalidWhenSkinColorIsOutsideItsList() {
        assertFalse(new Appearance("MALE", "SHORT", "BLACK", "GREEN").isValid());
    }

    @Test
    void invalidWhenAnyFieldIsNull() {
        assertFalse(new Appearance(null, "SHORT", "BLACK", "TAN").isValid());
        assertFalse(new Appearance("MALE", null, "BLACK", "TAN").isValid());
        assertFalse(new Appearance("MALE", "SHORT", null, "TAN").isValid());
        assertFalse(new Appearance("MALE", "SHORT", "BLACK", null).isValid());
    }
}
