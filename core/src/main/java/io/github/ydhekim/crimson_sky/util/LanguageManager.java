package io.github.ydhekim.crimson_sky.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;

import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private static final String PREF_NAME = "CrimsonSkySettings";
    private static final String PREF_LANG_KEY = "language";

    private Map<String, String> translations = new HashMap<>();
    private String currentLang;

    public LanguageManager() {
        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);
        this.currentLang = prefs.getString(PREF_LANG_KEY, "en_US");
    }

    public void setTranslations(Map<String, String> newTranslations) {
        if (newTranslations != null) {
            System.out.println("Dil paketi yüklendi! Toplam anahtar: " + newTranslations.size());
            this.translations = newTranslations;
        } else {
            System.out.println("DİKKAT: Gelen dil paketi NULL!");
        }
    }

    public String get(String key) {
        return translations.getOrDefault(key, "!" + key + "!");
    }

    public String get(MessageCode code) {
        if (code == null) return "!";
        return get(code.name());
    }

    public String getCurrentLang() {
        return currentLang;
    }

    public void setCurrentLang(String langCode) {
        this.currentLang = langCode;
        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);
        prefs.putString(PREF_LANG_KEY, langCode);
        prefs.flush();
    }
}
