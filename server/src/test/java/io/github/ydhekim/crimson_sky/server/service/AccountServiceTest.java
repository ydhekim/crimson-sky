package io.github.ydhekim.crimson_sky.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
import io.github.ydhekim.crimson_sky.server.support.FakeAccountDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the volume-key serialization contract (prompt 31 / V24). AccountService must serialize
 * AccountSettings through a mapper that carries {@link ParameterNamesModule}, so the record's
 * constructor-parameter {@code @JsonProperty("volume_master")} is honoured. A bare
 * {@code new ObjectMapper()} silently falls back to the accessor name ({@code volumeMaster}), which is
 * how the duplicate camelCase key got written into {@code accounts.settings} in the first place.
 */
class AccountServiceTest {

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
    }

    @Test
    void serializesVolumeUnderSnakeCaseKey() {
        FakeAccountDao dao = new FakeAccountDao();
        ObjectMapper mapper = new ObjectMapper().registerModule(new ParameterNamesModule());
        AccountService service = new AccountService(dao, mapper);

        service.saveAccountSettings(1L, AccountSettings.createDefault());

        String json = dao.lastSettingsJson();
        assertTrue(json.contains("\"volume_master\""),
            "expected snake_case volume_master key, got: " + json);
        assertFalse(json.contains("volumeMaster"),
            "the camelCase accessor name must never be written (V24 duplicate-key bug), got: " + json);
    }
}
