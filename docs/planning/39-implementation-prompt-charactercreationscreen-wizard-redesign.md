# Implementation prompt — CharacterCreationScreen wizard redesign

Seventh screen in the M4 pass, and the largest single rewrite so far. Depends on prompt 38 (the `Faction.CRIMSON`/`Faction.SKYBORN` rename) landing first — this prompt consumes those enum names directly.

Approved design, decided across a design discussion (not just style): split into a 3-step wizard (Identity → Appearance → Stats) instead of today's single dense panel; real faction names ("The Wardens" / "The Skyborn," see `03-lore-and-worldbuilding.md`'s 2026-07-27 naming note — **not** "Crimson Accord," which reads as borrowing the game's own title) with short lore-accurate taglines and placeholder crests; a reserved character-preview slot on the Appearance step; solid-color swatches for hair/skin color instead of plain text buttons; and a real Skyborn accent palette (blue/silver) alongside the existing crimson/gold. Also fixing three real bugs found while grounding this screen, independent of the redesign: no option button (faction, gender, hair, skin) ever shows a selected state at all; the name field's "Enter Name" is fake placeholder text that's actually real content; and this is the least-localized screen in the entire app.

## 1. New palette: Skyborn's blue/silver

`UiPalette.java` currently only has crimson/gold. Add the Skyborn pair, following the exact same shape as the crimson constants:

```java
public static final Color ACCENT_BLUE = new Color(0.227f, 0.431f, 0.647f, 1f);        // #3A6EA5
public static final Color ACCENT_BLUE_HOVER = new Color(0.30f, 0.51f, 0.73f, 1f);
public static final Color ACCENT_BLUE_PRESSED = new Color(0.165f, 0.33f, 0.50f, 1f);
public static final Color ACCENT_SILVER = new Color(0.722f, 0.769f, 0.816f, 1f);       // #B8C4D0
```

Update the class javadoc's "Blue/silver... intentionally not here yet" note — this is that screen now.

## 2. Extract the placeholder-crest builder (DRY, not new)

`ConnectionScreen.createCrest()` already builds exactly the "two rotated squares, outer border color + inner fill color" placeholder crest this screen needs, just hardcoded to gold/crimson. Extract it into a small shared static helper so both screens use one implementation:

New file, `core/src/main/java/io/github/ydhekim/crimson_sky/ui/CrestFactory.java`:

```java
package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * Generic placeholder crest (two rotated 45-degree squares, an outer "border" color behind a smaller
 * inner "fill" color) — extracted from {@code ConnectionScreen.createCrest()} so the same generic-crest
 * treatment (system design §24/the M4 screen pass) can be reused per-faction with different colors,
 * rather than duplicated. Callers own and dispose the two textures this creates.
 */
public final class CrestFactory {
    private CrestFactory() {}

    public static Stack build(Texture outerTexture, Texture innerTexture, float outerSize, float innerSize) {
        Image outer = new Image(new TextureRegionDrawable(new TextureRegion(outerTexture)));
        outer.setOrigin(outerSize / 2f, outerSize / 2f);
        outer.setRotation(45);

        Image inner = new Image(new TextureRegionDrawable(new TextureRegion(innerTexture)));
        inner.setOrigin(innerSize / 2f, innerSize / 2f);
        inner.setRotation(45);

        Stack crest = new Stack();
        crest.add(outer);
        crest.add(new Container<>(inner).size(innerSize));
        return crest;
    }
}
```

Update `ConnectionScreen.createCrest()` to call `CrestFactory.build(crestGoldTexture, crestCrimsonTexture, CREST_OUTER_SIZE, CREST_INNER_SIZE)` instead of building the `Stack` inline — behavior identical, just de-duplicated.

## 3. Selected-state feedback — the actual bug fix

None of today's option buttons (faction, gender, hair type, hair color, skin color) ever change appearance when selected; `selectedFaction`/`selectedGender`/etc. are tracked purely in Java fields with no visual binding back to the buttons. Fix using LibGDX's real checked-state mechanism instead of ad-hoc manual restyling:

