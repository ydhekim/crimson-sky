package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextField;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.common.network.packet.SignUpResponse;
import io.github.ydhekim.crimson_sky.network.NetworkListener;

public class LoginScreen extends BaseScreen implements NetworkListener {
    private VisTextField usernameField;
    private VisTextField passwordField;
    private VisLabel statusLabel;

    public LoginScreen(final CrimsonSky game) {
        super(game);
        setupUI();
        game.getNetworkClient().setListener(this);
    }

    private void setupUI() {
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        VisLabel titleLabel = new VisLabel("CRIMSON SKY");
        titleLabel.setFontScale(2f); // Simple scaling for now

        usernameField = new VisTextField();
        usernameField.setMessageText("Username");

        passwordField = new VisTextField();
        passwordField.setMessageText("Password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');

        TextButton loginButton = new TextButton("Login", customButtonStyle);
        loginButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleLogin();
            }
        });

        TextButton signUpButton = new TextButton("Sign Up", customButtonStyle);
        signUpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new SignUpScreen(game));
            }
        });

        statusLabel = new VisLabel("");

        rootTable.add(titleLabel).padBottom(40).row();
        rootTable.add(usernameField).width(200).padBottom(10).row();
        rootTable.add(passwordField).width(200).padBottom(20).row();
        rootTable.add(loginButton).width(96).height(32).padBottom(10).row();
        rootTable.add(signUpButton).width(96).height(32).padBottom(10).row();
        rootTable.add(statusLabel).padTop(10);
    }

    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username or password cannot be empty.");
            return;
        }

        statusLabel.setText("Logging in...");
        game.getNetworkClient().sendTCP(new LoginRequest(username, password));
    }

    @Override
    public void onLoginResponse(LoginResponse response) {
        if (response.success) {
            statusLabel.setText("Login successful!");
            game.setScreen(new MainMenuScreen(game));
        } else {
            statusLabel.setText("Login failed: " + response.message);
        }
    }

    @Override
    public void onSignUpResponse(SignUpResponse response) {
        // Not handled on this screen
    }

    @Override
    public void dispose() {
        super.dispose();
        // Clear the listener to avoid memory leaks
        game.getNetworkClient().setListener(null);
    }
}
