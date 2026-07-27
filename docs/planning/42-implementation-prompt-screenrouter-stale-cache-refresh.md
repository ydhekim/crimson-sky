# Implementation prompt — refresh cached screens on re-entry

Follow-up to K7/prompt 40. After that merged, a language change made mid-session (via Settings) still didn't reach **Achievements** or **Characters**, while Character Creation and other screens correctly showed the new language — a narrower, different bug from the one prompt 40 fixed (that one was specifically about the moment right after login; this one is about revisiting an already-cached screen later in the same session).

## Root cause

`ScreenRouter.navigateTo(type)` (`core/screen/factory/ScreenRouter.java:30`) reuses a cached `Screen` instance per `ScreenType` and just calls `game.setScreen(screen)` — no refresh of any kind:

```java
public void navigateTo(ScreenType type) {
    Screen screen = screenCache.get(type);
    if (screen == null) {
        screen = screenFactory.createScreen(type);
        screenCache.put(type, screen);
    }
    game.setScreen(screen);
}
```

Every screen only rebuilds its localized text in response to `onLocalizationResponse` while it is the **active** `NetworkListener` (`BaseScreen.show()`/`hide()` wire the listener on and off; `BaseScreen`'s default `onLocalizationResponse` calls `refreshUI()`). A screen that isn't currently showing has its listener cleared, so a `LocalizationResponse` arriving while it's inactive never reaches it — its cached actor tree simply stays exactly as it was the last time it was built.

This explains the exact split reported: Achievements and Characters had been visited (and cached) *before* the in-session language change; revisiting them afterward through `navigateTo` reused that stale cache with zero rebuild trigger. Character Creation and the other screens happened to be freshly constructed *after* the change (or were the active screen *during* the change, e.g. Settings itself), so they read the already-current translations map at construction time — not because they're architecturally different, just visit-order luck. The same staleness would eventually surface on any screen, given the right visit order; K7 only fixed the specific pre-login race, not this general cached-reuse gap.

## Fix

Make every screen a `BaseScreen` in the router's own types (all five current screen types already are, per `ScreenFactory.createScreen`'s switch), and call `refreshUI()` on a *reused* cached instance right after showing it — freshly-created instances don't need it, since their constructor already builds against the current state:

`ScreenFactory.java` — tighten the return type from `Screen` to `BaseScreen` (no behavior change, every branch already returns a `BaseScreen` subclass; drop the now-unused `com.badlogic.gdx.Screen` import):

```java
public BaseScreen createScreen(ScreenType type) {
    return switch (type) {
        case MAIN_MENU -> new MainMenuScreen(game);
        case CHARACTERS -> new CharactersScreen(game);
        case CHARACTER_CREATION -> new CharacterCreationScreen(game);
        case ACHIEVEMENTS -> new AchievementsScreen(game);
        case SETTINGS -> new SettingsScreen(game);
        case GAME -> throw new UnsupportedOperationException("GameScreen not yet implemented.");
        default -> throw new IllegalArgumentException("Unknown screen type: " + type);
    };
}
```

`ScreenRouter.java` — cache type follows suit (`ObjectMap<ScreenType, BaseScreen>`), and `navigateTo` calls `refreshUI()` only on the reused-instance path:

```java
public void navigateTo(ScreenType type) {
    BaseScreen screen = screenCache.get(type);
    if (screen == null) {
        screen = screenFactory.createScreen(type);
        screenCache.put(type, screen);
        game.setScreen(screen);
    } else {
        // Reused cached instance — session-global state (translations, most concretely) may have
        // changed while this screen wasn't active and therefore wasn't listening for the change.
        // Re-running its UI build here is what K7/prompt 40 didn't cover: that fix handled the one race
        // right after login; this covers every later in-session change.
        game.setScreen(screen);
        screen.refreshUI();
    }
}
```

`getScreen(type)`'s return type changes the same way, for consistency (it currently has no callers, so purely cosmetic).

`BaseScreen.refreshUI()`'s default is a no-op override point already implemented by every current screen (`ConnectionScreen`, `MainMenuScreen`, `CharactersScreen`, `AchievementsScreen`, `SettingsScreen`, `CharacterCreationScreen`) as `{ setupUI(); }` or an equivalent rebuild — each of those is already safe to call repeatedly (established discipline from the whole M4 pass: `setupUI()` clears the stage first, textures are constructor-only). No screen-level changes needed; this is entirely a `ScreenRouter`/`ScreenFactory` fix.

## Testing / Definition of Done

1. `gradlew.bat build` — confirm it compiles with the tightened `BaseScreen` return/cache types.
2. `gradlew.bat test` — confirm nothing existing broke (no test currently covers `ScreenRouter`; not asking for new ones here, the fix is small enough to verify by direct reading + the manual check below).

Manual verification — I'll test this myself: visit Characters and Achievements once (caching both), change language in Settings, then navigate back to each via the normal nav buttons — confirm both now show the new language immediately, without needing an app restart.

Definition of done: any cached screen reflects the current global translations the moment it's re-shown, regardless of when the language change happened relative to that screen's last visit.
