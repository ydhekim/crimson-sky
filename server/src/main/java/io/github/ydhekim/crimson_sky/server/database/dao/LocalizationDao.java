package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Map;

public interface LocalizationDao {

    @SqlQuery("SELECT lk.key_name, lv.text_value " +
        "FROM localization_keys lk " +
        "JOIN localization_values lv ON lk.id = lv.key_id " +
        "WHERE lv.lang_code = :langCode")
    @KeyColumn("key_name")
    @ValueColumn("text_value")
    Map<String, String> getTranslationsByLanguage(@Bind("langCode") String langCode);
}
