package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
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
import io.github.ydhekim.crimson_sky.ui.UiMetrics;
import io.github.ydhekim.crimson_sky.ui.UiPalette;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AchievementsScreen extends BaseScreen implements NetworkListener {

    private final List<Disposable> disposables;
    private VisTable scrollTable;
    private VisLabel unlockedCountLabel;
    private TextureRegionDrawable rowBgUnlockedDrawable;
    private TextureRegionDrawable rowBgLockedDrawable;
    private TextureRegionDrawable xpBadgeUnlockedDrawable;
    private TextureRegionDrawable xpBadgeLockedDrawable;
    private TextureRegionDrawable dividerDrawable;
    private TextureRegion placeholderIconRegion;

    public AchievementsScreen(CrimsonSky game) {
        super(game);
        this.disposables = new ArrayList<>();

        // Register this screen as the network listener before firing the request.
        game.getNetworkClient().setListener(this);

        initializeTextures();   // one-time only — never called again (see the method's contract)
        setupUIShell();         // safe to re-run — reads the drawables built above, builds no textures itself
        fetchAchievements();
    }

    private void fetchAchievements() {
        game.getNetworkClient().sendTCP(new AchievementListRequest());
    }

    /**
     * Rebuilds every localized part of the screen (header, count, Back button, rows) with the now-current
     * translations map. {@link BaseScreen#onLocalizationResponse} calls this on each successful
     * {@code LocalizationResponse}. Re-fetching is deliberate: {@link #populateAchievements} resolves each
     * row's title/description via {@code getLanguageManager().get(...)} at render time, so a fresh render
     * pass — not different server data — is what re-localizes the rows. Textures are untouched here; only
     * {@link #initializeTextures()} (constructor-time) ever creates them, so toggling language can't leak.
     */
    @Override
    public void refreshUI() {
        setupUIShell();
        fetchAchievements();
    }

    /**
     * Creates this screen's solid-color placeholder textures/drawables once, from the constructor only —
     * never from {@link #setupUIShell()}, which {@link #refreshUI()} re-runs on every language change.
     * Rebuilding these on each refresh (without disposing the old ones) is the texture-leak class already
     * fixed for {@code ConnectionScreen}'s Retry button; keeping them here keeps the shell re-runnable.
     */
    private void initializeTextures() {
        // Unlocked rows read slightly brighter than locked; the XP badge picks up a gold tint when earned.
        Texture rowBgUnlockedTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.05f));
        disposables.add(rowBgUnlockedTexture);
        Texture rowBgLockedTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.025f));
        disposables.add(rowBgLockedTexture);
        rowBgUnlockedDrawable = new TextureRegionDrawable(new TextureRegion(rowBgUnlockedTexture));
        rowBgLockedDrawable = new TextureRegionDrawable(new TextureRegion(rowBgLockedTexture));

        Texture xpBadgeUnlockedTexture = TextureFactory.createSolidTexture(1, 1,
            new Color(UiPalette.ACCENT_GOLD.r, UiPalette.ACCENT_GOLD.g, UiPalette.ACCENT_GOLD.b, 0.15f));
        disposables.add(xpBadgeUnlockedTexture);
        Texture xpBadgeLockedTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.05f));
        disposables.add(xpBadgeLockedTexture);
        xpBadgeUnlockedDrawable = new TextureRegionDrawable(new TextureRegion(xpBadgeUnlockedTexture));
        xpBadgeLockedDrawable = new TextureRegionDrawable(new TextureRegion(xpBadgeLockedTexture));

        Texture dividerTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.15f));
        disposables.add(dividerTexture);
        dividerDrawable = new TextureRegionDrawable(new TextureRegion(dividerTexture));

        // Placeholder achievement icon — no atlas is shipped (M4 foundation cleanup); real per-achievement
        // icon art is Epic E content work. One solid-color icon is shared across every row and tinted
        // per-row (crimson when unlocked, muted when locked) rather than swapping textures.
        Texture placeholderIconTexture = TextureFactory.createPlaceholderIconTexture(64);
        disposables.add(placeholderIconTexture);
        placeholderIconRegion = new TextureRegion(placeholderIconTexture);
    }

    /**
     * Builds the UI shell (header, count, scroll pane, footer) from the current language. Re-run by
     * {@link #refreshUI()}, so it clears the stage first and touches no {@code Texture} fields — those are
     * populated once by {@link #initializeTextures()} before this ever runs.
     */
    private void setupUIShell() {
        stage.clear();

        VisTable mainPanel = createMainContentPanel();

        VisLabel headerLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_ACHIEVEMENTS"));
        headerLabel.setFontScale(2f);
        headerLabel.setColor(UiPalette.ACCENT_CRIMSON);
        headerLabel.setAlignment(Align.center);
        mainPanel.add(headerLabel).padBottom(4).center().row();

        unlockedCountLabel = new VisLabel("");
        unlockedCountLabel.setColor(UiPalette.TEXT_MUTED);
        unlockedCountLabel.setAlignment(Align.center);
        mainPanel.add(unlockedCountLabel).padBottom(20).center().row();

        scrollTable = new VisTable();
        scrollTable.top();

        VisScrollPane scrollPane = new VisScrollPane(scrollTable);
        scrollPane.setOverscroll(false, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        mainPanel.add(scrollPane).expand().fill().padBottom(20).row();

        // Footer & Back Button using UIButtonBuilder
        VisTable footerTable = new VisTable();
        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_BACK"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(() -> game.getScreenRouter().navigateTo(ScreenType.MAIN_MENU))
            .buildAndAddTo(footerTable);
        footerTable.add().expandX();
        mainPanel.add(footerTable).expandX().fillX();
    }

    /**
     * Populates the list dynamically once achievement data arrives from the server. Unlocked achievements
     * sort to the top (newest first); locked ones follow below a divider in server order (stable ascending
     * id per the DAO's ORDER BY).
     */
    private void populateAchievements(List<AccountAchievement> achievements) {
        scrollTable.clearChildren(); // Clear any previously rendered rows.

        if (achievements == null || achievements.isEmpty()) {
            unlockedCountLabel.setText("");
            scrollTable.add(new VisLabel(game.getLanguageManager().get("UI_MSG_NO_ACHIEVEMENTS"))).expand().center();
            return;
        }

        int unlockedCount = (int) achievements.stream().filter(AccountAchievement::isUnlocked).count();
        unlockedCountLabel.setText(String.format(
            game.getLanguageManager().get("UI_LBL_ACHIEVEMENTS_UNLOCKED_COUNT"), unlockedCount, achievements.size()));

        List<AccountAchievement> unlocked = new ArrayList<>();
        List<AccountAchievement> locked = new ArrayList<>();
        for (AccountAchievement ach : achievements) {
            (ach.isUnlocked() ? unlocked : locked).add(ach);
        }
        unlocked.sort(Comparator.comparing(AccountAchievement::unlockedAt).reversed());

        for (AccountAchievement ach : unlocked) {
            String translatedTitle = game.getLanguageManager().get(ach.titleLocKey());
            String translatedDesc = game.getLanguageManager().get(ach.descLocKey());
            scrollTable.add(createAchievementRow(placeholderIconRegion, ach, translatedTitle, translatedDesc))
                .growX().padBottom(5).row();
        }

        if (!unlocked.isEmpty() && !locked.isEmpty()) {
            Image divider = new Image(dividerDrawable);
            scrollTable.add(divider).growX().height(1).padTop(4).padBottom(9).row();
        }

        for (AccountAchievement ach : locked) {
            String translatedTitle = game.getLanguageManager().get(ach.titleLocKey());
            String translatedDesc = game.getLanguageManager().get(ach.descLocKey());
            scrollTable.add(createAchievementRow(placeholderIconRegion, ach, translatedTitle, translatedDesc))
                .growX().padBottom(5).row();
        }
    }

    private VisTable createAchievementRow(TextureRegion icon, AccountAchievement ach, String title, String description) {
        VisTable rowTable = new VisTable();
        rowTable.setBackground(ach.isUnlocked() ? rowBgUnlockedDrawable : rowBgLockedDrawable);
        rowTable.pad(10);

        Image iconImage = new Image(icon);
        iconImage.setColor(ach.isUnlocked() ? UiPalette.ACCENT_CRIMSON : new Color(0.29f, 0.28f, 0.25f, 1f));
        rowTable.add(iconImage).size(64, 64).padRight(15).align(Align.left);

        VisTable textTable = new VisTable();
        VisLabel titleLabel = new VisLabel(title);
        titleLabel.setColor(ach.isUnlocked() ? Color.WHITE : Color.GRAY);
        titleLabel.setAlignment(Align.left);
        textTable.add(titleLabel).growX().row();

        VisLabel descriptionLabel = new VisLabel(description);
        descriptionLabel.setWrap(true);
        descriptionLabel.setColor(ach.isUnlocked() ? UiPalette.TEXT_MUTED : Color.DARK_GRAY);
        descriptionLabel.setAlignment(Align.topLeft);
        textTable.add(descriptionLabel).growX().row();

        rowTable.add(textTable).expandX().fillX().align(Align.top);

        if (ach.isUnlocked()) {
            VisLabel timeLabel = new VisLabel(formatRelativeTime(ach.unlockedAt()));
            timeLabel.setColor(UiPalette.TEXT_MUTED);
            timeLabel.setFontScale(0.85f);
            rowTable.add(timeLabel).padRight(10).align(Align.right);
        }

        VisLabel xpLabel = new VisLabel("+" + ach.xpReward() + " XP");
        xpLabel.setColor(ach.isUnlocked() ? UiPalette.ACCENT_GOLD : UiPalette.TEXT_MUTED);
        xpLabel.setFontScale(0.85f);
        VisTable xpBadge = new VisTable();
        xpBadge.setBackground(ach.isUnlocked() ? xpBadgeUnlockedDrawable : xpBadgeLockedDrawable);
        xpBadge.pad(4, 10, 4, 10);
        xpBadge.add(xpLabel);
        rowTable.add(xpBadge).align(Align.right);

        return rowTable;
    }

    /**
     * Cosmetic "earned N ago" label. {@code unlocked_at} is a Postgres {@code TIMESTAMP} (no timezone) read
     * via {@code ResultSet.getString()}, so its wire format is space-separated ("2026-07-21 18:22:51.398"),
     * not ISO's {@code T}-separated form — normalize before parsing. Compares against the client's local
     * clock, not a synced server time; acceptable for a cosmetic label at this stage.
     */
    private String formatRelativeTime(String unlockedAt) {
        if (unlockedAt == null) return "";
        try {
            LocalDateTime then = LocalDateTime.parse(unlockedAt.replace(' ', 'T'));
            Duration elapsed = Duration.between(then, LocalDateTime.now());
            long minutes = elapsed.toMinutes();
            if (minutes < 1) return "just now";
            if (minutes < 60) return minutes + "m ago";
            long hours = elapsed.toHours();
            if (hours < 24) return hours + "h ago";
            long days = elapsed.toDays();
            if (days == 1) return "Yesterday";
            if (days < 30) return days + "d ago";
            return unlockedAt.substring(0, 10);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onAchievementListResponse(AchievementListResponse response) {
        // Hop from the network thread onto the LibGDX render thread before touching UI state.
        Gdx.app.postRunnable(() -> {
            if (response.success() && response.achievements() != null) {
                populateAchievements(response.achievements());
            } else {
                scrollTable.clearChildren();
                scrollTable.add(new VisLabel(game.getLanguageManager().get("UI_MSG_ACHIEVEMENTS_LOAD_ERROR")))
                    .expand().center();
            }
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        for (Disposable disposable : disposables) {
            if (disposable != null) disposable.dispose();
        }
        disposables.clear();

        // Clear the listener to avoid a memory leak.
        game.getNetworkClient().setListener(null);
    }
}
