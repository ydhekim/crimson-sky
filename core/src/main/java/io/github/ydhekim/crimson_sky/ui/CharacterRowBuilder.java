package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisProgressBar;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.LevelCurve;
import io.github.ydhekim.crimson_sky.screen.action.ScreenAction;

/**
 * Builder for character row UI component (Builder Pattern).
 * Centralizes character row creation, reducing code duplication.
 */
public class CharacterRowBuilder {
    private final CrimsonSky game;
    private Character character;
    private Texture avatarTexture;
    private Texture rowBackgroundTexture;
    private TextButton.TextButtonStyle accentButtonStyle;
    private TextButton.TextButtonStyle iconButtonStyle;
    private ScreenAction onPlayAction;
    private ScreenAction onDeleteAction;

    public CharacterRowBuilder(CrimsonSky game, Character character) {
        this.game = game;
        this.character = character;
    }

    public CharacterRowBuilder withAvatarTexture(Texture texture) {
        this.avatarTexture = texture;
        return this;
    }

    public CharacterRowBuilder withRowBackgroundTexture(Texture texture) {
        this.rowBackgroundTexture = texture;
        return this;
    }

    /**
     * The two distinct styles this row needs: {@code accent} is the crimson primary-CTA style for the
     * Play button (the row's main action), {@code icon} the smaller icon-square style for the Delete "X".
     */
    public CharacterRowBuilder withButtonStyles(TextButton.TextButtonStyle accent, TextButton.TextButtonStyle icon) {
        this.accentButtonStyle = accent;
        this.iconButtonStyle = icon;
        return this;
    }

    public CharacterRowBuilder onPlay(ScreenAction action) {
        this.onPlayAction = action;
        return this;
    }

    public CharacterRowBuilder onDelete(ScreenAction action) {
        this.onDeleteAction = action;
        return this;
    }

    public Table build() {
        Table row = new Table();
        if (rowBackgroundTexture != null) {
            row.setBackground(new TextureRegionDrawable(new TextureRegion(rowBackgroundTexture)));
        }
        row.pad(10);

        if (avatarTexture != null) {
            Image avatar = new Image(avatarTexture);
            row.add(avatar).size(64, 64).padRight(20);
        }

        Table infoTable = new Table();
        infoTable.left();
        VisLabel nameLabel = new VisLabel(character.name());
        nameLabel.setFontScale(1.2f);
        infoTable.add(nameLabel).left().padBottom(5).row();

        int level = character.level();
        long exp = character.experience();

        VisTable levelRow = new VisTable();
        String levelText = String.format(game.getLanguageManager().get("UI_LBL_LEVEL_SHORT"), level);
        levelRow.add(new VisLabel(levelText)).padRight(8);

        if (level >= LevelCurve.LEVEL_CAP) {
            VisLabel maxLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_MAX_LEVEL"));
            maxLabel.setColor(UiPalette.TEXT_MUTED);
            levelRow.add(maxLabel);
        } else {
            long currentThreshold = LevelCurve.expNeededForLevel(level);
            long nextThreshold = LevelCurve.expNeededForLevel(level + 1);
            float progress = (float) (exp - currentThreshold) / (nextThreshold - currentThreshold);

            VisProgressBar xpBar = new VisProgressBar(0f, 1f, 0.01f, false);
            xpBar.setValue(progress);
            xpBar.setAnimateDuration(0f);
            levelRow.add(xpBar).width(200).padRight(8);

            String xpText = String.format(game.getLanguageManager().get("UI_LBL_XP_PROGRESS"),
                exp - currentThreshold, nextThreshold - currentThreshold);
            VisLabel xpLabel = new VisLabel(xpText);
            xpLabel.setColor(UiPalette.TEXT_MUTED);
            levelRow.add(xpLabel);
        }
        infoTable.add(levelRow).left();
        row.add(infoTable).expandX().fillX();

        Table actionsTable = new Table();
        // Play and Delete share one row of actionsTable (no actionsTable.row() between them). build()
        // directly instead of buildAndAddTo() so Play's cell can carry .padRight(8) for spacing — same
        // explicit-cell shape CharactersScreen.createCharacterButton already uses.
        TextButton playButton = new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_PLAY"))
            .withStyle(accentButtonStyle)
            .withAction(onPlayAction)
            .build();
        actionsTable.add(playButton).width(UiMetrics.DIALOG_BUTTON_WIDTH).height(UiMetrics.DIALOG_BUTTON_HEIGHT).padRight(8);
        new UIButtonBuilder("X")   // no icon font/atlas is shipped (M4 foundation cleanup) — a real trash-can
                                   // glyph is Epic E content-art work; "X" is the practical stand-in for now.
            .withStyle(iconButtonStyle)
            .withSize(UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_BUTTON_SIZE)
            .withAction(onDeleteAction)
            .buildAndAddTo(actionsTable);
        row.add(actionsTable).right().padLeft(20);

        return row;
    }
}
