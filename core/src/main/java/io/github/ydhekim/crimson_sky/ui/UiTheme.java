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

    /**
     * The primary-CTA style: crimson from {@link UiPalette} rather than inline colors, so any future
     * "this is the main action" button (an eventual Attack/Play) reaches for this one shared accent
     * instead of each screen inventing its own.
     */
    public TextButton.TextButtonStyle accentButtonStyle(BitmapFont font) {
        return buildStyle(font, UiPalette.ACCENT_CRIMSON, UiPalette.ACCENT_CRIMSON_HOVER,
            UiPalette.ACCENT_CRIMSON_PRESSED);
    }

    /**
     * A toggle style whose {@code checked} state is visually distinct — for option rows (gender, hair
     * type) where exactly one choice is selected at a time via a
     * {@link com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup}. Unlike standardButtonStyle/accentButtonStyle,
     * this one defines {@code checked}/{@code checkedOver}, since neither of those is ever set otherwise
     * ({@code Button.ButtonStyle} silently falls back to {@code up} when unset, which is why toggling
     * {@code setChecked()} on any existing style produces no visible change).
     * <p>
     * Gold rather than crimson: this is the generic "this is the current choice" indicator, independent
     * of faction — crimson stays reserved for the Wardens on the faction cards.
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
