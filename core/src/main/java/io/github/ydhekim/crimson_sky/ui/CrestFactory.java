package io.github.ydhekim.crimson_sky.ui;

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
 * rather than duplicated. Callers own and dispose the two textures this creates images from.
 * <p>
 * The inner square is wrapped in a {@link Container} because {@link Stack} otherwise stretches every
 * child to the full cell — the container is what keeps the outer "border" visible.
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
