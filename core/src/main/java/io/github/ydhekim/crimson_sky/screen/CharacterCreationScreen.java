package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.Appearance;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterRequest;
import io.github.ydhekim.crimson_sky.screen.factory.ScreenRouter;
import io.github.ydhekim.crimson_sky.ui.CrestFactory;
import io.github.ydhekim.crimson_sky.ui.TextureFactory;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;
import io.github.ydhekim.crimson_sky.ui.UiMetrics;
import io.github.ydhekim.crimson_sky.ui.UiPalette;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Character creation, laid out as a three-step wizard (Identity → Appearance → Stats) rather than one
 * dense panel — see the M4 screen pass. Each step change rebuilds the whole panel through
 * {@link #setupUI()}, the same full-rebuild convention every other screen already uses for localization
 * refreshes, just parameterized by {@link #currentStep} as well as by language.
 */
public class CharacterCreationScreen extends BaseScreen {

    // Per-stat cap is the shared lifetime ceiling (Stats.MAX_STAT_VALUE, system design §15), so the
    // creation screen and server-side spend validation can never drift apart. The creation pool below is
    // small enough that no stat gets near the cap at creation — it becomes the binding limit only later,
    // as leveling grants points to spend.
    private static final int INITIAL_STAT_POOL = 20;

    private static final int STEP_COUNT = 3;
    private static final float STEP_DOT_WIDTH = 22f;
    private static final float STEP_DOT_HEIGHT = 3f;
    private static final float CREST_OUTER_SIZE = 36f;
    private static final float CREST_INNER_SIZE = 26f;
    private static final int SWATCH_SIZE = 28;
    private static final float SWATCH_RING = 2f;
    private static final float OPTION_LABEL_WIDTH = 110f;

    /** Canonical stat keys — also the {@code UI_STAT_*}/{@code UI_STAT_DESC_*} localization key suffixes. */
    private static final String[] STAT_NAMES = {
        "Strength", "Dexterity", "Vitality", "Intelligence", "Wisdom", "Spirit", "Speed", "Insight"
    };

    // Swatch colors for the two color rows. Keyed by the raw Appearance constants so the curated lists
    // in Appearance stay the single source of truth for *what* is offered; this only says what each
    // option looks like.
    private static final ObjectMap<String, Color> SWATCH_COLORS = new ObjectMap<>();
    static {
        SWATCH_COLORS.put("BLACK", new Color(0.11f, 0.10f, 0.09f, 1f));
        SWATCH_COLORS.put("BROWN", new Color(0.353f, 0.227f, 0.141f, 1f));
        SWATCH_COLORS.put("BLONDE", new Color(0.851f, 0.706f, 0.353f, 1f));
        SWATCH_COLORS.put("RED", UiPalette.ACCENT_CRIMSON);
        SWATCH_COLORS.put("PALE", new Color(0.910f, 0.788f, 0.659f, 1f));
        SWATCH_COLORS.put("LIGHT", new Color(0.851f, 0.659f, 0.471f, 1f));
        SWATCH_COLORS.put("TAN", new Color(0.659f, 0.475f, 0.310f, 1f));
        SWATCH_COLORS.put("DARK", new Color(0.420f, 0.271f, 0.188f, 1f));
    }

    private int currentStep = 1;

    private VisTextField nameField;
    /** Survives the panel rebuilds between steps — {@link #nameField} itself is recreated per step-1 build. */
    private String characterName = "";
    private VisLabel statPoolLabel;

    private final ObjectMap<String, Integer> stats = new ObjectMap<>();
    private final ObjectMap<String, VisProgressBar> statProgressBars = new ObjectMap<>();
    private final ObjectMap<String, VisLabel> statValueLabels = new ObjectMap<>();

    private int statPool = INITIAL_STAT_POOL;
    private Faction selectedFaction = Faction.CRIMSON;

    // Purely cosmetic (system design §23), defaulting to each curated list's first entry. The lists live in
    // Appearance so the button UI here and the server's validation read one source of truth.
    private String selectedGender = Appearance.GENDERS.get(0);
    private String selectedHairType = Appearance.HAIR_TYPES.get(0);
    private String selectedHairColor = Appearance.HAIR_COLORS.get(0);
    private String selectedSkinColor = Appearance.SKIN_COLORS.get(0);

    // Built once in the constructor and reused across every rebuild, disposed in dispose() — same
    // discipline as ConnectionScreen's crest textures. Their content never depends on step or language.
    private Texture crestGoldTexture;
    private Texture crestCrimsonTexture;
    private Texture crestSilverTexture;
    private Texture crestBlueTexture;
    private Texture goldRingTexture;
    private Texture infoIconTexture;
    private TextureRegionDrawable goldRingDrawable;
    private VisImageButton.VisImageButtonStyle infoIconStyle;
    private final ObjectMap<String, Texture> swatchTextures = new ObjectMap<>();

    // Textures whose *content* changes per rebuild (which step is active, which faction is selected),
    // so they genuinely cannot be built once. Disposed and rebuilt on each setupUI() rather than
    // accumulating for the screen's lifetime — this screen rebuilds far more often than the others.
    private final Array<Texture> stepTextures = new Array<>();

    public CharacterCreationScreen(CrimsonSky game) {
        super(game);
        initializeStats();
        initializeCreationVisuals();
        setupUI();
    }

    private void initializeStats() {
        for (String name : STAT_NAMES) {
            stats.put(name, 5);
        }
    }

    /** Creates the rebuild-independent placeholder textures; see the field comment above for why. */
    private void initializeCreationVisuals() {
        crestGoldTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_GOLD);
        crestCrimsonTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_CRIMSON);
        crestSilverTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_SILVER);
        crestBlueTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_BLUE);

        goldRingTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_GOLD);
        goldRingDrawable = new TextureRegionDrawable(new TextureRegion(goldRingTexture));

        for (ObjectMap.Entry<String, Color> entry : SWATCH_COLORS) {
            swatchTextures.put(entry.key, TextureFactory.createSolidTexture(SWATCH_SIZE, SWATCH_SIZE, entry.value));
        }

        infoIconStyle = createInfoIconStyle();
    }

    /**
     * {@link ScreenRouter} caches screens, so this instance is reused every time the player comes back
     * from the character list — and a wizard, unlike the single panels this replaced, would otherwise
     * reopen parked on step 3 with the previous run's name still filled in, one click away from
     * submitting it again without ever passing step 1's validation. Each visit starts a fresh creation.
     */
    @Override
    public void show() {
        super.show();
        resetWizard();
    }

    private void resetWizard() {
        currentStep = 1;
        characterName = "";
        nameField = null;
        statPool = INITIAL_STAT_POOL;
        selectedFaction = Faction.CRIMSON;
        selectedGender = Appearance.GENDERS.get(0);
        selectedHairType = Appearance.HAIR_TYPES.get(0);
        selectedHairColor = Appearance.HAIR_COLORS.get(0);
        selectedSkinColor = Appearance.SKIN_COLORS.get(0);
        initializeStats();
        setupUI();
    }

    private void setupUI() {
        // The text field is recreated on every step-1 build, so capture what the player typed first.
        if (nameField != null) {
            characterName = nameField.getText();
        }

        stage.clear();
        disposeStepTextures();

        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_CREATE_CHARACTER"), "title");
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

    /** Progress dots (filled up to the current step) plus the current step's name. */
    private VisTable buildStepIndicator() {
        VisTable indicator = new VisTable();

        VisTable dots = new VisTable();
        for (int i = 1; i <= STEP_COUNT; i++) {
            Texture dotTexture = TextureFactory.createSolidTexture(1, 1,
                i <= currentStep ? UiPalette.ACCENT_CRIMSON : new Color(0.23f, 0.22f, 0.2f, 1f));
            stepTextures.add(dotTexture);
            dots.add(new Image(new TextureRegionDrawable(new TextureRegion(dotTexture))))
                .size(STEP_DOT_WIDTH, STEP_DOT_HEIGHT).padRight(8);
        }
        indicator.add(dots).row();

        VisLabel stepLabel = new VisLabel(game.getLanguageManager().get(stepNameKey()));
        stepLabel.setColor(UiPalette.TEXT_MUTED);
        indicator.add(stepLabel).padTop(6);

        return indicator;
    }

    private String stepNameKey() {
        return switch (currentStep) {
            case 1 -> "UI_LBL_STEP_IDENTITY";
            case 2 -> "UI_LBL_STEP_APPEARANCE";
            default -> "UI_LBL_STEP_STATS";
        };
    }

    // ===== Step 1: identity =====

    private VisTable buildIdentityStep() {
        VisTable step = new VisTable();
        step.top();

        VisTable nameRow = new VisTable();
        nameRow.add(new VisLabel(game.getLanguageManager().get("UI_LBL_CHARACTER_NAME"))).width(140).left();
        nameField = new VisTextField(characterName);
        // A real message text, not prefilled content: getText() stays empty until the player types, so
        // the emptiness check below is the same one every other field in this codebase uses.
        nameField.setMessageText(game.getLanguageManager().get("UI_HINT_ENTER_NAME"));
        nameRow.add(nameField).width(300).left();
        step.add(nameRow).left().padBottom(24).row();

        VisLabel factionLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_FACTION"));
        factionLabel.setColor(UiPalette.TEXT_MUTED);
        step.add(factionLabel).left().padBottom(8).row();

        VisTable factionRow = new VisTable();
        factionRow.add(createFactionCard(Faction.CRIMSON, "UI_FACTION_WARDENS_NAME", "UI_FACTION_WARDENS_TAGLINE",
            crestGoldTexture, crestCrimsonTexture, UiPalette.ACCENT_CRIMSON, UiPalette.ACCENT_GOLD))
            .expandX().fillX().padRight(12);
        factionRow.add(createFactionCard(Faction.SKYBORN, "UI_FACTION_SKYBORN_NAME", "UI_FACTION_SKYBORN_TAGLINE",
            crestSilverTexture, crestBlueTexture, UiPalette.ACCENT_BLUE, UiPalette.ACCENT_SILVER))
            .expandX().fillX();
        step.add(factionRow).expandX().fillX();

        return step;
    }

    /**
     * One faction card: crest, name, lore tagline. Not a {@link TextButton} — the composite layout needs
     * a table — so selection is a manual two-state restyle instead of a {@code ButtonGroup}, which is
     * proportionate for exactly two mutually exclusive cards.
     */
    private VisTable createFactionCard(Faction faction, String nameKey, String taglineKey,
                                       Texture crestOuterTexture, Texture crestInnerTexture,
                                       Color accentColor, Color accentBorderColor) {
        boolean selected = faction == selectedFaction;

        VisTable card = new VisTable();
        card.pad(16);
        card.left().top();

        Stack crest = CrestFactory.build(crestOuterTexture, crestInnerTexture, CREST_OUTER_SIZE, CREST_INNER_SIZE);
        card.add(crest).size(CREST_OUTER_SIZE, CREST_OUTER_SIZE).padRight(12).top();

        VisTable textColumn = new VisTable();
        VisLabel nameLabel = new VisLabel(game.getLanguageManager().get(nameKey));
        nameLabel.setFontScale(1.1f);
        // The faction's secondary accent (gold / silver) is what marks the selected card's name; there is
        // no real bordered-drawable support here (same limitation ConnectionScreen's crest documents), so
        // the background tint below plus this label color are what read as "selected" at a glance.
        nameLabel.setColor(selected ? accentBorderColor : Color.WHITE);
        textColumn.add(nameLabel).left().padBottom(4).row();

        VisLabel taglineLabel = new VisLabel(game.getLanguageManager().get(taglineKey));
        taglineLabel.setWrap(true);
        taglineLabel.setColor(UiPalette.TEXT_MUTED);
        textColumn.add(taglineLabel).width(240).left();
        card.add(textColumn).top();

        Texture bgTexture = TextureFactory.createSolidTexture(1, 1, selected
            ? new Color(accentColor.r, accentColor.g, accentColor.b, 0.16f)
            : new Color(1f, 1f, 1f, 0.03f));
        stepTextures.add(bgTexture);
        card.setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));

        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedFaction = faction;
                setupUI(); // full rebuild picks up the new selection on both cards
            }
        });
        return card;
    }

    // ===== Step 2: appearance =====

    private VisTable buildAppearanceStep() {
        VisTable step = new VisTable();
        step.top();

        step.add(buildPreviewBox()).size(120, 160).top().padRight(24);

        VisTable options = new VisTable();
        options.add(createOptionRow("UI_LBL_GENDER", "UI_OPT_GENDER_", Appearance.GENDERS,
            selectedGender, v -> selectedGender = v)).left().padBottom(12).row();
        options.add(createOptionRow("UI_LBL_HAIR_TYPE", "UI_OPT_HAIR_", Appearance.HAIR_TYPES,
            selectedHairType, v -> selectedHairType = v)).left().padBottom(12).row();
        options.add(createSwatchRow("UI_LBL_HAIR_COLOR", "UI_OPT_HAIRCOLOR_", Appearance.HAIR_COLORS,
            selectedHairColor, v -> selectedHairColor = v)).left().padBottom(12).row();
        options.add(createSwatchRow("UI_LBL_SKIN_COLOR", "UI_OPT_SKINCOLOR_", Appearance.SKIN_COLORS,
            selectedSkinColor, v -> selectedSkinColor = v)).left();
        step.add(options).top().expandX().left();

        return step;
    }

    /**
     * A reserved, deliberately inert slot for the character preview. Nothing renders appearance data yet
     * (system design §23: M4 is still placeholder-rendering), so this only holds the layout's place for
     * M5's real art pipeline.
     */
    private VisTable buildPreviewBox() {
        VisTable previewBox = new VisTable();
        Texture previewBgTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.04f));
        stepTextures.add(previewBgTexture);
        previewBox.setBackground(new TextureRegionDrawable(new TextureRegion(previewBgTexture)));

        VisLabel previewLabel = new VisLabel(game.getLanguageManager().get("UI_MSG_CHARACTER_PREVIEW_PLACEHOLDER"));
        previewLabel.setWrap(true);
        previewLabel.setColor(new Color(0.35f, 0.34f, 0.32f, 1f));
        previewLabel.setAlignment(Align.center);
        previewBox.add(previewLabel).width(96).center();
        return previewBox;
    }

    /**
     * A one-of-many option row. The {@link ButtonGroup} is what makes selection visible at all: it keeps
     * exactly one button checked, and {@code toggleButtonStyle} is the only style in {@link
     * io.github.ydhekim.crimson_sky.ui.UiTheme} that draws a checked state differently.
     */
    private VisTable createOptionRow(String labelKey, String optionKeyPrefix, List<String> options,
                                     String initiallySelected, Consumer<String> onSelect) {
        VisTable rowTable = new VisTable();
        rowTable.add(new VisLabel(game.getLanguageManager().get(labelKey))).width(OPTION_LABEL_WIDTH).left();

        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        group.setMaxCheckCount(1);

        for (String option : options) {
            // build() rather than buildAndAddTo(): the button has to be checked and grouped before it is
            // usable as one of a mutually exclusive set.
            TextButton button = new UIButtonBuilder(game.getLanguageManager().get(optionKeyPrefix + option))
                .withStyle(toggleButtonStyle)
                .withAction(() -> onSelect.accept(option))
                .build();
            button.setChecked(option.equals(initiallySelected));
            group.add(button);
            rowTable.add(button).size(UiMetrics.SMALL_BUTTON_WIDTH, UiMetrics.SMALL_BUTTON_HEIGHT).padRight(4);
        }
        return rowTable;
    }

    /**
     * Same contract as {@link #createOptionRow}, but for the two rows where the choice *is* a color: a
     * solid swatch reads instantly where "Blonde" as text does not. The gold ring around the selected
     * swatch stands in for a border — the cell always reserves the ring's padding, so selecting a
     * different swatch never shifts the row's layout. Each swatch carries its localized color name as a
     * tooltip, since the square itself is unlabeled.
     */
    private VisTable createSwatchRow(String labelKey, String optionKeyPrefix, List<String> options,
                                     String initiallySelected, Consumer<String> onSelect) {
        VisTable rowTable = new VisTable();
        rowTable.add(new VisLabel(game.getLanguageManager().get(labelKey))).width(OPTION_LABEL_WIDTH).left();

        for (String option : options) {
            Image swatch = new Image(new TextureRegionDrawable(new TextureRegion(swatchTextures.get(option))));

            VisTable swatchCell = new VisTable();
            swatchCell.pad(SWATCH_RING);
            swatchCell.setBackground(option.equals(initiallySelected) ? goldRingDrawable : null);
            swatchCell.add(swatch).size(SWATCH_SIZE, SWATCH_SIZE);
            swatchCell.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onSelect.accept(option);
                    setupUI(); // simplest correct refresh — same full rebuild every step transition uses
                }
            });
            new Tooltip.Builder(game.getLanguageManager().get(optionKeyPrefix + option)).target(swatchCell).build();

            rowTable.add(swatchCell).padRight(8);
        }
        return rowTable;
    }

    // ===== Step 3: stats =====

    private VisTable buildStatsStep() {
        VisTable statsTable = new VisTable();
        statsTable.top();

        statPoolLabel = new VisLabel(pointsRemainingText());
        statPoolLabel.setColor(UiPalette.ACCENT_GOLD);
        statsTable.add(statPoolLabel).center().padBottom(15).row();

        for (String name : STAT_NAMES) {
            statsTable.add(createStatRow(name)).expandX().fillX().padBottom(5).row();
        }

        return statsTable;
    }

    private String pointsRemainingText() {
        return String.format(game.getLanguageManager().get("UI_LBL_POINTS_REMAINING"), statPool);
    }

    private VisTable createStatRow(String name) {
        VisTable rowTable = new VisTable();

        // Locale.ROOT: under a Turkish default locale "Insight".toUpperCase() yields "İNSİGHT", which
        // would miss the seeded key entirely — and Turkish is exactly this app's other language.
        String upperName = name.toUpperCase(Locale.ROOT);
        rowTable.add(new VisLabel(game.getLanguageManager().get("UI_STAT_" + upperName))).width(120).left();

        VisImageButton infoButton = new VisImageButton(infoIconStyle);
        new Tooltip.Builder(game.getLanguageManager().get("UI_STAT_DESC_" + upperName)).target(infoButton).build();
        rowTable.add(infoButton).padRight(5);

        // Uses VisUI's bundled default progress-bar style; the custom "stat-bar" style was removed with
        // uiskin.json in the M4 foundation cleanup (prompt 24). A crimson/gold restyle needs a shared
        // progress-bar style first — mutating VisUI's shared style object here would recolor every bar in
        // the app (Achievements, Characters), so that stays a follow-up rather than a local hack.
        VisProgressBar progressBar = new VisProgressBar(0, Stats.MAX_STAT_VALUE, 1, false);
        progressBar.setValue(stats.get(name));
        statProgressBars.put(name, progressBar);
        rowTable.add(progressBar).expandX().fillX().padRight(10);

        VisLabel valueLabel = new VisLabel(stats.get(name).toString());
        statValueLabels.put(name, valueLabel);
        rowTable.add(valueLabel).width(25).center().padRight(10);

        TextButton minusButton = new UIButtonBuilder("-")
            .withStyle(squareButtonStyle)
            .withSize(UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_BUTTON_SIZE)
            .withAction(() -> adjustStat(name, -1))
            .build();

        TextButton plusButton = new UIButtonBuilder("+")
            .withStyle(squareButtonStyle)
            .withSize(UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_BUTTON_SIZE)
            .withAction(() -> adjustStat(name, 1))
            .build();

        rowTable.add(minusButton).pad(0, 2, 0, 2);
        rowTable.add(plusButton);

        return rowTable;
    }

    private void adjustStat(String name, int amount) {
        int currentValue = stats.get(name);

        if (amount > 0 && statPool > 0 && currentValue < Stats.MAX_STAT_VALUE) {
            statPool--;
            stats.put(name, currentValue + 1);
        } else if (amount < 0 && currentValue > 0) {
            statPool++;
            stats.put(name, currentValue - 1);
        }

        updateStatUI(name);
    }

    private void updateStatUI(String name) {
        int value = stats.get(name);
        statProgressBars.get(name).setValue(value);
        statValueLabels.get(name).setText(String.valueOf(value));
        statPoolLabel.setText(pointsRemainingText());
    }

    // ===== Navigation =====

    private VisTable buildFooter() {
        VisTable footer = new VisTable();

        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_BACK"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::handleBack)
            .buildAndAddTo(footer);

        footer.add().expandX();

        boolean lastStep = currentStep == STEP_COUNT;
        new UIButtonBuilder(game.getLanguageManager().get(lastStep ? "UI_BTN_CREATE" : "UI_BTN_NEXT"))
            .withStyle(accentButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(lastStep ? this::submitCharacterCreation : this::handleNext)
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

    /**
     * Name validation lives on the step 1 → 2 transition rather than at submit: name entry only exists on
     * step 1, so rejecting an empty one there beats letting it ride through two more steps first.
     */
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

    /**
     * Builds and sends the creation request. No name check here — {@link #handleNext()} is the only path
     * to the final step and already guarantees a non-empty name.
     */
    private void submitCharacterCreation() {
        Stats characterStats = new Stats(
            stats.get("Strength"),
            stats.get("Dexterity"),
            stats.get("Vitality"),
            stats.get("Intelligence"),
            stats.get("Wisdom"),
            stats.get("Spirit"),
            stats.get("Speed"),
            stats.get("Insight")
        );

        // maxHp, maxMp, maxStamina, baseDef, baseAtk — literal starting values (see V6 migration
        // note; stat-derived formulas from system design §4.2 are a later tuning concern).
        Character newCharacter = new Character(
            0, 0, characterName.trim(), selectedFaction, 1, 0, 100, 100, 100, 10, 10,
            characterStats,
            // Null arrays match what every character created to date persists; the consumables map is
            // real-but-empty on purpose (§18) — an absent one would NPE the first shop purchase.
            new Inventory(null, null, null, new HashMap<>()),
            new Loadout(null, null, null),
            new HashMap<>()
        );

        Appearance appearance = new Appearance(selectedGender, selectedHairType, selectedHairColor, selectedSkinColor);
        game.getNetworkClient().sendTCP(new CreateCharacterRequest(newCharacter, appearance));
        game.getScreenRouter().navigateTo(ScreenType.CHARACTERS);
    }

    @Override
    public void refreshUI() {
        setupUI();
    }

    private VisImageButton.VisImageButtonStyle createInfoIconStyle() {
        Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.drawCircle(8, 8, 7);
        pixmap.setColor(Color.BLACK);
        pixmap.fillCircle(8, 8, 6);
        pixmap.setColor(Color.WHITE);
        pixmap.fillRectangle(7, 7, 2, 5); // 'i' body
        pixmap.fillCircle(8, 4, 1);       // 'i' dot

        infoIconTexture = new Texture(pixmap);
        pixmap.dispose();

        VisImageButton.VisImageButtonStyle style = new VisImageButton.VisImageButtonStyle(VisUI.getSkin().get(VisImageButton.VisImageButtonStyle.class));
        style.imageUp = new TextureRegionDrawable(infoIconTexture);

        return style;
    }

    private void disposeStepTextures() {
        for (Texture texture : stepTextures) {
            texture.dispose();
        }
        stepTextures.clear();
    }

    @Override
    public void dispose() {
        super.dispose();
        disposeStepTextures();
        crestGoldTexture.dispose();
        crestCrimsonTexture.dispose();
        crestSilverTexture.dispose();
        crestBlueTexture.dispose();
        goldRingTexture.dispose();
        infoIconTexture.dispose();
        for (Texture texture : swatchTextures.values()) {
            texture.dispose();
        }
        swatchTextures.clear();
    }
}
