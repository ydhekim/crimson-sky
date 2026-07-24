package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;

import java.util.Optional;

/**
 * In-memory {@link AccountDao} that captures the JSON handed to {@link #updateSettings} so
 * {@code AccountServiceTest} can assert what AccountService serialized without a database. The other
 * gold/slot methods are no-ops — this fake exists only to inspect the settings write.
 */
public class FakeAccountDao implements AccountDao {

    private String lastSettingsJson;

    public String lastSettingsJson() {
        return lastSettingsJson;
    }

    @Override
    public boolean updateSettings(long accountId, String settingsJson) {
        this.lastSettingsJson = settingsJson;
        return true;
    }

    @Override
    public void addGlobalCurrency(long accountId, int goldDelta) {
    }

    @Override
    public void addCharacterSlots(long accountId, int delta) {
    }

    @Override
    public Optional<Long> getGlobalCurrency(long accountId) {
        return Optional.empty();
    }

    @Override
    public int spendGlobalCurrency(long accountId, long cost) {
        return 0;
    }
}
