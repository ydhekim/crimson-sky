package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.network.packet.*;
import io.github.ydhekim.crimson_sky.network.NetworkListener;

public class CharactersScreen extends BaseScreen implements NetworkListener {

    private Table charactersListContainer;
    private VisScrollPane scrollPane;
    private Array<Character> characters;
    private int maxCharacterSlots = 3;

    private TextButton createCharacterButton;
    private Texture placeholderAvatarTexture;
    private Texture rowBackgroundTexture;

    public CharactersScreen(final CrimsonSky game) {
        super(game);
        characters = new Array<>();

        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.DARK_GRAY);
        pixmap.fill();
        placeholderAvatarTexture = new Texture(pixmap);
        pixmap.dispose();

        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(new Color(0.2f, 0.2f, 0.2f, 0.8f));
        bgPixmap.fill();
        rowBackgroundTexture = new Texture(bgPixmap);
        bgPixmap.dispose();

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

        VisTable footerTable = new VisTable();
        TextButton backButton = new TextButton(game.getLanguageManager().get("UI_BTN_BACK"), customButtonStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game));
            }
        });
        createCharacterButton = new TextButton("New Character", customButtonStyle);
        createCharacterButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!createCharacterButton.isDisabled()) {
                    game.setScreen(new CharacterCreationScreen(game));
                }
            }
        });

        footerTable.add(backButton).width(200).height(40).left();
        footerTable.add().expandX();
        footerTable.add(createCharacterButton).width(200).height(40).right();

        mainPanel.add(footerTable).expandX().fillX();

        updateList(characters);
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

    private Table createCharacterRow(final Character character) {
        Table row = new Table();
        row.setBackground(new TextureRegionDrawable(new TextureRegion(rowBackgroundTexture)));
        row.pad(10);

        // Slot 1: Visual
        Image avatar = new Image(placeholderAvatarTexture);
        row.add(avatar).size(64, 64).padRight(20);

        // Slot 2: Info
        Table infoTable = new Table();
        infoTable.left();
        VisLabel nameLabel = new VisLabel(character.name());
        nameLabel.setFontScale(1.2f);
        VisLabel levelLabel = new VisLabel("Level: " + character.level());
        VisLabel expLabel = new VisLabel("EXP: " + character.experience());

        infoTable.add(nameLabel).left().padBottom(5).row();
        infoTable.add(levelLabel).left().row();
        infoTable.add(expLabel).left();

        row.add(infoTable).expandX().fillX();

        // Slot 3: Actions
        Table actionsTable = new Table();
        TextButton playButton = new TextButton("Play", customButtonStyle);
        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startGame(character);
            }
        });

        TextButton deleteButton = new TextButton("Delete", customButtonStyle);
        deleteButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                confirmDeleteCharacter(character);
            }
        });

        actionsTable.add(playButton).width(96).height(32).padBottom(5).row();
        actionsTable.add(deleteButton).width(96).height(32);

        row.add(actionsTable).right().padLeft(20);

        return row;
    }

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

        TextButton yesButton = new TextButton("Yes", customButtonStyle);
        yesButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
                game.getNetworkClient().sendTCP(new DeleteCharacterRequest(character.name()));
            }
        });

        TextButton noButton = new TextButton("No", customButtonStyle);
        noButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
            }
        });

        dialog.getButtonsTable().add(yesButton).width(96).height(32).pad(10);
        dialog.getButtonsTable().add(noButton).width(96).height(32).pad(10);

        dialog.show(stage);
    }

    private void startGame(Character character) {
        System.out.println("Starting game with " + character.name() + "...");
    }

    @Override
    public void onCharacterListResponse(CharacterListResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success) {
                this.maxCharacterSlots = response.maxCharacterSlots;
                Array<Character> gdxArray = new Array<>(response.characters != null ? response.characters.size() : 0);
                if (response.characters != null) {
                    for (Character c : response.characters) {
                        gdxArray.add(c);
                    }
                }
                updateList(response.characters != null ? gdxArray : new Array<>());
            }
        });
    }

    @Override
    public void onCreateCharacterResponse(CreateCharacterResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success && response.character != null) {
                characters.add(response.character);
                updateList(characters);
            }
        });
    }

    @Override
    public void onDeleteCharacterResponse(DeleteCharacterResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success) {
                fetchCharacters();
            }
        });
    }

    @Override
    public void onLocalizationResponse(LocalizationResponse response) {
        Gdx.app.log("CharactersScreen", "Response alındı! Başarı: " + response.success);

        if (response.success && response.translations != null) {
            Gdx.app.log("CharactersScreen", "Gelen veri boyutu: " + response.translations.size());

            // Gelen verileri tek tek logla (Hangi anahtarlar gelmiş görelim)
            response.translations.forEach((key, value) ->
                Gdx.app.log("CharactersScreen", "Key: " + key + " | Value: " + value));

            Gdx.app.postRunnable(() -> {
                // 1. Veriyi menajere işle
                game.getLanguageManager().setTranslations(response.translations);

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