New `UiTheme` method — a style with a distinct `checked` drawable, so `TextButton.setChecked(true)` actually looks different (today's `standardButtonStyle`/`accentButtonStyle` only set `up`/`over`/`down`, never `checked`, which is why toggling checked state currently does nothing visually even where it might already be called):

```java
/**
 * A toggle style whose `checked` state is visually distinct — for option rows (gender, hair type,
 * hair/skin color) where exactly one choice is selected at a time via a {@link com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup}.
 * Unlike standardButtonStyle/accentButtonStyle, this one defines `checked`/`checkedOver`, since neither
 * of those is ever set otherwise (Button.ButtonStyle silently falls back to `up` when unset, which is
 * why toggling `.setChecked()` on any existing style produces no visible change).
 */
public TextButton.TextButtonStyle toggleButtonStyle(BitmapFont font) {
    TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
    style.font = font;
    style.up = drawable(new Color(0.2f, 0.2f, 0.22f, 1f));
    style.over = drawable(new Color(0.3f, 0.3f, 0.33f, 1f));
    style.checked = drawable(new Color(UiPalette.ACCENT_GOLD.r, UiPalette.ACCENT_GOLD.g, UiPalette.ACCENT_GOLD.b, 0.22f));
    style.checkedOver = drawable(new Color(UiPalette.ACCENT_GOLD.r, UiPalette.ACCENT_GOLD.g, UiPalette.ACCENT_GOLD.b, 0.32f));
    style.fontColor = Color.WHITE;
    style.checkedFontColor = UiPalette.ACCENT_GOLD;
    return style;
}
```

(Gold, not crimson — this is the generic "this is the current choice" indicator for gender/hair/skin, independent of faction; crimson stays reserved for the Wardens specifically, per the faction-card treatment below.)

`createOptionRow(...)` rewrite — one `ButtonGroup` per row so selecting an option automatically unchecks the others, and the initially-selected option starts checked (today, nothing marks the default selection either):

```java
private VisTable createOptionRow(String label, List<String> options, String initiallySelected, java.util.function.Consumer<String> onSelect) {
    VisTable rowTable = new VisTable();
    rowTable.add(new VisLabel(label)).width(100);

    ButtonGroup<TextButton> group = new ButtonGroup<>();
    group.setMinCheckCount(1);
    group.setMaxCheckCount(1);

    for (String option : options) {
        TextButton button = new UIButtonBuilder(game.getLanguageManager().get(optionLocKey(option)))
            .withStyle(toggleButtonStyle)
            .withSize(UiMetrics.SMALL_BUTTON_WIDTH, UiMetrics.SMALL_BUTTON_HEIGHT)
            .withAction(() -> onSelect.accept(option))
            .build();
        button.setChecked(option.equals(initiallySelected));
        group.add(button);
        rowTable.add(button).padRight(4);
    }
    return rowTable;
}
```

(`optionLocKey(String)` maps a raw `Appearance` constant like `"MALE"`/`"BLACK"`/`"SHORT"` to its `UI_OPT_*` localization key — see §7's key list. `UIButtonBuilder.build()` returning a bare `TextButton` rather than using `buildAndAddTo` is necessary here since the button needs `.setChecked(...)` called on it before it's usable in the group.)

Hair color and skin color rows get swatches instead of text (§4), which need their own selected-state treatment (a gold border ring, matching the approved mockup) rather than `toggleButtonStyle`'s background — see §4.

Faction cards are larger composite widgets (icon + name + tagline), not simple `TextButton`s, so they don't go through `ButtonGroup`/`checked` at all — see §5's manual two-state restyle.

## 4. Hair/skin color swatches

Replace `createOptionRow`'s plain-text buttons for `HAIR_COLORS`/`SKIN_COLORS` with actual colored swatches. New method, used only for these two rows:

```java
private static final ObjectMap<String, Color> HAIR_COLOR_SWATCHES = new ObjectMap<>();
private static final ObjectMap<String, Color> SKIN_COLOR_SWATCHES = new ObjectMap<>();
static {
    HAIR_COLOR_SWATCHES.put("BLACK", new Color(0.11f, 0.10f, 0.09f, 1f));
    HAIR_COLOR_SWATCHES.put("BROWN", new Color(0.353f, 0.227f, 0.141f, 1f));
    HAIR_COLOR_SWATCHES.put("BLONDE", new Color(0.851f, 0.706f, 0.353f, 1f));
    HAIR_COLOR_SWATCHES.put("RED", UiPalette.ACCENT_CRIMSON);
    SKIN_COLOR_SWATCHES.put("PALE", new Color(0.910f, 0.788f, 0.659f, 1f));
    SKIN_COLOR_SWATCHES.put("LIGHT", new Color(0.851f, 0.659f, 0.471f, 1f));
    SKIN_COLOR_SWATCHES.put("TAN", new Color(0.659f, 0.475f, 0.310f, 1f));
    SKIN_COLOR_SWATCHES.put("DARK", new Color(0.420f, 0.271f, 0.188f, 1f));
}

private VisTable createSwatchRow(String label, List<String> options, ObjectMap<String, Color> swatches,
                                   String initiallySelected, java.util.function.Consumer<String> onSelect) {
    VisTable rowTable = new VisTable();
    rowTable.add(new VisLabel(label)).width(100);
    for (String option : options) {
        Texture swatchTexture = TextureFactory.createSolidTexture(28, 28, swatches.get(option));
        swatchTextures.add(swatchTexture); // disposables — see §9
        Image swatch = new Image(new TextureRegionDrawable(new TextureRegion(swatchTexture)));

        boolean selected = option.equals(initiallySelected);
        VisTable swatchCell = new VisTable();
        swatchCell.setBackground(selected ? goldRingDrawable : null);
        swatchCell.pad(selected ? 0 : 2);
        swatchCell.add(swatch).size(28, 28);
        swatchCell.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onSelect.accept(option);
                setupUI(); // simplest correct refresh — see §6, same full-rebuild pattern every step transition already uses
            }
        });
        rowTable.add(swatchCell).padRight(8);
    }
    return rowTable;
}
```

(`goldRingDrawable` — a small solid-gold `TextureRegionDrawable` reused as a 2px-equivalent border by padding the swatch inward when selected, cheapest way to fake a ring without a nine-patch. Build it once alongside the other constructor-time textures, same lifecycle discipline as everywhere else. `swatchTextures` is a new `List<Texture>` disposed in `dispose()`, since these are built fresh — 8 total, small — each time `setupUI()` runs; simpler than trying to cache-and-reuse across rebuilds given how few there are.)

## 5. Faction cards

Two side-by-side clickable cards (not `TextButton`s — the icon+name+tagline layout needs a composite widget), each with two visual states: selected uses that faction's own accent (crimson+gold border for the Wardens, blue+silver border for the Skyborn), unselected uses a neutral outline. Manual restyle on click, since this is exactly two mutually-exclusive cards (a `ButtonGroup` would be overkill for a binary choice built from custom `Table`s rather than `Button`s):

```java
private VisTable createFactionCard(Faction faction, String nameKey, String taglineKey,
                                     Texture crestOuterTexture, Texture crestInnerTexture,
                                     Color accentColor, Color accentBorderColor) {
    VisTable card = new VisTable();
    card.pad(16);
    card.left().top();

    Stack crest = CrestFactory.build(crestOuterTexture, crestInnerTexture, 36, 26);
    card.add(crest).size(36, 36).padRight(12);

    VisTable textColumn = new VisTable();
    VisLabel nameLabel = new VisLabel(game.getLanguageManager().get(nameKey));
    nameLabel.setFontScale(1.1f);
    textColumn.add(nameLabel).left().padBottom(4).row();

    VisLabel taglineLabel = new VisLabel(game.getLanguageManager().get(taglineKey));
    taglineLabel.setWrap(true);
    taglineLabel.setColor(UiPalette.TEXT_MUTED);
    textColumn.add(taglineLabel).width(200).left();
    card.add(textColumn);

    updateFactionCardStyle(card, faction == selectedFaction, accentColor, accentBorderColor);

    card.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
            selectedFaction = faction;
            setupUI(); // full rebuild picks up the new selection on both cards
        }
    });
    return card;
}

private void updateFactionCardStyle(VisTable card, boolean selected, Color accentColor, Color accentBorderColor) {
    Texture bgTexture = TextureFactory.createSolidTexture(1, 1, selected
        ? new Color(accentColor.r, accentColor.g, accentColor.b, 0.16f)
        : new Color(1f, 1f, 1f, 0.03f));
    disposables.add(bgTexture);
    card.setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));
    // Border color is cosmetic-only here (no real bordered-drawable support, same limitation
    // ConnectionScreen's crest Container workaround already documents) — the background tint above is
    // what actually reads as "selected" at a glance; a true border is a later art-pipeline nicety.
}
```

Call site (inside the Identity step builder):

```java
VisTable factionRow = new VisTable();
factionRow.add(createFactionCard(Faction.CRIMSON, "UI_FACTION_WARDENS_NAME", "UI_FACTION_WARDENS_TAGLINE",
    crestGoldTexture, crestCrimsonTexture, UiPalette.ACCENT_CRIMSON, UiPalette.ACCENT_GOLD))
    .expandX().fillX().padRight(12);
factionRow.add(createFactionCard(Faction.SKYBORN, "UI_FACTION_SKYBORN_NAME", "UI_FACTION_SKYBORN_TAGLINE",
    silverTexture, blueTexture, UiPalette.ACCENT_BLUE, UiPalette.ACCENT_SILVER))
    .expandX().fillX();
```

(`crestGoldTexture`/`crestCrimsonTexture`/`silverTexture`/`blueTexture` — four small constructor-time textures, `TextureFactory.createSolidTexture(color)` each, added to `disposables`, same lifecycle pattern as every other screen's constructor-only textures.)

## 6. Wizard structure

Add a `currentStep` field (`1`, `2`, or `3`) and rebuild the whole panel on every step change — the same full-rebuild convention `setupUI()` already follows everywhere else in this codebase, just now parameterized by step:

```java
private int currentStep = 1;
private static final int STEP_COUNT = 3;

private void setupUI() {
    stage.clear();
    VisTable mainPanel = createMainContentPanel();

    VisLabel titleLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_CREATE_CHARACTER"));
    titleLabel.setFontScale(2f);
    titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
    titleLabel.setAlignment(Align.center);
    mainPanel.add(titleLabel).padBottom(8).center().row();

    mainPanel.add(buildStepIndicator()).padBottom(20).center().row();

    VisTable stepContent = switch (currentStep) {
        case 1 -> buildIdentityStep();
        case 2 -> buildAppearanceStep();
        default -> buildStatsStep();
    };
    mainPanel.add(stepContent).expand().fill().padBottom(20).row();

    mainPanel.add(buildFooter()).expandX().fillX();
}

private VisTable buildStepIndicator() {
    VisTable dots = new VisTable();
    for (int i = 1; i <= STEP_COUNT; i++) {
        Texture dotTexture = TextureFactory.createSolidTexture(22, 3,
            i <= currentStep ? UiPalette.ACCENT_CRIMSON : new Color(0.23f, 0.22f, 0.2f, 1f));
        disposables.add(dotTexture); // rebuilt every setupUI() call, same as swatch textures — see §9
        dots.add(new Image(new TextureRegionDrawable(new TextureRegion(dotTexture)))).size(22, 3).padRight(8);
    }
    return dots;
}

private VisTable buildFooter() {
    VisTable footer = new VisTable();
    new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_BACK"))
        .withStyle(customButtonStyle)
        .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
        .withAction(this::handleBack)
        .buildAndAddTo(footer);
    footer.add().expandX();
    new UIButtonBuilder(game.getLanguageManager().get(currentStep < STEP_COUNT ? "UI_BTN_NEXT" : "UI_BTN_CREATE"))
        .withStyle(accentButtonStyle)
        .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
        .withAction(currentStep < STEP_COUNT ? this::handleNext : this::submitCharacterCreation)
        .buildAndAddTo(footer);
    return footer;
}

private void handleBack() {
    if (currentStep > 1) {
        currentStep--;
        setupUI();
    } else {
        game.getScreenRouter().navigateTo(ScreenType.CHARACTERS);
    }
}

private void handleNext() {
    if (currentStep == 1 && nameField.getText().trim().isEmpty()) {
        new VisDialog(game.getLanguageManager().get("UI_LBL_INVALID_NAME_TITLE"))
            .text(game.getLanguageManager().get("UI_MSG_INVALID_NAME"))
            .button(game.getLanguageManager().get("UI_BTN_OK"))
            .show(stage);
        return;
    }
    currentStep++;
    setupUI();
}
```

(Name validation moves from `submitCharacterCreation()` to the step-1→2 `handleNext()` transition, since name entry now lives entirely on step 1 — catching it there is more natural than letting an invalid name silently ride through two more steps before rejecting it at the very end. `submitCharacterCreation()` keeps its existing network-request-building logic unchanged, just drops the name check it no longer needs since `handleNext()` already guards it.)

## 7. Real placeholder text (bug fix, independent of the wizard)

```java
nameField = new VisTextField();
nameField.setMessageText(game.getLanguageManager().get("UI_HINT_ENTER_NAME"));
```

Drop `submitCharacterCreation()`'s `characterName.equals("Enter Name")` special case entirely — `setMessageText` never counts as real content, so `getText()` correctly returns `""` when nothing's been typed, same emptiness check as any other field in this codebase.

## 8. Character preview placeholder (Appearance step only)

A reserved, clearly-inert slot — nothing renders appearance data yet (system design §23: "M4 is still placeholder-rendering... nothing consumes this data yet"), this just keeps a visual spot for M5's real art pipeline so this step's layout doesn't need revisiting later:

```java
VisTable previewBox = new VisTable();
Texture previewBgTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.04f));
disposables.add(previewBgTexture);
previewBox.setBackground(new TextureRegionDrawable(new TextureRegion(previewBgTexture)));
VisLabel previewLabel = new VisLabel(game.getLanguageManager().get("UI_MSG_CHARACTER_PREVIEW_PLACEHOLDER"));
previewLabel.setWrap(true);
previewLabel.setColor(new Color(0.35f, 0.34f, 0.32f, 1f));
previewLabel.setAlignment(Align.center);
previewBox.add(previewLabel).width(96).center();
appearanceStepRow.add(previewBox).size(120, 160);
```

(A dashed border would match the mockup more closely, but VisUI has no built-in dashed-line drawable without a nine-patch asset — a solid faint fill reads as "placeholder" clearly enough without inventing new art infrastructure for a box that renders nothing yet.)

## 9. Stat bar recoloring

`VisProgressBar` in `createStatRow` currently uses VisUI's stock default style (blue). No custom `VisProgressBar` style exists yet in this codebase's placeholder-rendering setup — building one from raw textures the way `UiTheme` does for buttons is out of scope for this pass (progress bar styles need knob/background/knob-before drawables, more pieces than a button). Simplest in-scope fix: `progressBar.getStyle().knobBefore` isn't safely mutable per-instance without cloning VisUI's shared style object (mutating it would recolor every progress bar in the app, including Achievements' and Characters' XP bars). Leave the stat bars in VisUI's default style for this pass — flag as a follow-up K-epic item once a proper shared progress-bar style exists, rather than risk a shared-mutable-style bug by hacking one in here.

## 10. Texture lifecycle note

Unlike every other redesigned screen this pass, `setupUI()` here legitimately needs to run on every step change (not just on a localization refresh), and per-row textures (swatches, step-indicator dots, faction card backgrounds) are genuinely rebuilt each time rather than built once at construction — because their content (which option is selected, which step is active) changes across those rebuilds, not just their language. Every such texture must go into `disposables` (already a `List<Disposable>` field) so `dispose()` cleans them all up — this screen will create more total textures over its lifetime than others, and that's expected, not a leak, as long as every one lands in `disposables`. The genuinely-static assets (`crestGoldTexture`, `crestCrimsonTexture`, the new `silverTexture`/`blueTexture`, `goldRingDrawable`'s backing texture) should still be built once in the constructor, same discipline as `ConnectionScreen`.

## 11. Full localization

New migration, `server/src/main/resources/db/migration/V29__Add_Character_Creation_Localization.sql` — the largest single localization batch in the M4 pass, since this was the least-localized screen in the app:

```sql
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_LBL_CREATE_CHARACTER', 'UI'), ('UI_LBL_STEP_IDENTITY', 'UI'), ('UI_LBL_STEP_APPEARANCE', 'UI'),
    ('UI_LBL_STEP_STATS', 'UI'), ('UI_LBL_CHARACTER_NAME', 'UI'), ('UI_HINT_ENTER_NAME', 'UI'),
    ('UI_LBL_FACTION', 'UI'), ('UI_FACTION_WARDENS_NAME', 'UI'), ('UI_FACTION_WARDENS_TAGLINE', 'UI'),
    ('UI_FACTION_SKYBORN_NAME', 'UI'), ('UI_FACTION_SKYBORN_TAGLINE', 'UI'),
    ('UI_LBL_GENDER', 'UI'), ('UI_OPT_GENDER_MALE', 'UI'), ('UI_OPT_GENDER_FEMALE', 'UI'),
    ('UI_LBL_HAIR_TYPE', 'UI'), ('UI_OPT_HAIR_SHORT', 'UI'), ('UI_OPT_HAIR_LONG', 'UI'), ('UI_OPT_HAIR_BALD', 'UI'),
    ('UI_LBL_HAIR_COLOR', 'UI'), ('UI_OPT_HAIRCOLOR_BLACK', 'UI'), ('UI_OPT_HAIRCOLOR_BROWN', 'UI'),
    ('UI_OPT_HAIRCOLOR_BLONDE', 'UI'), ('UI_OPT_HAIRCOLOR_RED', 'UI'),
    ('UI_LBL_SKIN_COLOR', 'UI'), ('UI_OPT_SKINCOLOR_PALE', 'UI'), ('UI_OPT_SKINCOLOR_LIGHT', 'UI'),
    ('UI_OPT_SKINCOLOR_TAN', 'UI'), ('UI_OPT_SKINCOLOR_DARK', 'UI'),
    ('UI_MSG_CHARACTER_PREVIEW_PLACEHOLDER', 'UI'), ('UI_LBL_POINTS_REMAINING', 'UI'),
    ('UI_STAT_STRENGTH', 'UI'), ('UI_STAT_DESC_STRENGTH', 'UI'),
    ('UI_STAT_DEXTERITY', 'UI'), ('UI_STAT_DESC_DEXTERITY', 'UI'),
    ('UI_STAT_VITALITY', 'UI'), ('UI_STAT_DESC_VITALITY', 'UI'),
    ('UI_STAT_INTELLIGENCE', 'UI'), ('UI_STAT_DESC_INTELLIGENCE', 'UI'),
    ('UI_STAT_WISDOM', 'UI'), ('UI_STAT_DESC_WISDOM', 'UI'),
    ('UI_STAT_SPIRIT', 'UI'), ('UI_STAT_DESC_SPIRIT', 'UI'),
    ('UI_STAT_SPEED', 'UI'), ('UI_STAT_DESC_SPEED', 'UI'),
    ('UI_STAT_INSIGHT', 'UI'), ('UI_STAT_DESC_INSIGHT', 'UI'),
    ('UI_BTN_NEXT', 'UI'), ('UI_BTN_CREATE', 'UI'),
    ('UI_LBL_INVALID_NAME_TITLE', 'UI'), ('UI_MSG_INVALID_NAME', 'UI'), ('UI_BTN_OK', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CREATE_CHARACTER'), 'tr_TR', 'Karakter Oluştur'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CREATE_CHARACTER'), 'en_US', 'Create character'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_STEP_IDENTITY'), 'tr_TR', 'Kimlik'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_STEP_IDENTITY'), 'en_US', 'Identity'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_STEP_APPEARANCE'), 'tr_TR', 'Görünüm'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_STEP_APPEARANCE'), 'en_US', 'Appearance'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_STEP_STATS'), 'tr_TR', 'Özellikler'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_STEP_STATS'), 'en_US', 'Stats'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_NAME'), 'tr_TR', 'Karakter adı'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_NAME'), 'en_US', 'Character name'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_HINT_ENTER_NAME'), 'tr_TR', 'İsim girin'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_HINT_ENTER_NAME'), 'en_US', 'Enter name'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_FACTION'), 'tr_TR', 'Fraksiyon'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_FACTION'), 'en_US', 'Faction'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_FACTION_WARDENS_NAME'), 'tr_TR', 'Muhafızlar'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_FACTION_WARDENS_NAME'), 'en_US', 'The Wardens'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_FACTION_WARDENS_TAGLINE'), 'tr_TR', 'Teslimiyetle gelen kesinlik. Maskeyi tak, nişanını bırak — asla ıskalamazsın.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_FACTION_WARDENS_TAGLINE'), 'en_US', 'Certainty through submission. Wear the mask, surrender your aim — you will never miss.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_FACTION_SKYBORN_NAME'), 'tr_TR', 'Gökdoğanlar'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_FACTION_SKYBORN_NAME'), 'en_US', 'The Skyborn'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_FACTION_SKYBORN_TAGLINE'), 'tr_TR', 'Riskle gelen özgürlük. Yarı sis, yarı yıldırım — ıskalayabilirsin ama köşeye sıkıştırılamazsın.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_FACTION_SKYBORN_TAGLINE'), 'en_US', 'Freedom through risk. Half mist, half lightning — you might miss, but you can''t be pinned down.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_GENDER'), 'tr_TR', 'Cinsiyet'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_GENDER'), 'en_US', 'Gender'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_GENDER_MALE'), 'tr_TR', 'Erkek'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_GENDER_MALE'), 'en_US', 'Male'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_GENDER_FEMALE'), 'tr_TR', 'Kadın'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_GENDER_FEMALE'), 'en_US', 'Female'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_HAIR_TYPE'), 'tr_TR', 'Saç tipi'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_HAIR_TYPE'), 'en_US', 'Hair type'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIR_SHORT'), 'tr_TR', 'Kısa'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIR_SHORT'), 'en_US', 'Short'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIR_LONG'), 'tr_TR', 'Uzun'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIR_LONG'), 'en_US', 'Long'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIR_BALD'), 'tr_TR', 'Kel'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIR_BALD'), 'en_US', 'Bald'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_HAIR_COLOR'), 'tr_TR', 'Saç rengi'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_HAIR_COLOR'), 'en_US', 'Hair color'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIRCOLOR_BLACK'), 'tr_TR', 'Siyah'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIRCOLOR_BLACK'), 'en_US', 'Black'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIRCOLOR_BROWN'), 'tr_TR', 'Kahverengi'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIRCOLOR_BROWN'), 'en_US', 'Brown'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIRCOLOR_BLONDE'), 'tr_TR', 'Sarışın'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIRCOLOR_BLONDE'), 'en_US', 'Blonde'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIRCOLOR_RED'), 'tr_TR', 'Kızıl'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_HAIRCOLOR_RED'), 'en_US', 'Red'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_SKIN_COLOR'), 'tr_TR', 'Ten rengi'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_SKIN_COLOR'), 'en_US', 'Skin color'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_SKINCOLOR_PALE'), 'tr_TR', 'Soluk'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_SKINCOLOR_PALE'), 'en_US', 'Pale'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_SKINCOLOR_LIGHT'), 'tr_TR', 'Açık'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_SKINCOLOR_LIGHT'), 'en_US', 'Light'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_SKINCOLOR_TAN'), 'tr_TR', 'Bronz'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_SKINCOLOR_TAN'), 'en_US', 'Tan'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_SKINCOLOR_DARK'), 'tr_TR', 'Koyu'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_OPT_SKINCOLOR_DARK'), 'en_US', 'Dark'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_CHARACTER_PREVIEW_PLACEHOLDER'), 'tr_TR', 'Karakter önizlemesi yakında'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_CHARACTER_PREVIEW_PLACEHOLDER'), 'en_US', 'Character preview coming soon'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_POINTS_REMAINING'), 'tr_TR', '%d puan kaldı'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_POINTS_REMAINING'), 'en_US', '%d points remaining'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_STRENGTH'), 'tr_TR', 'Güç'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_STRENGTH'), 'en_US', 'Strength'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_STRENGTH'), 'tr_TR', 'Fiziksel hasarı ve taşıma kapasitesini artırır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_STRENGTH'), 'en_US', 'Increases physical damage and carry weight.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DEXTERITY'), 'tr_TR', 'Çeviklik'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DEXTERITY'), 'en_US', 'Dexterity'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_DEXTERITY'), 'tr_TR', 'Saldırı hızını ve isabet oranını artırır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_DEXTERITY'), 'en_US', 'Improves attack speed and accuracy.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_VITALITY'), 'tr_TR', 'Dayanıklılık'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_VITALITY'), 'en_US', 'Vitality'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_VITALITY'), 'tr_TR', 'Maksimum canı ve dirençleri artırır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_VITALITY'), 'en_US', 'Boosts maximum health and resistances.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_INTELLIGENCE'), 'tr_TR', 'Zeka'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_INTELLIGENCE'), 'en_US', 'Intelligence'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_INTELLIGENCE'), 'tr_TR', 'Büyü hasarını ve etkinliğini artırır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_INTELLIGENCE'), 'en_US', 'Increases magical damage and effectiveness.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_WISDOM'), 'tr_TR', 'Bilgelik'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_WISDOM'), 'en_US', 'Wisdom'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_WISDOM'), 'tr_TR', 'Büyü hızını ve başarı oranını artırır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_WISDOM'), 'en_US', 'Improves spell casting speed and success rate.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_SPIRIT'), 'tr_TR', 'Ruh'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_SPIRIT'), 'en_US', 'Spirit'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_SPIRIT'), 'tr_TR', 'Maksimum manayı ve büyü direncini artırır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_SPIRIT'), 'en_US', 'Boosts maximum mana and magic resistance.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_SPEED'), 'tr_TR', 'Hız'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_SPEED'), 'en_US', 'Speed'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_SPEED'), 'tr_TR', 'Kaçınma şansını ve sıra önceliğini artırır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_SPEED'), 'en_US', 'Increases dodge chance and turn priority.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_INSIGHT'), 'tr_TR', 'Sezgi'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_INSIGHT'), 'en_US', 'Insight'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_INSIGHT'), 'tr_TR', 'Evcil hayvan etkinliğini ve özel yetenekleri artırır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_STAT_DESC_INSIGHT'), 'en_US', 'Improves pet effectiveness and special abilities.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NEXT'), 'tr_TR', 'İleri'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NEXT'), 'en_US', 'Next'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_CREATE'), 'tr_TR', 'Oluştur'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_CREATE'), 'en_US', 'Create'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_INVALID_NAME_TITLE'), 'tr_TR', 'Geçersiz İsim'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_INVALID_NAME_TITLE'), 'en_US', 'Invalid name'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_INVALID_NAME'), 'tr_TR', 'Lütfen geçerli bir karakter adı girin.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_INVALID_NAME'), 'en_US', 'Please enter a valid character name.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_OK'), 'tr_TR', 'Tamam'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_OK'), 'en_US', 'OK');
```

`optionLocKey(String)` and the stat name/description key lookups are straightforward switch expressions or `Map<String,String>` lookups from the raw `Appearance`/stat-name constant to its `UI_*` key above — mechanical, not worth inlining here.

## 12. Testing / Definition of Done

1. `gradlew.bat lwjgl3:run`, reach Character Creation — confirm three steps navigate correctly (Next/Back/Create swap appropriately, step dots update, Back on step 1 exits to Characters).
2. Confirm every option (faction, gender, hair type, hair color, skin color) visibly shows which one is currently selected, and that selecting a different option in the same row correctly un-highlights the previous one.
3. Confirm the name field shows genuinely empty (grayed placeholder only) until typed into, and that leaving it empty and clicking Next shows the invalid-name dialog instead of silently advancing.
4. Confirm the faction cards show the right accent (crimson+gold for the Wardens, blue+silver for the Skyborn) only on the selected one.
5. Confirm the character-preview box appears on the Appearance step, clearly inert.
6. Switch language, confirm every string on all three steps (including stat names/descriptions and both factions' taglines) updates correctly.
7. Complete character creation end to end — confirm the created character still has the correct faction/appearance/stats server-side (unchanged request-building logic, just confirm the wizard didn't break the actual submission).

Definition of done: matches the approved mockups; every option shows real selected-state feedback; the name field uses real placeholder text; faction identity uses "The Wardens"/"The Skyborn" throughout with per-faction color; a preview slot exists on the Appearance step; every string is localized.
