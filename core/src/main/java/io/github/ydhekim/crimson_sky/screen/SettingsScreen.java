package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveAccountSettingsRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveAccountSettingsResponse;
import io.github.ydhekim.crimson_sky.screen.factory.ScreenRouter;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;

import java.util.HashMap;
import java.util.Map;

public class SettingsScreen extends BaseScreen {

    private final ScreenRouter screenRouter;
    private VisSelectBox<String> languageSelectBox;
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
        mainPanel.add(titleLabel).padBottom(20).colspan(2).row();

        VisTable contentTable = new VisTable();

        VisLabel volumeLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_VOLUME"));
        volumeSlider = new VisSlider(0f, 1f, 0.05f, false);
        volumeSlider.setValue(0.8f);
        contentTable.add(volumeLabel);
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
        contentTable.add(languageLabel).padRight(20);
        contentTable.add(languageSelectBox).width(200).padBottom(20).row();

        VisLabel fullscreenLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_FULLSCREEN"));
        fullscreenCheckBox = new VisCheckBox("");
        fullscreenCheckBox.setChecked(Gdx.graphics.isFullscreen());
        contentTable.add(fullscreenLabel);
        contentTable.add(fullscreenCheckBox).width(200).padBottom(20).row();

        mainPanel.add(contentTable).expand().fill().padBottom(40).row();

        // Footer Actions
        VisTable footerTable = new VisTable();
        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_BACK"))
            .withStyle(customButtonStyle)
            .withSize(200, 40)
            .withAction(this::navigateBack)
            .buildAndAddTo(footerTable);

        footerTable.add().expandX();

        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_SAVE"))
            .withStyle(customButtonStyle)
            .withSize(200, 40)
            .withAction(this::saveSettings)
            .buildAndAddTo(footerTable);

        mainPanel.add(footerTable).expandX().fillX();
    }

    private void saveSettings() {
        String selectedLanguageName = languageSelectBox.getSelected();
        String selectedLangCode = languageMap.get(selectedLanguageName);

        AccountSettings accountSettings = new AccountSettings(
            volumeSlider.getValue(),
            selectedLangCode,
            fullscreenCheckBox.isChecked());

        game.getLanguageManager().setCurrentLang(selectedLangCode);
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
