package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterRequest;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;

public class CharacterCreationScreen extends BaseScreen {

    private static final int MAX_STAT_VALUE = 20;
    private static final int INITIAL_STAT_POOL = 20;

    private VisTextField nameField;
    private VisLabel factionDescriptionLabel;
    private VisLabel statPoolLabel;

    private final ObjectMap<String, Integer> stats = new ObjectMap<>();
    private final ObjectMap<String, VisProgressBar> statProgressBars = new ObjectMap<>();
    private final ObjectMap<String, VisLabel> statValueLabels = new ObjectMap<>();

    private int statPool = INITIAL_STAT_POOL;
    private Faction selectedFaction = Faction.A;

    public CharacterCreationScreen(CrimsonSky game) {
        super(game);
        initializeStats();
        setupUI();
    }

    private void initializeStats() {
        stats.put("Strength", 5);
        stats.put("Dexterity", 5);
        stats.put("Vitality", 5);
        stats.put("Intelligence", 5);
        stats.put("Wisdom", 5);
        stats.put("Spirit", 5);
        stats.put("Speed", 5);
        stats.put("Insight", 5);
    }

    private void setupUI() {
        VisTable mainPanel = createMainContentPanel();

        // --- Header ---
        VisLabel titleLabel = new VisLabel("Create Character");
        titleLabel.setFontScale(2f);
        mainPanel.add(titleLabel).padBottom(20).colspan(2).row();

        Table headerTable = new Table();
        headerTable.add(new VisLabel("Character Name: ")).padRight(10);
        nameField = new VisTextField("Enter Name");
        headerTable.add(nameField).width(300);
        mainPanel.add(headerTable).padBottom(20).colspan(2).row();

        // --- Middle Section ---
        VisTable middleTable = new VisTable();

        middleTable.add(createFactionSelectionTable()).expandX().fillX().padBottom(20).row();
        middleTable.add(createStatsTable()).expand().fill();

        mainPanel.add(middleTable).expand().fill().padBottom(20).row();

        // --- Footer ---
        mainPanel.add(createFooterTable()).expandX().fillX().bottom();
    }

    private VisTable createFactionSelectionTable() {
        VisTable factionTable = new VisTable();

        factionDescriptionLabel = new VisLabel("Description for Faction A. This faction focuses on might and raw power.", "small");
        factionDescriptionLabel.setWrap(true);

        // Use UIButtonBuilder with Command Pattern for faction selection
        TextButton factionAButton = new UIButtonBuilder("Faction A")
            .withStyle(customButtonStyle)
            .withSize(150, 40)
            .withAction(() -> {
                selectedFaction = Faction.A;
                factionDescriptionLabel.setText("Description for Faction A. This faction focuses on might and raw power.");
            })
            .build();

        TextButton factionBButton = new UIButtonBuilder("Faction B")
            .withStyle(customButtonStyle)
            .withSize(150, 40)
            .withAction(() -> {
                selectedFaction = Faction.B;
                factionDescriptionLabel.setText("Description for Faction B. This faction is known for its cunning and arcane knowledge.");
            })
            .build();

        Table buttonTable = new Table();
        buttonTable.add(factionAButton).pad(5);
        buttonTable.add(factionBButton).pad(5);

        factionTable.add(buttonTable).row();
        factionTable.add(new VisScrollPane(factionDescriptionLabel)).expandX().fillX().height(60).padTop(10);

        return factionTable;
    }

    private VisTable createStatsTable() {
        VisTable statsTable = new VisTable();
        statsTable.top();

        statPoolLabel = new VisLabel("Points Remaining: " + statPool);
        statsTable.add(statPoolLabel).colspan(6).center().padBottom(15).row();

        String[] statNames = {"Strength", "Dexterity", "Vitality", "Intelligence", "Wisdom", "Spirit", "Speed", "Insight"};
        String[] statDescriptions = {
            "Increases physical damage and carry weight.",
            "Improves attack speed and accuracy.",
            "Boosts maximum health and resistances.",
            "Increases magical damage and effectiveness.",
            "Improves spell casting speed and success rate.",
            "Boosts maximum mana and magic resistance.",
            "Increases dodge chance and turn priority.",
            "Improves pet effectiveness and special abilities."
        };

        for (int i = 0; i < statNames.length; i++) {
            statsTable.add(createStatRow(statNames[i], statDescriptions[i])).expandX().fillX().padBottom(5).row();
        }

        return statsTable;
    }

    private VisTable createStatRow(String name, String description) {
        VisTable rowTable = new VisTable();

        rowTable.add(new VisLabel(name)).width(100);

        VisImageButton infoButton = new VisImageButton(createInfoIconStyle());
        new Tooltip.Builder(description).target(infoButton).build();
        rowTable.add(infoButton).padRight(5);

        VisProgressBar progressBar = new VisProgressBar(0, MAX_STAT_VALUE, 1, false, "stat-bar");
        progressBar.setValue(stats.get(name));
        statProgressBars.put(name, progressBar);
        rowTable.add(progressBar).expandX().fillX().padRight(10);

        VisLabel valueLabel = new VisLabel(stats.get(name).toString());
        statValueLabels.put(name, valueLabel);
        rowTable.add(valueLabel).width(25).center().padRight(10);

        // Use UIButtonBuilder for stat adjustment buttons
        TextButton minusButton = new UIButtonBuilder("-")
            .withStyle(squareButtonStyle)
            .withSize(16, 16)
            .withAction(() -> adjustStat(name, -1))
            .build();

        TextButton plusButton = new UIButtonBuilder("+")
            .withStyle(squareButtonStyle)
            .withSize(16, 16)
            .withAction(() -> adjustStat(name, 1))
            .build();

        rowTable.add(minusButton).pad(0, 2, 0, 2);
        rowTable.add(plusButton);

        return rowTable;
    }

    private VisTable createFooterTable() {
        VisTable footerTable = new VisTable();

        new UIButtonBuilder("Back")
            .withStyle(customButtonStyle)
            .withSize(200, 40)
            .withAction(() -> game.getScreenRouter().navigateTo(ScreenType.CHARACTERS))
            .buildAndAddTo(footerTable);

        footerTable.add().expandX();

        new UIButtonBuilder("Create")
            .withStyle(customButtonStyle)
            .withSize(200, 40)
            .withAction(this::submitCharacterCreation)
            .buildAndAddTo(footerTable);

        return footerTable;
    }

    /**
     * Validates and submits character creation request.
     */
    private void submitCharacterCreation() {
        String characterName = nameField.getText();
        if (characterName.trim().isEmpty() || characterName.equals("Enter Name")) {
            new VisDialog("Invalid Name", "Please enter a valid character name.").button("OK").show(stage);
            return;
        }

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
            0, 0, characterName, selectedFaction, 1, 0, 100, 100, 100, 10, 10,
            characterStats,
            new Inventory(null, null, null),
            new Loadout(null, null, null)
        );

        game.getNetworkClient().sendTCP(new CreateCharacterRequest(newCharacter));
        game.getScreenRouter().navigateTo(ScreenType.CHARACTERS);
    }

    private void adjustStat(String name, int amount) {
        int currentValue = stats.get(name);

        if (amount > 0 && statPool > 0 && currentValue < MAX_STAT_VALUE) {
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
        statPoolLabel.setText("Points Remaining: " + statPool);
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

        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        VisImageButton.VisImageButtonStyle style = new VisImageButton.VisImageButtonStyle(VisUI.getSkin().get(VisImageButton.VisImageButtonStyle.class));
        style.imageUp = new TextureRegionDrawable(texture);

        return style;
    }
}
