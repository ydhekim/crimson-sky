package io.github.ydhekim.crimson_sky.common.network.packet;

// We need an Account model in common module to share it
// To solve this, we can transfer primitive data, and client side will manage state independently, or keep the old approach where client just receives success/failure
// However, since Kryo requires shared classes to be in common module, Account MUST be in common module if it is sent over network.
// If we want to use the one in server module, then server module Account cannot be sent over Kryo directly.
// The easiest fix is to let LoginResponse only contain simple values (e.g. accountId, maxSlots) instead of the whole Account object

public class LoginResponse {
    public boolean success;
    public String message;

    // Extracted account fields instead of relying on an Account class that is only on the server
    public long accountId;
    public int maxSlots;

    public LoginResponse() {}

    public LoginResponse(boolean success, String message, long accountId, int maxSlots) {
        this.success = success;
        this.message = message;
        this.accountId = accountId;
        this.maxSlots = maxSlots;
    }
}
