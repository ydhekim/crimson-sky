# Implementation prompt — fix stale translations after login

Root cause of a bug that's been surfacing all through the M4 screen pass as seemingly random, screen-specific "still shows English" reports (most recently CharactersScreen, via a live screenshot) — it was deliberately left undiagnosed earlier rather than guessed at, and grounding it now in `ConnectionScreen`/`LanguageManager` found the actual single cause.

## Root cause

`LanguageManager`'s `currentLang` field is seeded in its constructor from a **local** GDX `Preferences` value (`core/util/LanguageManager.java:19`, default `"en_US"`) — this is a different, independent source of truth from the account's **server-persisted** `AccountSettings.language()`.

`ConnectionScreen.onConnected()` (`core/screen/ConnectionScreen.java:245`) fires the very first `LocalizationRequest` using that local preference, before login has even happened — so the `translations` map gets populated with whatever language the local preference says, which may not match the account's actual saved language (a fresh install defaults to `en_US`; any prior divergence between local prefs and the account's DB-saved language reproduces this too).

`onLoginResponse` (`ConnectionScreen.java:268`) then correctly calls `game.getLanguageManager().setCurrentLang(settings.language())` once the account's real saved language is known — but `setCurrentLang` (`LanguageManager.java:44`) only updates the field and re-persists it to the local preference for *next* launch. It never re-requests translations. So the `translations` map loaded pre-login stays in effect for the rest of the session — every screen built after login (MainMenu, Characters, Achievements, Settings) reads that same, potentially-wrong map.

This is why it looked flaky: the bug self-heals on the player's *next* launch (since `setCurrentLang` did persist the correct value locally), so it only reproduces on the first launch after install, or after playing on a different device/profile — not consistently, which is exactly why it read as a per-screen mystery rather than one root cause.

## Fix

`ConnectionScreen.onLoginResponse` — send a fresh `LocalizationRequest` for the account's real language immediately after `setCurrentLang`, so the translations map is corrected before any post-login screen is built:

```java
AccountSettings settings = response.settings() != null ? response.settings() : AccountSettings.createDefault();
game.setAccountSettings(settings);
DisplaySettings.apply(settings.resolution(), settings.fullscreen());
game.getLanguageManager().setCurrentLang(settings.language());
game.getNetworkClient().sendTCP(new LocalizationRequest(settings.language())); // NEW — see prompt 40

setState(ConnectionState.SUCCESS);
```

`ConnectionScreen.onLocalizationResponse`'s existing guard would misfire against this new second response — as written (`currentState != ConnectionState.AUTHENTICATING`), a `LocalizationResponse` arriving after login (state is `SUCCESS` by then) would satisfy `SUCCESS != AUTHENTICATING` and incorrectly call `authenticateWithPlatform()` again, firing a duplicate `LoginRequest`. Tighten the guard to only match the *original* pre-auth moment:

```java
@Override
public void onLocalizationResponse(io.github.ydhekim.crimson_sky.common.network.packet.LocalizationResponse response) {
    super.onLocalizationResponse(response);

    // Only the very first LocalizationResponse (received while still CONNECTING, before any login
    // attempt) should kick off authentication. The second one this screen may now receive — the
    // corrective re-fetch added above, once the account's real language is known post-login — must not
    // re-trigger auth; `currentState` is SUCCESS by then, not CONNECTING, so this guard now excludes it
    // where the old `!= AUTHENTICATING` check incorrectly would have matched.
    if (response.success() && testIdentityToken != null && currentState == ConnectionState.CONNECTING) {
        Gdx.app.postRunnable(this::authenticateWithPlatform);
    }
}
```

No change needed anywhere else: `PacketHandlerRegistry` already applies `languageManager.setTranslations(...)` globally for every `LocalizationResponse` regardless of which screen's listener is currently registered (`network/PacketHandlerRegistry.java:91`), so the corrected map takes effect immediately no matter whether this second response arrives while `ConnectionScreen` is still showing or after `MainMenuScreen` has already taken over — either screen's `onLocalizationResponse` (default `BaseScreen` behavior, or `ConnectionScreen`'s override above) just calls `refreshUI()` on success, which is safe either way.

## Testing / Definition of Done

1. `gradlew.bat build` — confirm it compiles.
2. `gradlew.bat test` — confirm nothing existing broke (no test currently covers `ConnectionScreen`'s network callbacks; not asking for new ones here, this is a small enough change to verify by direct reading + the manual check below).

Manual verification — I'll test this myself:

- With the account's saved language set to Turkish, launch the app fresh and confirm every screen (MainMenu, Characters, Achievements, Settings) shows Turkish immediately after login, without needing to touch Settings first.
- Confirm login still completes normally and only once — watch for any duplicate-login symptom (e.g. a second `CharacterListRequest`/duplicate rows) that would indicate the guard fix didn't fully prevent the double-`authenticateWithPlatform()` case.

Definition of done: translations are correct immediately after login on the very first launch after any local/account language divergence, not just after a restart; login fires exactly once per connection.
