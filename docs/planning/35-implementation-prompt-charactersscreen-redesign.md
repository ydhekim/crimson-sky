# Implementation prompt — CharactersScreen redesign

Sixth screen in the M4 pass. Approved design: centered crimson title + centered "N / M slots" count beneath it (same header shape as Achievements, prompt 33); each row gets a real XP progress bar computed from the actual §15 level curve; Play becomes the crimson accent action, Delete shrinks to a small icon-style button; New Character (footer) also picks up the accent style as the screen's other primary action. `Play` itself stays a no-op for this pass — Character Hub doesn't exist yet, and wiring it to anything else is explicitly out of scope here.

Grounded in the current `CharactersScreen.java`, `CharacterRowBuilder.java`, and `RewardService.java` before writing this — three things below are real defects, not just style choices.

## 1. Almost none of this screen is localized

`"Character Selection"`, `"Level: "`, `"EXP: "`, `"Play"`, `"Delete"`, `"New Character"`/`"Slots Full"`, the empty-state message, and the entire delete-confirmation dialog (title, body, Yes/No) are all hardcoded English literals — only Back (`UI_BTN_BACK`) is localized today. New migration, `server/src/main/resources/db/migration/V27__Add_Characters_Screen_Localization.sql`:

```sql
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_LBL_CHARACTER_SELECTION', 'UI'),
    ('UI_LBL_CHARACTER_SLOTS_COUNT', 'UI'),
    ('UI_LBL_LEVEL_SHORT', 'UI'),
    ('UI_LBL_XP_PROGRESS', 'UI'),
    ('UI_LBL_MAX_LEVEL', 'UI'),
    ('UI_BTN_PLAY', 'UI'),
    ('UI_BTN_NEW_CHARACTER', 'UI'),
    ('UI_LBL_SLOTS_FULL', 'UI'),
    ('UI_MSG_NO_CHARACTERS', 'UI'),
    ('UI_LBL_DELETE_CHARACTER_TITLE', 'UI'),
    ('UI_MSG_DELETE_CHARACTER_CONFIRM', 'UI'),
    ('UI_BTN_YES', 'UI'),
    ('UI_BTN_NO', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_SELECTION'), 'tr_TR', 'Karakter Seçimi'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_SELECTION'), 'en_US', 'Character selection'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_SLOTS_COUNT'), 'tr_TR', '%d / %d slot'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_SLOTS_COUNT'), 'en_US', '%d / %d slots'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_LEVEL_SHORT'), 'tr_TR', 'Sv. %d'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_LEVEL_SHORT'), 'en_US', 'Lv %d'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_XP_PROGRESS'), 'tr_TR', '%d / %d XP'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_XP_PROGRESS'), 'en_US', '%d / %d XP'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_MAX_LEVEL'), 'tr_TR', 'Maks. seviye'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_MAX_LEVEL'), 'en_US', 'Max level'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_PLAY'), 'tr_TR', 'Oyna'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_PLAY'), 'en_US', 'Play'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NEW_CHARACTER'), 'tr_TR', 'Yeni karakter'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NEW_CHARACTER'), 'en_US', 'New character'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_SLOTS_FULL'), 'tr_TR', 'Slotlar dolu'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_SLOTS_FULL'), 'en_US', 'Slots full'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_NO_CHARACTERS'), 'tr_TR', 'Karakter bulunamadı. Maceraya başlamak için bir tane oluştur.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_NO_CHARACTERS'), 'en_US', 'No characters found. Create one to begin your journey.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_DELETE_CHARACTER_TITLE'), 'tr_TR', 'Karakteri sil'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_DELETE_CHARACTER_TITLE'), 'en_US', 'Delete character'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_DELETE_CHARACTER_CONFIRM'), 'tr_TR', '%s karakterini kalıcı olarak silmek istediğine emin misin?'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_DELETE_CHARACTER_CONFIRM'), 'en_US', 'Are you sure you want to permanently delete %s?'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_YES'), 'tr_TR', 'Evet'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_YES'), 'en_US', 'Yes'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NO'), 'tr_TR', 'Hayır'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NO'), 'en_US', 'No');
```

