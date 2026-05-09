package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextField;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.common.network.packet.SignUpRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SignUpResponse;
import io.github.ydhekim.crimson_sky.network.NetworkListener;

public class SignUpScreen extends BaseScreen implements NetworkListener {

    private VisTextField usernameField;
    private VisTextField passwordField;
    private VisLabel statusLabel;

    public SignUpScreen(final CrimsonSky game) {
        super(game);
        setupUI();
        game.getNetworkClient().setListener(this);
    }

    private void setupUI() {
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        VisLabel titleLabel = new VisLabel("CREATE ACCOUNT");
        titleLabel.setFontScale(2f);

        usernameField = new VisTextField();
        usernameField.setMessageText("Username");

        passwordField = new VisTextField();
        passwordField.setMessageText("Password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');

        TextButton signUpButton = new TextButton("Sign Up", customButtonStyle);
        signUpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleSignUp();
            }
        });

        TextButton backButton = new TextButton("Back", customButtonStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new LoginScreen(game));
            }
        });

        statusLabel = new VisLabel("");

        rootTable.add(titleLabel).padBottom(40).row();
        rootTable.add(usernameField).width(200).padBottom(10).row();
        rootTable.add(passwordField).width(200).padBottom(20).row();
        rootTable.add(signUpButton).width(96).height(32).padBottom(10).row();
        rootTable.add(backButton).width(96).height(32).padBottom(10).row();
        rootTable.add(statusLabel).padTop(10);
    }

    private void handleSignUp() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username or password cannot be empty.");
            return;
        }

        statusLabel.setText("Signing up...");
        game.getNetworkClient().sendTCP(new SignUpRequest(username, password));
    }

    @Override
    public void onLoginResponse(LoginResponse response) {
        // Not handled on this screen
    }

    @Override
    public void onSignUpResponse(SignUpResponse response) {
        if (response.success) {
            statusLabel.setText("Sign up successful! You can now log in.");
        } else {
            statusLabel.setText("Sign up failed: " + response.message);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        // Clear the listener to avoid memory leaks
        game.getNetworkClient().setListener(null);
    }
}
