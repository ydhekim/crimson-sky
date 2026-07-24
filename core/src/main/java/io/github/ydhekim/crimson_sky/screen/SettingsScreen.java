package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveAccountSettingsRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveAccountSettingsResponse;
import io.github.ydhekim.crimson_sky.screen.factory.ScreenRouter;
import io.github.ydhekim.crimson_sky.ui.DisplaySettings;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;
import io.github.ydhekim.crimson_sky.ui.UiMetrics;
import io.github.ydhekim.crimson_sky.ui.UiPalette;

import java.util.HashMap;
import java.util.Map;

public class SettingsScreen extends BaseScreen {

    private final ScreenRouter screenRouter;
    private VisSelectBox<String> languageSelectBox;
    private VisSelectBox<String> resolutionSelectBox;
    private VisSlider volumeSlider;
    private VisCheckBox fullscreenCheckBox;
    private final Map<String, String> languageMap = new HashMap<>();
    private String initialLangCode;

    public SettingsScreen(CrimsonSky game) {
        super(game);
        this.screenRouter = game.getScreenRouter();
        setupLanguageMap();
        setupUI();
    }

    private void setupLanguageMap() {
        languageMap.put("English", "en_US");
        languageMap.put("Türkçe", "tr_TR");
    }

    private void setupUI() {
        stage.clear();
        if (backgroundImage != null) {
            stage.addActor(backgroundImage);
        }

        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_SETTINGS"));
        titleLabel.setFontScale(2f);
        titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
        mainPanel.add(titleLabel).padBottom(20).colspan(2).row();

        VisTable contentTable = new VisTable();

        VisLabel volumeLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_VOLUME"));
        volumeSlider = new VisSlider(0f, 1f, 0.05f, false);
        volumeSlider.setValue((float) game.getAccountSettings().volumeMaster());
        contentTable.add(volumeLabel).padRight(20).padBottom(20).left();
        contentTable.add(volumeSlider).width(200).padBottom(20).row();

        VisLabel languageLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_LANGUAGE"));
        languageSelectBox = new VisSelectBox<>();

        Array<String> languages = new Array<>();
        languageMap.keySet().forEach(languages::add);
        languageSelectBox.setItems(languages);

        // Set initial selection
        initialLangCode = game.getLanguageManager().getCurrentLang();
        languageMap.forEach((name, code) -> {
            if (code.equals(initialLangCode)) {
                languageSelectBox.setSelected(name);
            }
        });
        contentTable.add(languageLabel).padRight(20).padBottom(20).left();
        contentTable.add(languageSelectBox).width(200).padBottom(20).row();

        VisLabel resolutionLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_RESOLUTION"));
        resolutionSelectBox = new VisSelectBox<>();
        resolutionSelectBox.setItems("1280x720", "1600x900", "1920x1080");
        resolutionSelectBox.setSelected(game.getAccountSettings().resolution());

        // Applies the selected resolution immediately when it changes.
        resolutionSelectBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applyResolution(resolutionSelectBox.getSelected(), fullscreenCheckBox.isChecked());
            }
        });

        contentTable.add(resolutionLabel).padRight(20).padBottom(20).left();
        contentTable.add(resolutionSelectBox).width(200).padBottom(20).row();

        VisLabel fullscreenLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_FULLSCREEN"));
        fullscreenCheckBox = new VisCheckBox("");
        // The persisted preference is the label of record, not Gdx.graphics.isFullscreen() — the live
        // window and the saved value can legitimately disagree right after applyResolution runs async.
        fullscreenCheckBox.setChecked(game.getAccountSettings().fullscreen());

        // Toggling only the checkbox (without touching resolution) must still take effect immediately,
        // same as changing the resolution does.
        fullscreenCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applyResolution(resolutionSelectBox.getSelected(), fullscreenCheckBox.isChecked());
            }
        });

        contentTable.add(fullscreenLabel).padRight(20).padBottom(20).left();
        contentTable.add(fullscreenCheckBox).width(200).padBottom(20).row();

        mainPanel.add(contentTable).expand().fill().padBottom(40).row();

        // Footer Actions
        VisTable footerTable = new VisTable();
        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_BACK"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::navigateBack)
            .buildAndAddTo(footerTable);

        footerTable.add().expandX();

        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_SAVE"))
            .withStyle(accentButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::saveSettings)
            .buildAndAddTo(footerTable);

        mainPanel.add(footerTable).expandX().fillX();
    }

    private void applyResolution(String resolutionStr, boolean isFullscreen) {
        DisplaySettings.apply(resolutionStr, isFullscreen);
    }

    private void saveSettings() {
        String selectedLanguageName = languageSelectBox.getSelected();
        String selectedLangCode = languageMap.get(selectedLanguageName);
        String selectedResolution = resolutionSelectBox.getSelected();

        AccountSettings accountSettings = new AccountSettings(
            volumeSlider.getValue(),
            selectedLangCode,
            fullscreenCheckBox.isChecked(),
            selectedResolution);

        game.getLanguageManager().setCurrentLang(selectedLangCode);
        // Keep the client-side cache (K4) in sync with what was just saved, so a refreshUI() rebuild
        // reads the new values instead of snapping back to the login-time snapshot. Optimistic — matches
        // setCurrentLang above; if the save later fails, cache and DB disagree until next login (same
        // known gap language already has; a rollback path is out of scope for this fix).
        game.setAccountSettings(accountSettings);
        game.getNetworkClient().sendTCP(new SaveAccountSettingsRequest(accountSettings));
    }

    @Override
    public void onSaveAccountSettingsResponse(SaveAccountSettingsResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success()) {
                game.getNetworkClient().sendTCP(new LocalizationRequest(game.getLanguageManager().getCurrentLang()));
            }
        });

    }

    private void navigateBack() {
        screenRouter.navigateTo(ScreenType.MAIN_MENU);
    }

    @Override
    public void refreshUI() {
        Gdx.app.log("SettingsScreen", "Refreshing UI after localization update.");
        setupUI();
    }
}
