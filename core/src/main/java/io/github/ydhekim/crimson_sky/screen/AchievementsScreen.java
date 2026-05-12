package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;

import java.util.ArrayList;
import java.util.List;

public class AchievementsScreen extends BaseScreen {

    private List<Disposable> disposables;

    public AchievementsScreen(CrimsonSky game) {
        super(game);
        disposables = new ArrayList<>();
        setupUI();
    }

    private void setupUI() {
        VisTable mainPanel = createMainContentPanel();
        VisLabel headerLabel = new VisLabel("Achievements");
        headerLabel.setFontScale(1.5f);
        mainPanel.add(headerLabel).pad(20).row();
        VisTable scrollTable = new VisTable();
        scrollTable.top();
        Pixmap rowBgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        rowBgPixmap.setColor(new Color(0.15f, 0.15f, 0.15f, 0.7f));
        rowBgPixmap.fill();
        Texture rowBgTexture = new Texture(rowBgPixmap);
        rowBgPixmap.dispose();
        disposables.add(rowBgTexture);
        TextureRegionDrawable rowBgDrawable = new TextureRegionDrawable(new TextureRegion(rowBgTexture));

        for (int i = 1; i <= 20; i++) {
            Texture icon = createPlaceholderTexture(getRandomColor());
            disposables.add(icon);
            scrollTable.add(createAchievementRow(icon, "Achievement " + i + ": Title", "Description for achievement number " + i + ". This text should wrap nicely within the allocated space.", rowBgDrawable))
                .growX().padBottom(5).row();
        }

        // Create VisScrollPane
        VisScrollPane scrollPane = new VisScrollPane(scrollTable);
        scrollPane.setOverscroll(false, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        mainPanel.add(scrollPane).expand().fill().padBottom(20).row();

        VisTable footerTable = new VisTable();
        TextButton backButton = new TextButton("Back", customButtonStyle);

        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game));
            }
        });
        footerTable.add(backButton).width(200).height(40).left();
        footerTable.add().expandX();
        mainPanel.add(footerTable).expandX().fillX();
    }

    private VisTable createAchievementRow(Texture icon, String title, String description, TextureRegionDrawable background) {
        VisTable rowTable = new VisTable();
        rowTable.setBackground(background);
        rowTable.pad(10); // Padding inside the row
        // rowTable.debugAll(); // Uncomment to see layout debug lines

        // Icon
        Image iconImage = new Image(icon);
        rowTable.add(iconImage).size(64, 64).padRight(15).align(Align.left);

        // Title and Description
        VisTable textTable = new VisTable();
        // textTable.debugAll(); // Uncomment to see layout debug lines
        VisLabel titleLabel = new VisLabel(title); // Use default style
        titleLabel.setAlignment(Align.left);
        textTable.add(titleLabel).growX().row();

        VisLabel descriptionLabel = new VisLabel(description); // Use default style
        descriptionLabel.setWrap(true);
        descriptionLabel.setAlignment(Align.topLeft);
        textTable.add(descriptionLabel).growX().row();

        rowTable.add(textTable).expandX().fillX().align(Align.top);

        return rowTable;
    }

    private Texture createPlaceholderTexture(Color color) {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private Color getRandomColor() {
        // Generate a random color for placeholder icons
        return new Color((float) Math.random(), (float) Math.random(), (float) Math.random(), 1f);
    }

    // BaseScreen already handles render, resize, hide, pause, resume.
    // Only override dispose to clean up custom disposables.
    @Override
    public void dispose() {
        super.dispose(); // Call BaseScreen's dispose method

        for (Disposable disposable : disposables) {
            disposable.dispose();
        }
        disposables.clear(); // Clear the list after disposing
    }
}
