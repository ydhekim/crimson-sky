package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;

public class MainMenuScreen extends BaseScreen {

    public MainMenuScreen(final CrimsonSky game) {
        super(game);
        setupUI();
    }

    private void setupUI() {
        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel("MAIN MENU");
        titleLabel.setFontScale(2f);
        mainPanel.add(titleLabel).padBottom(20).row();

        Table buttonTable = new Table();

        TextButton charactersButton = new TextButton("Characters", customButtonStyle);
        charactersButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new CharactersScreen(game));
            }
        });

        TextButton achievementsButton = new TextButton("Achievements", customButtonStyle);
        achievementsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // TODO: Navigate to Achievements Screen
            }
        });

        TextButton settingsButton = new TextButton("Settings", customButtonStyle);
        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // TODO: Navigate to Settings Screen
            }
        });

        TextButton exitButton = new TextButton("Exit", customButtonStyle);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        buttonTable.add(charactersButton).width(200).height(40).padBottom(15).row();
        buttonTable.add(achievementsButton).width(200).height(40).padBottom(15).row();
        buttonTable.add(settingsButton).width(200).height(40).padBottom(15).row();
        buttonTable.add(exitButton).width(200).height(40).padBottom(15).row();

        mainPanel.add(buttonTable);
    }
}
