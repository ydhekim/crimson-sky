package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.AccountAchievement;
import io.github.ydhekim.crimson_sky.common.network.packet.AchievementListRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.AchievementListResponse;
import io.github.ydhekim.crimson_sky.network.NetworkListener;
import io.github.ydhekim.crimson_sky.ui.TextureFactory;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;

import java.util.ArrayList;
import java.util.List;

public class AchievementsScreen extends BaseScreen implements NetworkListener {

    private final List<Disposable> disposables;
    private VisTable scrollTable;
    private TextureRegionDrawable rowBgDrawable;

    public AchievementsScreen(CrimsonSky game) {
        super(game);
        this.disposables = new ArrayList<>();

        // 1. Ağ dinleyicisini bu ekrana ayarla ve istek at
        game.getNetworkClient().setListener(this);

        setupUIShell();
        fetchAchievements();
    }

    private void fetchAchievements() {
        game.getNetworkClient().sendTCP(new AchievementListRequest());
    }

    /**
     * Sets up the UI shell (Header, ScrollPane, Footer) using Builder patterns.
     */
    private void setupUIShell() {
        VisTable mainPanel = createMainContentPanel();

        VisLabel headerLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_ACHIEVEMENTS"));
        headerLabel.setFontScale(1.5f);
        mainPanel.add(headerLabel).pad(20).row();

        scrollTable = new VisTable();
        scrollTable.top();

        // Use TextureFactory for row background texture
        Texture rowBgTexture = TextureFactory.createSolidTexture(1, 1, new Color(0.15f, 0.15f, 0.15f, 0.7f));
        disposables.add(rowBgTexture);
        rowBgDrawable = new TextureRegionDrawable(new TextureRegion(rowBgTexture));

        VisScrollPane scrollPane = new VisScrollPane(scrollTable);
        scrollPane.setOverscroll(false, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        mainPanel.add(scrollPane).expand().fill().padBottom(20).row();

        // Footer & Back Button using UIButtonBuilder
        VisTable footerTable = new VisTable();
        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_BACK"))
            .withStyle(customButtonStyle)
            .withSize(200, 40)
            .withAction(() -> game.getScreenRouter().navigateTo(ScreenType.MAIN_MENU))
            .buildAndAddTo(footerTable);
        footerTable.add().expandX();
        mainPanel.add(footerTable).expandX().fillX();
    }

    /**
     * Sunucudan veri geldiğinde listeyi dinamik olarak doldurur
     */
    private void populateAchievements(List<AccountAchievement> achievements) {
        scrollTable.clearChildren(); // Eski veriler (varsa) temizlensin

        if (achievements == null || achievements.isEmpty()) {
            scrollTable.add(new VisLabel(game.getLanguageManager().get("UI_MSG_NO_ACHIEVEMENTS"))).expand().center();
            return;
        }

        for (AccountAchievement ach : achievements) {
            // AssetManager'dan iconId'ye göre texture çekilebilir, yoksa geçici renk üret
            Texture icon = createPlaceholderTexture(getFactionColor(ach.keyName()));
            disposables.add(icon);

            // Dil anahtarlarını tercüme ettiriyoruz
            String translatedTitle = game.getLanguageManager().get(ach.titleLocKey());
            String translatedDesc = game.getLanguageManager().get(ach.descLocKey());

            VisTable row = createAchievementRow(icon, translatedTitle, translatedDesc, rowBgDrawable, ach.isUnlocked());
            scrollTable.add(row).growX().padBottom(5).row();
        }
    }

    private VisTable createAchievementRow(Texture icon, String title, String description, TextureRegionDrawable background, boolean isUnlocked) {
        VisTable rowTable = new VisTable();
        rowTable.setBackground(background);
        rowTable.pad(10);

        // Icon İşlemleri
        Image iconImage = new Image(icon);
        if (!isUnlocked) {
            // UX Dokunuşu: Eğer başarım kilitliyse simgeyi yarı şeffaf ve gri yapıyoruz!
            iconImage.setColor(new Color(0.3f, 0.3f, 0.3f, 0.5f));
        }
        rowTable.add(iconImage).size(64, 64).padRight(15).align(Align.left);

        // Metin Alanları
        VisTable textTable = new VisTable();
        VisLabel titleLabel = new VisLabel(title);
        if (!isUnlocked) titleLabel.setColor(Color.GRAY); // Kilitliyse başlık gri olsun
        titleLabel.setAlignment(Align.left);
        textTable.add(titleLabel).growX().row();

        VisLabel descriptionLabel = new VisLabel(description);
        descriptionLabel.setWrap(true);
        descriptionLabel.setColor(isUnlocked ? Color.LIGHT_GRAY : Color.DARK_GRAY);
        descriptionLabel.setAlignment(Align.topLeft);
        textTable.add(descriptionLabel).growX().row();

        rowTable.add(textTable).expandX().fillX().align(Align.top);
        return rowTable;
    }

    @Override
    public void onAchievementListResponse(AchievementListResponse response) {
        // Ağ thread'inden LibGDX ana render thread'ine güvenli geçiş
        Gdx.app.postRunnable(() -> {
            if (response.success() && response.achievements() != null) {
                populateAchievements(response.achievements());
            } else {
                scrollTable.clearChildren();
                scrollTable.add(new VisLabel("Error loading achievements.")).expand().center();
            }
        });
    }

    /**
     * Creates achievement placeholder texture using TextureFactory.
     */
    private Texture createPlaceholderTexture(Color color) {
        return TextureFactory.createSolidTexture(64, 64, color);
    }

    private Color getFactionColor(String keyName) {
        // Tamamen rastgele yerine başarımın adına göre sabit bir mock renk üretelim ki kırpışma yapmasın
        int hash = keyName.hashCode();
        float r = Math.abs((hash & 0xFF0000) >> 16) / 255f;
        float g = Math.abs((hash & 0x00FF00) >> 8) / 255f;
        float b = Math.abs(hash & 0x0000FF) / 255f;
        return new Color(r, g, b, 1f);
    }

    @Override
    public void dispose() {
        super.dispose();
        for (Disposable disposable : disposables) {
            if (disposable != null) disposable.dispose();
        }
        disposables.clear();

        // Dinleyiciyi temizleyerek hafıza sızıntısını (memory leak) engelliyoruz
        game.getNetworkClient().setListener(null);
    }
}
