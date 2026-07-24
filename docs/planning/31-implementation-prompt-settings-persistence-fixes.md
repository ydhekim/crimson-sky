# Implementation prompt — fix Settings persistence path (K4)

Four defects found in the shipped Settings screen (prompt 29) via a live screenshot + a direct look at `accounts.settings` in Postgres. All four are grounded below against the current code — none of this is speculative.

Row observed: `settings = {"language": "en_US", "fullscreen": false, "resolution": "1280x720", "volumeMaster": 0.8..., "volume_master": 0.35...}`.

## 1. Stray `volumeMaster` key (duplicate of `volume_master`)

Root cause: `AccountSettings` (`common/model/AccountSettings.java`) annotates its record component `@JsonProperty("volume_master")`, but `AccountService.saveAccountSettings()` (`server/service/AccountService.java:17`) serializes with a bare `new ObjectMapper()` that has none of the modules the *real* mapper has. `DatabaseManager.init()` (`server/database/DatabaseManager.java:72-78`) already builds a correctly configured one — `Jdk8Module` + `JavaTimeModule` + `ParameterNamesModule` + `FAIL_ON_UNKNOWN_PROPERTIES=false` — and installs it into `Jackson2Config` for JDBI's own `@Json` column reads. `AccountService`'s private mapper doesn't have `ParameterNamesModule`, so when it serializes the record it falls back to the accessor method name (`volumeMaster()`) instead of the constructor parameter's `@JsonProperty`. Every other field's JSON name happens to match its accessor name, so only `volumeMaster` shows the mismatch. `AccountDao.updateSettings()` does a shallow `COALESCE(...) || :settingsJson::jsonb` merge — a differently-spelled key doesn't overwrite the old one, it just sits alongside it forever.

Fix: stop constructing a second, unconfigured `ObjectMapper` in `AccountService`. Reuse the one `DatabaseManager` already builds.

- Add a getter to `DatabaseManager`: expose the configured `ObjectMapper` (e.g. `public ObjectMapper getObjectMapper()`, storing the local variable at `DatabaseManager.java:72` as a field instead of a local).
- `AccountService`'s constructor should take that `ObjectMapper` as a parameter (constructor injection, matching the project's manual-DI convention) instead of `new ObjectMapper()`. Wire it through wherever `AccountService` is constructed (check `ServiceRegistry`).
- Data cleanup migration, `server/src/main/resources/db/migration/V24__Cleanup_Duplicate_Volume_Master_Key.sql`:

```sql
UPDATE accounts SET settings = settings - 'volumeMaster' WHERE settings ? 'volumeMaster';
```

(Drops the stray camelCase key repo-wide; `volume_master` — the correct key — is untouched.)

## 2. Settings never load back into the client — screen always shows hardcoded defaults

Root cause: there is no wire path for `AccountSettings` from server to client at all. `LoginResponse` (`common/network/packet/LoginResponse.java`) carries only `success, message, accountId, maxSlots`. `SettingsScreen.setupUI()` has nothing to read from, so it hardcodes UI state on every open: `volumeSlider.setValue(0.8f)` (`SettingsScreen.java:61`), `resolutionSelectBox.setSelected("1280x720")` (`SettingsScreen.java:85`), and `fullscreenCheckBox.setChecked(Gdx.graphics.isFullscreen())` (`SettingsScreen.java:100`, which reflects the live window, not saved state). Only the language dropdown looks correct, and only by coincidence — it reads `LanguageManager.getCurrentLang()`, which persists through a separate mechanism (LibGDX `Preferences`, local to the machine, never touching the DB row at all).

Fix: extend `LoginResponse` to carry the account's settings, following the precedent already set by `maxSlots` (an account-level field embedded directly in the login response, no separate round trip).

