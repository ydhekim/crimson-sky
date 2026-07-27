package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.network.packet.*;
import io.github.ydhekim.crimson_sky.network.NetworkListener;
import io.github.ydhekim.crimson_sky.screen.factory.ScreenRouter;
import io.github.ydhekim.crimson_sky.ui.CharacterRowBuilder;
import io.github.ydhekim.crimson_sky.ui.TextureFactory;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;
import io.github.ydhekim.crimson_sky.ui.UiMetrics;
import io.github.ydhekim.crimson_sky.ui.UiPalette;

public class CharactersScreen extends BaseScreen implements NetworkListener {

    private Table charactersListContainer;
    private VisScrollPane scrollPane;
    private Array<Character> characters;
    private int maxCharacterSlots = 3;

    private VisTable footerTable;
    private VisLabel slotsCountLabel;
    private TextButton createCharacterButton;
    private Texture placeholderAvatarTexture;
    private Texture rowBackgroundTexture;
    private final ScreenRouter screenRouter;

    public CharactersScreen(final CrimsonSky game) {
        super(game);
        this.screenRouter = game.getScreenRouter();
        characters = new Array<>();

        // Use TextureFactory for texture creation (SRP: factory handles pixmap->texture conversion)
        placeholderAvatarTexture = TextureFactory.createPlaceholderAvatarTexture(64);
        rowBackgroundTexture = TextureFactory.createRowBackgroundTexture();

        setupUI();
        game.getNetworkClient().setListener(this);
        fetchCharacters();
    }

    private void setupUI() {
        // Re-runnable: refreshUI() calls this again on each language change, so clear the stage first
        // (mirrors AchievementsScreen) — without it every refresh would stack another panel. Textures
        // are constructor-only, so nothing is leaked by rebuilding the actor tree here.
        stage.clear();

        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_CHARACTER_SELECTION"), "title");
        titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
        titleLabel.setAlignment(Align.center);
        mainPanel.add(titleLabel).padBottom(4).center().row();

        slotsCountLabel = new VisLabel("");
        slotsCountLabel.setColor(UiPalette.TEXT_MUTED);
        slotsCountLabel.setAlignment(Align.center);
        mainPanel.add(slotsCountLabel).padBottom(20).center().row();

        charactersListContainer = new Table();
        charactersListContainer.top();

        scrollPane = new VisScrollPane(charactersListContainer);
        scrollPane.setOverscroll(false, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        mainPanel.add(scrollPane).expand().fill().padBottom(20).row();

        footerTable = new VisTable();
        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_BACK"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(() -> screenRouter.navigateTo(ScreenType.MAIN_MENU))
            .buildAndAddTo(footerTable);

        footerTable.add().expandX();

        createCharacterButton = new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_NEW_CHARACTER"))
            .withStyle(accentButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::navigateToCharacterCreation)
            .build();
        footerTable.add(createCharacterButton).width(UiMetrics.NAV_BUTTON_WIDTH).height(UiMetrics.NAV_BUTTON_HEIGHT).right();

        mainPanel.add(footerTable).expandX().fillX();

        updateList(characters);
    }

    /**
     * Navigates to character creation screen using ScreenRouter.
     */
    private void navigateToCharacterCreation() {
        if (!createCharacterButton.isDisabled()) {
            screenRouter.navigateTo(ScreenType.CHARACTER_CREATION);
        }
    }

    private void fetchCharacters() {
        game.getNetworkClient().sendTCP(new CharacterListRequest());
    }

    private void updateList(Array<Character> newCharacters) {
        this.characters = newCharacters;
        charactersListContainer.clearChildren();

        slotsCountLabel.setText(String.format(
            game.getLanguageManager().get("UI_LBL_CHARACTER_SLOTS_COUNT"), characters.size, maxCharacterSlots));

        if (characters.isEmpty()) {
            charactersListContainer.add(new VisLabel(game.getLanguageManager().get("UI_MSG_NO_CHARACTERS"))).expand().center();
        } else {
            for (Character character : characters) {
                Table rowTable = createCharacterRow(character);
                charactersListContainer.add(rowTable).growX().padBottom(10).row();
            }
        }

        boolean canCreate = characters.size < maxCharacterSlots;
        createCharacterButton.setDisabled(!canCreate);
        createCharacterButton.setText(canCreate
            ? game.getLanguageManager().get("UI_BTN_NEW_CHARACTER")
            : game.getLanguageManager().get("UI_LBL_SLOTS_FULL"));
    }

    /**
     * Creates character row using CharacterRowBuilder (Builder + Command Pattern).
     */
    private Table createCharacterRow(final Character character) {
        return new CharacterRowBuilder(game, character)
            .withAvatarTexture(placeholderAvatarTexture)
            .withRowBackgroundTexture(rowBackgroundTexture)
            .withButtonStyles(accentButtonStyle, squareButtonStyle)
            .onPlay(() -> startGame(character))
            .onDelete(() -> confirmDeleteCharacter(character))
            .build();
    }

    /**
     * Shows confirmation dialog for character deletion using UIButtonBuilder.
     */
    private void confirmDeleteCharacter(final Character character) {
        VisDialog dialog = new VisDialog(game.getLanguageManager().get("UI_LBL_DELETE_CHARACTER_TITLE"));
        dialog.text(String.format(game.getLanguageManager().get("UI_MSG_DELETE_CHARACTER_CONFIRM"), character.name()));

        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_YES"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
            .withAction(() -> {
                dialog.hide();
                game.getNetworkClient().sendTCP(new DeleteCharacterRequest(character.name()));
            })
            .buildAndAddTo(dialog.getButtonsTable());
        dialog.getButtonsTable().add().expandX();

        new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_NO"))
            .withStyle(customButtonStyle)
            .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
            .withAction(dialog::hide)
            .buildAndAddTo(dialog.getButtonsTable());

        dialog.show(stage);
    }

    private void startGame(Character character) {
        System.out.println("Starting game with " + character.name() + "...");
    }

    @Override
    public void onCharacterListResponse(CharacterListResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success()) {
                this.maxCharacterSlots = response.maxCharacterSlots();
                Array<Character> gdxArray = new Array<>(response.characters() != null ? response.characters().size() : 0);
                if (response.characters() != null) {
                    for (Character c : response.characters()) {
                        gdxArray.add(c);
                    }
                }
                updateList(response.characters() != null ? gdxArray : new Array<>());
            }
        });
    }

    @Override
    public void onCreateCharacterResponse(CreateCharacterResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success() && response.character() != null) {
                characters.add(response.character());
                updateList(characters);
            }
        });
    }

    @Override
    public void onDeleteCharacterResponse(DeleteCharacterResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success()) {
                fetchCharacters();
            }
        });
    }

    /**
     * Rebuilds the actor tree with the now-current translations. {@link BaseScreen#onLocalizationResponse}
     * calls this on each successful {@code LocalizationResponse} (which has already applied the new
     * translations map centrally). No re-fetch needed — {@code setupUI()}'s final {@code updateList()}
     * re-renders every row from the already-fetched {@code characters} array; only the localized strings
     * change, not the data. Textures are constructor-only, so rebuilding here leaks nothing.
     */
    @Override
    public void refreshUI() {
        setupUI();
    }

    @Override
    public void dispose() {
        super.dispose();
        if (placeholderAvatarTexture != null) {
            placeholderAvatarTexture.dispose();
        }
        if (rowBackgroundTexture != null) {
            rowBackgroundTexture.dispose();
        }
        game.getNetworkClient().setListener(null);
    }
}