(`%s`/`%d` templates follow the same `String.format` convention established in prompts 32/33 — no new formatting utility.)

## 2. The XP curve only exists server-side, package-private — extract it to `common` before the client can use it

`RewardService.expNeededForLevel(int level)` and `LEVEL_CAP` (`server/service/RewardService.java:275`, `:90`) implement exactly the formula needed for a client-side XP bar (`8×L² − 8`, §15) — but both are package-private in a `server`-only class, unreachable from `core`. Duplicating the formula client-side risks exactly the kind of drift this project has already been burned by once (§15's own doc notes a missed anchor-term bug caught during the L1 pass). Extract instead:

- New file, `common/src/main/java/io/github/ydhekim/crimson_sky/common/model/LevelCurve.java`:

```java
package io.github.ydhekim.crimson_sky.common.model;

/**
 * The shared leveling formula (system design §15): {@code expNeededForLevel(L) = 8×L² − 8}, anchored so
 * level 1 needs 0 cumulative exp. Extracted from {@code RewardService} (server-only, package-private)
 * so the client can compute XP-progress display without duplicating — and risking drift from — the
 * same formula the server uses to decide actual level-ups.
 */
public final class LevelCurve {
    private LevelCurve() {}

    public static final int LEVEL_CAP = 50;

    public static long expNeededForLevel(int level) {
        return 8L * level * level - 8L;
    }
}
```

- `RewardService`: delete its own `expNeededForLevel`/`LEVEL_CAP`, delegate every call site to `LevelCurve.expNeededForLevel(...)`/`LevelCurve.LEVEL_CAP` instead. Behavior must stay byte-for-byte identical — this is a pure extraction, not a formula change.
- Update `RewardServiceProgressionTest` (and any other test referencing `RewardService.expNeededForLevel`/`LEVEL_CAP` directly) to reference `LevelCurve` instead.

## 3. Header: centered title + slot count

```java
VisLabel titleLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_CHARACTER_SELECTION"));
titleLabel.setFontScale(2f);
titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
titleLabel.setAlignment(Align.center);
mainPanel.add(titleLabel).padBottom(4).center().row();

slotsCountLabel = new VisLabel("");
slotsCountLabel.setColor(UiPalette.TEXT_MUTED);
slotsCountLabel.setAlignment(Align.center);
mainPanel.add(slotsCountLabel).padBottom(20).center().row();
```

(`slotsCountLabel` becomes a field, set inside `updateList()`:)

```java
slotsCountLabel.setText(String.format(
    game.getLanguageManager().get("UI_LBL_CHARACTER_SLOTS_COUNT"), characters.size, maxCharacterSlots));
```

## 4. Row: real XP progress bar

`CharacterRowBuilder.build()` currently renders `"Level: " + character.level()` and `"EXP: " + character.experience()` as two plain label rows. Replace with a level caption + progress bar, using the newly-shared `LevelCurve`:

```java
int level = character.level();
long exp = character.experience();

VisTable levelRow = new VisTable();
String levelText = String.format(game.getLanguageManager().get("UI_LBL_LEVEL_SHORT"), level);
levelRow.add(new VisLabel(levelText)).padRight(8);

if (level >= LevelCurve.LEVEL_CAP) {
    levelRow.add(new VisLabel(game.getLanguageManager().get("UI_LBL_MAX_LEVEL"))).color(UiPalette.TEXT_MUTED);
} else {
    long currentThreshold = LevelCurve.expNeededForLevel(level);
    long nextThreshold = LevelCurve.expNeededForLevel(level + 1);
    float progress = (float) (exp - currentThreshold) / (nextThreshold - currentThreshold);

    VisProgressBar xpBar = new VisProgressBar(0f, 1f, 0.01f, false);
    xpBar.setValue(progress);
    xpBar.setAnimateDuration(0f);
    levelRow.add(xpBar).width(120).padRight(8);

    String xpText = String.format(game.getLanguageManager().get("UI_LBL_XP_PROGRESS"),
        exp - currentThreshold, nextThreshold - currentThreshold);
    levelRow.add(new VisLabel(xpText)).color(UiPalette.TEXT_MUTED);
}
infoTable.add(levelRow).left();
```

(`CharacterRowBuilder` needs a `CrimsonSky game` reference for `getLanguageManager()` — pass it through the constructor alongside `character`, same shape as every other builder in this codebase that needs localization. `VisProgressBar` is VisUI's stock progress bar widget — no custom drawable needed, its default style is fine as a first pass; a gold-tinted fill can come later if it reads too flat.)

## 5. Button hierarchy: Play accented, Delete shrunk to icon-style, New Character accented

`CharacterRowBuilder.build()`:

```java
new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_PLAY"))
    .withStyle(accentButtonStyle)   // was buttonStyle (customButtonStyle) — Play is this row's primary action
    .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
    .withAction(onPlayAction)
    .buildAndAddTo(actionsTable, 5);
actionsTable.row();
new UIButtonBuilder("X")   // no icon font/atlas is shipped (M4 foundation cleanup) — a real trash-can
                           // glyph is Epic E content-art work; "X" is the practical stand-in for now.
    .withStyle(iconButtonStyle)
    .withSize(UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_BUTTON_SIZE)
    .withAction(onDeleteAction)
    .buildAndAddTo(actionsTable);
```

`CharacterRowBuilder` needs both `accentButtonStyle` and `iconButtonStyle` passed in (currently only takes one shared `buttonStyle` via `withButtonStyle(...)`) — change its builder API to `withButtonStyles(TextButton.TextButtonStyle accent, TextButton.TextButtonStyle icon)` (or two separate `with...` methods), and have `CharactersScreen.createCharacterRow()` pass `this.accentButtonStyle` and `this.iconButtonStyle` (both already built on every `BaseScreen`, per prompt 28/33's `UiTheme` wiring — `iconButtonStyle` in particular has been sitting unused since prompt 25 explicitly built it for "icon-square buttons" like this one).

Footer's New Character button:

```java
createCharacterButton = new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_NEW_CHARACTER"))
    .withStyle(accentButtonStyle)   // was customButtonStyle
    .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
    .withAction(this::navigateToCharacterCreation)
    .build();
```

And in `updateList()`, swap the disabled-state text to the new key:

```java
createCharacterButton.setText(canCreate
    ? game.getLanguageManager().get("UI_BTN_NEW_CHARACTER")
    : game.getLanguageManager().get("UI_LBL_SLOTS_FULL"));
```

Empty-state message (`updateList()`):

```java
charactersListContainer.add(new VisLabel(game.getLanguageManager().get("UI_MSG_NO_CHARACTERS"))).expand().center();
```

## 6. Fix the localization-refresh anti-pattern (texture leak + stale ScreenRouter cache)

`CharactersScreen.onLocalizationResponse()` (`CharactersScreen.java:210-222`) doesn't follow the established `refreshUI()` pattern every other screen uses — it manually re-applies `setTranslations()` (redundant: `PacketHandlerRegistry.java:91` already does this for every screen, centrally, before dispatching to any listener) and then calls `game.setScreen(new CharactersScreen(game))`, constructing a whole new screen instance and swapping to it directly.

This is a real bug, not just an inconsistency: the *old* `CharactersScreen` instance is never disposed (`placeholderAvatarTexture`/`rowBackgroundTexture` leak — `game.setScreen()` doesn't call `dispose()` on the screen it replaces) and, worse, `ScreenRouter`'s cache still points at that stale old instance. The next time anything calls `screenRouter.navigateTo(ScreenType.CHARACTERS)`, it gets the *stale, pre-language-switch* instance back from the cache — not the fresh one currently on screen — undoing the switch on the next visit.

Fix: delete the `onLocalizationResponse` override entirely and add the standard override instead:

```java
@Override
public void refreshUI() {
    setupUI();
}
```

(No re-fetch needed — `setupUI()`'s final `updateList(characters)` call already re-renders every row from the existing, already-fetched `characters` array; only the *localized strings* need refreshing, not the character data itself. This mirrors the fact that `setupUI()` builds no textures itself — `placeholderAvatarTexture`/`rowBackgroundTexture` are already constructor-only — so, unlike Achievements' `refreshUI()` fix (prompt 34), no texture-lifecycle split is needed here; it's already correctly separated.)

## 7. Two small cleanups while this file is open

- `confirmDeleteCharacter()`'s anonymous `VisDialog` subclass overrides `result(Object object)` (`CharactersScreen.java:141-146`), but every button is added via `dialog.getButtonsTable()` rather than `Dialog.button(...)`, so `result()` is never actually invoked — dead code that reads like it's the real delete-trigger but isn't. Remove the override; the explicit "Yes" button's own `withAction()` callback is the only path that ever fires the delete.
- `import io.github.ydhekim.crimson_sky.network.NetworkListener;` is imported twice (`CharactersScreen.java:16` and `:22`) — drop the duplicate.
- While rewriting the dialog, localize its title/body/buttons using the new keys from §1:

```java
VisDialog dialog = new VisDialog(game.getLanguageManager().get("UI_LBL_DELETE_CHARACTER_TITLE"));
dialog.text(String.format(game.getLanguageManager().get("UI_MSG_DELETE_CHARACTER_CONFIRM"), character.name()));

new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_YES"))
    .withStyle(customButtonStyle)
    .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
    .withAction(() -> {
        dialog.hide();
        game.getNetworkClient().sendTCP(new DeleteCharacterRequest(character.name()));
    })
    .buildAndAddTo(dialog.getButtonsTable());
dialog.getButtonsTable().add().expandX();

new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_NO"))
    .withStyle(customButtonStyle)
    .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
    .withAction(dialog::hide)
    .buildAndAddTo(dialog.getButtonsTable());
```

## 8. Testing / Definition of Done

No new JUnit coverage for the client changes. Do add a quick assertion that `RewardService`'s delegation to `LevelCurve` didn't change behavior — `RewardServiceProgressionTest` already covers the level-up math; just confirm it still passes unmodified in intent (only its `RewardService.expNeededForLevel`/`LEVEL_CAP` references should need updating to `LevelCurve`).

1. `gradlew.bat lwjgl3:run`, reach Characters — confirm the title is centered/crimson, the slot count reads correctly (e.g. "1 / 3 slots"), each row shows a level caption + XP progress bar (or "Max level" if somehow at cap) instead of raw "Level:"/"EXP:" text, Play is crimson-accented, Delete is a small icon-style "X" button, and New Character is also crimson-accented (or reads "Slots full" once capped).
2. Switch language, confirm every piece of text on this screen — title, slot count, level/XP captions, Play, New Character/Slots full, empty-state message, and the delete-confirmation dialog — all update immediately without needing an app restart, and confirm navigating away and back to Characters afterward still shows the updated language (regression check for the ScreenRouter-cache bug in §6).
3. Delete a character, confirm the dialog still works exactly as before (only its copy changed).
4. Create characters up to the slot cap, confirm the count label and the New Character/Slots full swap both track correctly.

Definition of done: screen matches the approved mockup; every visible string is localized; the XP bar reflects the real §15 curve via the new shared `LevelCurve` (no duplicated formula); the language-switch bug (leaked textures + stale `ScreenRouter` cache) is fixed; dead code and the duplicate import are gone.
