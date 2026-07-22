package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Generates {@link TextButton.TextButtonStyle}s from solid-color placeholder textures (system design
 * §24) — no external skin/atlas, same spirit as {@link TextureFactory}'s panel/row textures (and the
 * combat action swatches a future combat-visual factory will generate the same way). Two distinct
 * styles instead of prompt 24's single stand-in: a standard button and a smaller, flatter one for
 * icon-square buttons (stat +/-, future loadout priority arrows — see §24).
 * <p>
 * Textures are owned by this instance; call {@link #dispose()} once per screen (BaseScreen already
 * does — see its dispose()).
 */
public class UiTheme implements Disposable {

    private final Array<Texture> textures = new Array<>();

    public TextButton.TextButtonStyle standardButtonStyle(BitmapFont font) {
        return buildStyle(font, new Color(0.25f, 0.25f, 0.28f, 1f), new Color(0.35f, 0.35f, 0.4f, 1f),
            new Color(0.18f, 0.18f, 0.2f, 1f));
    }

    public TextButton.TextButtonStyle iconButtonStyle(BitmapFont font) {
        return buildStyle(font, new Color(0.3f, 0.3f, 0.33f, 1f), new Color(0.4f, 0.4f, 0.45f, 1f),
            new Color(0.22f, 0.22f, 0.25f, 1f));
    }

    private TextButton.TextButtonStyle buildStyle(BitmapFont font, Color up, Color over, Color down) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = drawable(up);
        style.over = drawable(over);
        style.down = drawable(down);
        style.fontColor = Color.WHITE;
        return style;
    }

    private TextureRegionDrawable drawable(Color color) {
        Texture texture = TextureFactory.createSolidTexture(color);
        textures.add(texture);
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    @Override
    public void dispose() {
        for (Texture texture : textures) {
            texture.dispose();
        }
        textures.clear();
    }
}
