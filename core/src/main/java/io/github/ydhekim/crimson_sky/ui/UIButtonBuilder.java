package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.ydhekim.crimson_sky.screen.action.ScreenAction;

/**
 * Builder for creating TextButtons with Command Pattern (ScreenAction).
 * Simplifies button creation and listener setup, reducing anonymous class boilerplate.
 * Applies Builder Pattern and Decorator Pattern for component composition.
 */
public class UIButtonBuilder {
    private String label;
    private TextButton.TextButtonStyle style;
    private ScreenAction action;
    private float width = 0f;
    private float height = 0f;

    public UIButtonBuilder(String label) {
        this.label = label;
    }

    /**
     * Sets the button style.
     */
    public UIButtonBuilder withStyle(TextButton.TextButtonStyle style) {
        this.style = style;
        return this;
    }

    /**
     * Sets the action (command) to execute on button click.
     */
    public UIButtonBuilder withAction(ScreenAction action) {
        this.action = action;
        return this;
    }

    /**
     * Sets the button dimensions.
     */
    public UIButtonBuilder withSize(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Builds and returns the configured TextButton.
     */
    public TextButton build() {
        if (style == null) {
            throw new IllegalStateException("TextButton.TextButtonStyle is required. Use withStyle().");
        }
        TextButton button = new TextButton(label, style);

        if (action != null) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    action.execute();
                }
            });
        }

        return button;
    }

    /**
     * Builds and adds the button to a table with configured dimensions.
     */
    public void buildAndAddTo(Table table) {
        TextButton button = build();
        if (width > 0 && height > 0) {
            table.add(button).width(width).height(height);
        } else {
            table.add(button);
        }
    }

    /**
     * Builds and adds the button to a table with dimensions and padding.
     */
    public void buildAndAddTo(Table table, float padBottom) {
        TextButton button = build();
        if (width > 0 && height > 0) {
            table.add(button).width(width).height(height).padBottom(padBottom);
        } else {
            table.add(button).padBottom(padBottom);
        }
    }
}


