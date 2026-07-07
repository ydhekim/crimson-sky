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
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;

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

        VisLabel resolutionLabel = new VisLabel("Resolution:"); // İleride yerelleştirme anahtarı bağlanabilir
        resolutionSelectBox = new VisSelectBox<>();
        resolutionSelectBox.setItems("1280x720", "1600x900", "1920x1080");

        // Buraya ağ paketinden veya yerel ayarlardan gelen güncel çözünürlük verisini bağlayabilirsiniz.
        // Örnek varsayılan kilit:
        resolutionSelectBox.setSelected("1280x720");

        // Çözünürlük değiştiğinde anında pencereyi boyutlandırmasını sağlayan dinleyici
        resolutionSelectBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applyResolution(resolutionSelectBox.getSelected(), fullscreenCheckBox.isChecked());
            }
        });

        contentTable.add(resolutionLabel).padRight(20).left();
        contentTable.add(resolutionSelectBox).width(200).padBottom(20).row();

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

    private void applyResolution(String resolutionStr, boolean isFullscreen) {
        if (resolutionStr == null || !resolutionStr.contains("x")) return;

        try {
            String[] parts = resolutionStr.split("x");
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);

            if (isFullscreen) {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            } else {
                Gdx.graphics.setWindowedMode(width, height);
            }
            Gdx.app.log("SettingsScreen", "Pencere boyutu güncellendi: " + resolutionStr + " (Fullscreen: " + isFullscreen + ")");
        } catch (Exception e) {
            Gdx.app.error("SettingsScreen", "Çözünürlük uygulanırken hata oluştu", e);
        }
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
