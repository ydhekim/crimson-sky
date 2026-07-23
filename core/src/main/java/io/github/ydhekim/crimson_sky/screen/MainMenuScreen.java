package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.screen.factory.ScreenRouter;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;
import io.github.ydhekim.crimson_sky.ui.UiMetrics;
import io.github.ydhekim.crimson_sky.ui.UiPalette;

/**
 * Main menu screen with navigation options.
 * Refactored to use ScreenRouter for dependency-injected navigation.
 * Uses Command Pattern (ClickListener) for button actions.
 * Applies Single Responsibility Principle.
 */
public class MainMenuScreen extends BaseScreen {
    private final ScreenRouter screenRouter;

    public MainMenuScreen(final CrimsonSky game) {
        super(game);
        // Get the ScreenRouter from the game instance
        this.screenRouter = game.getScreenRouter();
//        setupUI();
    }

    /**
     * Sets up the main menu UI with navigation buttons using UIButtonBuilder.
     */
    private void setupUI() {
        stage.clear();
        if (backgroundImage != null) {
            stage.addActor(backgroundImage);
        }

        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel(game.getLanguageManager().get("UI_TITLE_MAIN_MENU"));
        titleLabel.setFontScale(2f);
        titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
        mainPanel.add(titleLabel).padBottom(20).row();

        Table buttonTable = new Table();

        // Build buttons using UIButtonBuilder + Command Pattern
        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_CHARACTERS"))
            .withStyle(accentButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::navigateToCharacters)
            .buildAndAddTo(buttonTable, 15);
        buttonTable.row();

        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_ACHIEVEMENTS"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::navigateToAchievements)
            .buildAndAddTo(buttonTable, 15);
        buttonTable.row();

        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_SETTINGS"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::navigateToSettings)
            .buildAndAddTo(buttonTable, 15);
        buttonTable.row();

        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_EXIT"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(Gdx.app::exit)
            .buildAndAddTo(buttonTable);

        mainPanel.add(buttonTable);
    }

    /**
     * Navigates to the characters screen via ScreenRouter.
     */
    private void navigateToCharacters() {
        Gdx.app.log("MainMenuScreen", "Navigating to Characters screen.");
        screenRouter.navigateTo(ScreenType.CHARACTERS);
    }

    /**
     * Navigates to the achievements screen via ScreenRouter.
     */
    private void navigateToAchievements() {
        Gdx.app.log("MainMenuScreen", "Navigating to Achievements screen.");
        screenRouter.navigateTo(ScreenType.ACHIEVEMENTS);
    }

    /**
     * Navigates to the settings screen via ScreenRouter.
     */
    private void navigateToSettings() {
        Gdx.app.log("MainMenuScreen", "Navigating to Settings screen.");
        screenRouter.navigateTo(ScreenType.SETTINGS);
    }

    @Override
    public void refreshUI() {
        Gdx.app.log("MainMenuScreen", "Refreshing UI with new localizations.");
        stage.clear();
        setupUI();
    }

    @Override
    public void show() {
        super.show();
        stage.clear();
        setupUI();
    }
}
