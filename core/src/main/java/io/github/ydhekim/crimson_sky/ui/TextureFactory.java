package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/**
 * Factory for creating textures used in UI and rendering.
 * Applies Factory Pattern to decouple texture creation from UI code.
 * Centralizes pixmap-> texture conversion and disposal management.
 */
public class TextureFactory {

    /**
     * Creates a solid-colored texture with the specified dimensions and color.
     *
     * @param width      texture width
     * @param height     texture height
     * @param color      solid color to fill
     * @return           Texture object (caller is responsible for disposal)
     */
    public static Texture createSolidTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    /**
     * Creates a solid 1x1 texture with the specified color.
     * Useful for rendering scalable solid-color backgrounds.
     *
     * @param color color to fill
     * @return      Texture object (caller is responsible for disposal)
     */
    public static Texture createSolidTexture(Color color) {
        return createSolidTexture(1, 1, color);
    }

    /**
     * Creates a panel background texture (semi-transparent dark overlay).
     *
     * @return Texture object (caller is responsible for disposal)
     */
    public static Texture createPanelBackgroundTexture() {
        return createSolidTexture(1, 1, new Color(0.15f, 0.15f, 0.15f, 0.85f));
    }

    /**
     * Creates a placeholder avatar texture (solid dark gray).
     *
     * @param size dimension (width and height)
     * @return     Texture object (caller is responsible for disposal)
     */
    public static Texture createPlaceholderAvatarTexture(int size) {
        return createSolidTexture(size, size, Color.DARK_GRAY);
    }

    /**
     * Creates a row background texture (darker than panel background).
     *
     * @return Texture object (caller is responsible for disposal)
     */
    public static Texture createRowBackgroundTexture() {
        return createSolidTexture(1, 1, new Color(0.2f, 0.2f, 0.2f, 0.8f));
    }
}

