package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.widget.VisLabel;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.screen.action.ScreenAction;

/**
 * Builder for character row UI component (Builder Pattern).
 * Centralizes character row creation, reducing code duplication.
 */
public class CharacterRowBuilder {
    private Character character;
    private Texture avatarTexture;
    private Texture rowBackgroundTexture;
    private TextButton.TextButtonStyle buttonStyle;
    private ScreenAction onPlayAction;
    private ScreenAction onDeleteAction;

    public CharacterRowBuilder(Character character) {
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

    public CharacterRowBuilder withButtonStyle(TextButton.TextButtonStyle style) {
        this.buttonStyle = style;
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
        infoTable.add(new VisLabel("Level: " + character.level())).left().row();
        infoTable.add(new VisLabel("EXP: " + character.experience())).left();
        row.add(infoTable).expandX().fillX();

        Table actionsTable = new Table();
        new UIButtonBuilder("Play")
            .withStyle(buttonStyle)
            .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
            .withAction(onPlayAction)
            .buildAndAddTo(actionsTable, 5);
        actionsTable.row();
        new UIButtonBuilder("Delete")
            .withStyle(buttonStyle)
            .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
            .withAction(onDeleteAction)
            .buildAndAddTo(actionsTable);
        row.add(actionsTable).right().padLeft(20);

        return row;
    }
}

