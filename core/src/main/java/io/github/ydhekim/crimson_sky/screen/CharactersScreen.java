package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
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
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterListRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterListResponse;
import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterResponse;
import io.github.ydhekim.crimson_sky.common.network.packet.DeleteCharacterRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.DeleteCharacterResponse;
import io.github.ydhekim.crimson_sky.network.NetworkListener;

public class CharactersScreen extends BaseScreen implements NetworkListener {

    private Table charactersListContainer;
    private VisScrollPane scrollPane;
    private Array<Character> characters;
    private int maxCharacterSlots = 3; // Default, will be updated by server

    private TextButton createCharacterButton;
    private Texture placeholderAvatarTexture;
    private Texture rowBackgroundTexture; // Cached row background to prevent memory leaks

    public CharactersScreen(final CrimsonSky game) {
        super(game);
        characters = new Array<>();

        // Create a simple gray placeholder texture for character silhouettes
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.DARK_GRAY);
        pixmap.fill();
        placeholderAvatarTexture = new Texture(pixmap);
        pixmap.dispose();

        // Create the row background to reuse for all rows
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(new Color(0.1f, 0.1f, 0.1f, 0.8f));
        bgPixmap.fill();
        rowBackgroundTexture = new Texture(bgPixmap);
        bgPixmap.dispose();

        setupUI();
        game.getNetworkClient().setListener(this);

        // Fetch the characters for the user
        fetchCharacters();
    }

    private void setupUI() {
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        // --- Header ---
        VisLabel titleLabel = new VisLabel("Character Selection");
        titleLabel.setFontScale(2f);
        rootTable.add(titleLabel).padTop(20).padBottom(20).row();

        // --- Character List Container ---
        charactersListContainer = new Table();
        charactersListContainer.top();

        scrollPane = new VisScrollPane(charactersListContainer);
        scrollPane.setOverscroll(false, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        rootTable.add(scrollPane).expand().fill().padLeft(20).padRight(20).row();

        // --- Footer ---
        createCharacterButton = new TextButton("New Character", customButtonStyle);
        createCharacterButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!createCharacterButton.isDisabled()) {
                    // Mocking Character Creation Process
                    mockCharacterCreation();
                }
            }
        });

        TextButton backButton = new TextButton("Back", customButtonStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game));
            }
        });

        Table footerTable = new Table();
        // Vertical layout for the two buttons
        footerTable.add(createCharacterButton).width(96).height(32).padBottom(10).row();
        footerTable.add(backButton).width(96).height(32);

        rootTable.add(footerTable).padTop(20).padBottom(20);

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
        if(!canCreate) {
             createCharacterButton.setText("Slots Full");
        } else {
             createCharacterButton.setText("New Character");
        }
    }

    private Table createCharacterRow(final Character character) {
        Table row = new Table();
        // Give row a subtle background so it looks like a distinct panel
        row.setBackground(new TextureRegionDrawable(new TextureRegion(rowBackgroundTexture)));
        row.pad(10);

        // Slot 1: Visual (Silhouette)
        Image avatar = new Image(placeholderAvatarTexture);
        row.add(avatar).size(64, 64).padRight(20);

        // Slot 2: Info (Name, Level, Exp)
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

        // Slot 3: Actions (Play, Delete)
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

    private void mockCharacterCreation() {
        // Generates a mock name like "Hero_42"
        String mockName = "Hero_" + MathUtils.random(1000, 9999);

        // Disable button temporarily to prevent spamming
        createCharacterButton.setDisabled(true);
        game.getNetworkClient().sendTCP(new CreateCharacterRequest(mockName));
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
        // TODO: Send packet to enter the world with this character, and transition to GameScreen
        System.out.println("Starting game with " + character.name() + "...");
    }

    @Override
    public void onCharacterListResponse(CharacterListResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success) {
                this.maxCharacterSlots = response.maxCharacterSlots;
                updateList(response.characters != null ? response.characters : new Array<>());
            } else {
                // Could show an error dialog here
            }
        });
    }

    @Override
    public void onCreateCharacterResponse(CreateCharacterResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success) {
                fetchCharacters(); // Refresh the list
            } else {
                createCharacterButton.setDisabled(false);
                // Could show an error dialog here
            }
        });
    }

    @Override
    public void onDeleteCharacterResponse(DeleteCharacterResponse response) {
        Gdx.app.postRunnable(() -> {
            if (response.success) {
                fetchCharacters(); // Refresh the list
            } else {
                // Could show an error dialog here
            }
        });
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

        // Clear the listener to avoid memory leaks
        game.getNetworkClient().setListener(null);
    }
}
