# Implementation prompt — Settings Save shows stale value until restart (K5)

Regression found immediately after K4/prompt 31 merged: change a value in Settings (e.g. volume 50 → 0), hit Save — the control snaps back to the *old* value (50) instead of showing what was just saved. Restart the client and log in again and it correctly shows 0. So the save itself works; only the in-session UI feedback is wrong.

## Root cause

Grounded against the current `SettingsScreen.java`/`BaseScreen.java`:

- `SettingsScreen.saveSettings()` builds a new `AccountSettings` from the current controls and sends `SaveAccountSettingsRequest` — but never updates `CrimsonSky.accountSettings` (the client-side cache K4 added) with those values.
- `onSaveAccountSettingsResponse()` always follows a successful save by sending a `LocalizationRequest`, regardless of whether the language actually changed.
- `BaseScreen.onLocalizationResponse()` (`BaseScreen.java:146-150`) calls `Gdx.app.postRunnable(this::refreshUI)` on *any* successful localization response.
- `SettingsScreen.refreshUI()` calls `setupUI()`, which rebuilds every control by reading `game.getAccountSettings()` — still the object loaded at login, since nothing updated it after the save.

So the save round-trip to the DB is correct; the client just re-renders itself from a cache that was never told about the save. A fresh `LoginResponse` (on restart) is the only thing currently capable of updating that cache.

## Fix

In `SettingsScreen.saveSettings()`, update the client-side cache immediately after constructing the `AccountSettings` object — the same optimistic-update pattern already used one line above it for `game.getLanguageManager().setCurrentLang(selectedLangCode)`:

```java
private void saveSettings() {
    String selectedLanguageName = languageSelectBox.getSelected();
    String selectedLangCode = languageMap.get(selectedLanguageName);
    String selectedResolution = resolutionSelectBox.getSelected();

    AccountSettings accountSettings = new AccountSettings(
        volumeSlider.getValue(),
        selectedLangCode,
        fullscreenCheckBox.isChecked(),
        selectedResolution);

    game.getLanguageManager().setCurrentLang(selectedLangCode);
    game.setAccountSettings(accountSettings);   // new line — keep the cache in sync with what was just saved
    game.getNetworkClient().sendTCP(new SaveAccountSettingsRequest(accountSettings));
}
```

This is deliberately optimistic (updates before the server ack arrives), matching how the language field already behaves. If `onSaveAccountSettingsResponse()` ever comes back `!success()`, the cache and the DB will disagree until the next login — that gap already exists today for language and isn't being changed here; call it out as a known limitation rather than solving it in this pass (would need a rollback path on failure, which is more machinery than this bug warrants).

## Testing / Definition of Done

1. Change volume, hit Save, confirm the slider stays at the new value (no snap-back) instead of reverting.
2. Change resolution and/or fullscreen, hit Save, confirm the same — no revert.
3. Change language, hit Save, confirm the screen still refreshes correctly in the new language (this is the existing, unchanged localization-refresh path — just confirm it isn't broken by the new line).
4. Fully restart the client, log back in, confirm all values still match what was last saved (K4's existing coverage, just re-confirm it's undisturbed).

Definition of done: Save reflects the just-saved values immediately, with no dependency on a restart; language-change refresh behavior is unchanged.
