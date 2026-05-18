package io.github.ydhekim.crimson_sky.screen;

/**
 * Represents the state of the connection/login flow.
 * Implements State Machine pattern using Java Enum.
 * Helps manage UI transitions and prevent invalid action sequences.
 */
public enum ConnectionState {
    IDLE("UI_IDLE"),
    CONNECTING("UI_CONNECTING"),
    AUTHENTICATING("UI_AUTHENTICATING"),
    SUCCESS("UI_LOGIN_SUCCESS"),
    FAILURE("UI_LOGIN_FAILED"),
    DISCONNECTED("UI_DISCONNECTED");

    private final String messageKey;

    ConnectionState(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
