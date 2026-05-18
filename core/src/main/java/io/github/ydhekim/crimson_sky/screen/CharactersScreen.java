package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
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
import io.github.ydhekim.crimson_sky.network.NetworkListener;

public class CharactersScreen extends BaseScreen implements NetworkListener {

    private Table charactersListContainer;
    private VisScrollPane scrollPane;
    private Array<Character> characters;
    private int maxCharacterSlots = 3;

    private VisTable footerTable;
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
        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel("Character Selection");
        titleLabel.setFontScale(2f);
        mainPanel.add(titleLabel).padBottom(20).row();

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
            .withSize(200, 40)
            .withAction(() -> screenRouter.navigateTo(ScreenType.MAIN_MENU))
            .buildAndAddTo(footerTable);

        footerTable.add().expandX();

        createCharacterButton = new UIButtonBuilder("New Character")
            .withStyle(customButtonStyle)
            .withSize(200, 40)
            .withAction(this::navigateToCharacterCreation)
            .build();
        footerTable.add(createCharacterButton).width(200).height(40).right();

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

        if (characters.isEmpty()) {
            charactersListContainer.add(new VisLabel("No characters found. Create one to begin your journey!")).expand().center();
        } else {
            for (Character character : characters) {
                Table rowTable = createCharacterRow(character);
                charactersListContainer.add(rowTable).growX().padBottom(10).row();
            }
        }

        boolean canCreate = characters.size < maxCharacterSlots;
        createCharacterButton.setDisabled(!canCreate);
        if (!canCreate) {
            createCharacterButton.setText("Slots Full");
        } else {
            createCharacterButton.setText("New Character");
        }
    }

    /**
     * Creates character row using CharacterRowBuilder (Builder + Command Pattern).
     */
    private Table createCharacterRow(final Character character) {
        return new CharacterRowBuilder(character)
            .withAvatarTexture(placeholderAvatarTexture)
            .withRowBackgroundTexture(rowBackgroundTexture)
            .withButtonStyle(customButtonStyle)
            .onPlay(() -> startGame(character))
            .onDelete(() -> confirmDeleteCharacter(character))
            .build();
    }

    /**
     * Shows confirmation dialog for character deletion using UIButtonBuilder.
     */
    private void confirmDeleteCharacter(final Character character) {
        VisDialog dialog = new VisDialog("Delete Character") {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    game.getNetworkClient().sendTCP(new DeleteCharacterRequest(character.name()));
                }
            }
        };
        dialog.text("Are you sure you want to permanently delete " + character.name() + "?");

        new UIButtonBuilder("Yes")
            .withStyle(customButtonStyle)
            .withSize(96, 32)
            .withAction(() -> {
                dialog.hide();
                game.getNetworkClient().sendTCP(new DeleteCharacterRequest(character.name()));
            })
            .buildAndAddTo(dialog.getButtonsTable());
        dialog.getButtonsTable().add().expandX();

        new UIButtonBuilder("No")
            .withStyle(customButtonStyle)
            .withSize(96, 32)
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

    @Override
    public void onLocalizationResponse(LocalizationResponse response) {
        Gdx.app.log("CharactersScreen", "Response alındı! Başarı: " + response.success());

        if (response.success() && response.translations() != null) {
            Gdx.app.log("CharactersScreen", "Gelen veri boyutu: " + response.translations().size());

            // Gelen verileri tek tek logla (Hangi anahtarlar gelmiş görelim)
            response.translations().forEach((key, value) ->
                Gdx.app.log("CharactersScreen", "Key: " + key + " | Value: " + value));

            Gdx.app.postRunnable(() -> {
                // 1. Veriyi menajere işle
                game.getLanguageManager().setTranslations(response.translations());

                // 2. KRİTİK NOKTA: Ekranı taze dille baştan yarat
                // setupUI() sadece metni değiştirmez, yeni objeler oluşturur.
                // O yüzden en temizi ekranı setScreen ile yenilemektir.
                game.setScreen(new CharactersScreen(game));
            });
        } else {
            Gdx.app.error("CharactersScreen", "Veri boş veya başarısız!");
        }
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
