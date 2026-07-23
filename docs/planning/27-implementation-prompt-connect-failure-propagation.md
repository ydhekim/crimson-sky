# Implementation prompt — propagate cold-connect failures to the listener

Found while verifying prompt 26 (ConnectionScreen redesign): `KryoClient.connect()` (`core/.../network/KryoClient.java:63-73`) catches the initial `IOException` from `client.connect(...)` and only logs it (`Gdx.app.error`) — it never notifies the `NetworkListener`. Before this redesign this was a silent dead end (the screen just sat on a static "Connecting..." label forever); now that `ConnectionScreen` has a pulsing indicator, the same dead end is visibly stuck rather than quietly stuck, which is what surfaced it. Pre-existing gap, not something prompt 26 introduced — but small enough to fix immediately rather than carry forward.

Fix — reuse the same `onDisconnected()` notification the class already sends from its `disconnected(Connection)` listener callback three lines above, so a cold-connect failure and a post-connect drop both land on the one state `ConnectionScreen` already knows how to show (Retry button, pulse stops):

```java
@Override
public void connect(String host, int tcpPort, int udpPort) {
    if (client.isConnected()) return;
    new Thread(() -> {
        try {
            client.connect(5000, host, tcpPort, udpPort);
        } catch (IOException e) {
            Gdx.app.error("KryoClient", "Failed to connect to server: " + e.getMessage(), e);
            if (listener != null) {
                Gdx.app.postRunnable(listener::onDisconnected);
            }
        }
    }, "KryoClient-Connect").start();
}
```

Deliberately not introducing a distinct "connect failed" state/message key — `ConnectionState.DISCONNECTED`'s existing copy ("disconnected from server") is close enough for "couldn't connect at all," and reusing it avoids widening `ConnectionState`/`NetworkListener`/localization for a wording nuance. Revisit only if it actually reads badly once seen on screen.

## Testing / Definition of Done

1. Stop the server (or point `ConnectionScreen.connectToServer()`'s host at an unreachable address temporarily), run `lwjgl3:run`, confirm the screen now transitions out of `CONNECTING` — pulse stops, Retry button appears — instead of spinning forever.
2. Confirm the existing post-connect-then-drop path (stop the server after a successful connect) still works exactly as before — this fix only adds a second caller of the same `onDisconnected()` notification, it doesn't change the existing one.
3. Normal successful connect/login flow is unaffected.

No server changes, no new tests needed (this is exception-handling plumbing in a network client, not decision logic).
