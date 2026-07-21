package io.github.ydhekim.crimson_sky.common.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against review §3.3 regressing: every MessageCode must have a seeded localization key, so
 * LanguageManager.get(MessageCode) never silently falls back to a "!CODE!" placeholder in front of a
 * player. Reads the migration SQL directly rather than needing a live Postgres — simple regex extraction
 * of every 'KEY_NAME' inserted into localization_keys across the two migrations that seed MessageCode
 * content (V2 for CHAR_NAME_TAKEN, V20 for everything else), diffed against MessageCode.values().
 */
class MessageCodeLocalizationCoverageTest {

    @Test
    void everyMessageCodeHasASeededLocalizationKey() throws IOException {
        Set<String> seededKeys = new HashSet<>();
        seededKeys.addAll(extractKeyNames("/db/migration/V2__Localization_Setup.sql"));
        seededKeys.addAll(extractKeyNames("/db/migration/V20__Seed_Message_Code_Localizations.sql"));

        List<String> missing = Arrays.stream(MessageCode.values())
            .map(Enum::name)
            .filter(name -> !seededKeys.contains(name))
            .toList();

        assertTrue(missing.isEmpty(), "MessageCode values with no seeded localization: " + missing);
    }

    private Set<String> extractKeyNames(String classpathResource) throws IOException {
        String sql = new String(getClass().getResourceAsStream(classpathResource).readAllBytes());
        Matcher matcher = Pattern.compile("'([A-Z_]+)',\\s*'(?:ERROR|UI|ACHIEVEMENT)'").matcher(sql);
        Set<String> keys = new HashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