- `LoginResponse`: add `AccountSettings settings` as a new record component. Register/verify `AccountSettings` is already Kryo-registered in `KryoConfig` (it must be, since `SaveAccountSettingsRequest` already carries it) — no new registration needed, just confirm ordering isn't disturbed by changing `LoginResponse`'s own shape (Kryo's `RecordSerializer` handles record components by reflection, not manual field registration, so this is safe, but sanity-check against `KryoConfig` before assuming).
- `LoginRequestHandler.handleTestLogin()` (`server/network/handler/LoginRequestHandler.java:41`): change `new LoginResponse(true, "Login successful", account.id(), account.maxSlots())` to also pass `account.settings()`. Update the two failure-path constructions (`LoginResponse.java:29`, `:44`) to pass `AccountSettings.createDefault()` (never null — `SettingsScreen`/`CrimsonSky` shouldn't have to null-check).
- `CrimsonSky.java`: add a field `private AccountSettings accountSettings = AccountSettings.createDefault();` plus `getAccountSettings()`/`setAccountSettings(AccountSettings)`, mirroring the existing `getLanguageManager()` pattern.
- `ConnectionScreen.onLoginResponse()` (`ConnectionScreen.java:268`): on success, before navigating to `MainMenuScreen`, store the settings (`game.setAccountSettings(response.settings())`) and apply the ones with an immediate visible/audible effect — resolution and fullscreen (mirror `SettingsScreen.applyResolution()`'s logic, or extract that method to a shared spot both screens call — see `UiTheme`-style extraction precedent from prompt 25/28). Language is already applied via the existing `LocalizationRequest` flow, so no change needed there, but do reconcile: prefer the DB-loaded `response.settings().language()` over whatever `ConfigurationManager.getLangCode()` set at boot, since the DB value is the actual source of truth for a returning player.
- `SettingsScreen.setupUI()`: replace the three hardcoded literals with reads from `game.getAccountSettings()`:
  - `volumeSlider.setValue((float) game.getAccountSettings().volumeMaster());`
  - `resolutionSelectBox.setSelected(game.getAccountSettings().resolution());`
  - `fullscreenCheckBox.setChecked(game.getAccountSettings().fullscreen());` (replaces the `Gdx.graphics.isFullscreen()` read — the live window state and the saved preference can legitimately disagree right after `applyResolution` runs asynchronously, so prefer the persisted value as the label of record).

## 3. Fullscreen checkbox has no effect on its own

Root cause: only `resolutionSelectBox` has a `ChangeListener` (`SettingsScreen.java:88-93`), which calls `applyResolution(resolutionSelectBox.getSelected(), fullscreenCheckBox.isChecked())`. `fullscreenCheckBox` itself has no listener, so toggling *only* the checkbox (leaving resolution untouched) never calls `applyResolution` at all.

Fix: give `fullscreenCheckBox` its own `ChangeListener` that calls the same `applyResolution(resolutionSelectBox.getSelected(), fullscreenCheckBox.isChecked())`:

```java
fullscreenCheckBox.addListener(new ChangeListener() {
    @Override
    public void changed(ChangeEvent event, Actor actor) {
        applyResolution(resolutionSelectBox.getSelected(), fullscreenCheckBox.isChecked());
    }
});
```

Add this right after `fullscreenCheckBox.setChecked(...)` (`SettingsScreen.java:100`), same shape as the existing resolution listener.

## 4. Label column misalignment

Root cause: label cells in `contentTable` aren't uniformly padded/aligned. `volumeLabel` and `fullscreenLabel` have no `.padRight(...)` or `.left()` at all; `languageLabel` and `resolutionLabel` have `.padRight(20)`, but only `resolutionLabel` additionally has `.left()`. Scene2D `Table` cells default to center alignment, so the four rows don't share a common left edge or gap to the input column — exactly what the screenshot shows.

Fix: apply the same alignment/padding to every label cell:

```java
contentTable.add(volumeLabel).padRight(20).left();
...
contentTable.add(languageLabel).padRight(20).left();
...
contentTable.add(resolutionLabel).padRight(20).left();
...
contentTable.add(fullscreenLabel).padRight(20).left();
```

(Only the four label `.add(...)` calls change — leave the input-column cells as they are.)

## 5. Testing / Definition of Done

No new JUnit coverage required beyond what's reasonable to add for the `AccountService` mapper-sharing change (existing `SaveAccountSettingsRequestHandlerTest` — check it still passes and consider asserting the serialized JSON key is `volume_master`, not `volumeMaster`, so this can't silently regress).

1. Run `V24` migration against the current dev DB, confirm the test account's `settings` JSONB no longer has a `volumeMaster` key and `volume_master` is intact.
2. Change volume, language, resolution, and fullscreen in Settings, hit Save, fully restart the client (not just navigate back), log in again — confirm all four now come back exactly as saved (not hardcoded defaults). Confirm the DB row after save has exactly one volume key (`volume_master`), not two.
3. Toggle *only* the fullscreen checkbox (don't touch resolution) — confirm the window actually goes fullscreen/windowed immediately, same as changing resolution already does.
4. Visually confirm all four label/input rows now share the same left edge and gap, matching the boxed-panel alignment used elsewhere (Main Menu, Connection).

Definition of done: `accounts.settings` never re-acquires a duplicate key on subsequent saves; a returning player's volume/language/resolution/fullscreen all reflect what they last saved, not hardcoded defaults; the fullscreen checkbox works standalone; the settings form's four rows are visually aligned.
