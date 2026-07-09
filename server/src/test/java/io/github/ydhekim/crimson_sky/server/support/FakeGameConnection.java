package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.server.database.entity.Account;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link GameConnection} that captures what would have been sent instead of touching a socket,
 * so handler/service behaviour can be asserted headlessly (no KryoNet server, no network).
 */
public class FakeGameConnection extends GameConnection {

    private final int id;
    private final List<Object> sent = new ArrayList<>();

    public FakeGameConnection(int id) {
        this.id = id;
    }

    /** An authenticated connection whose account owns whatever the test's fake DAO says it owns. */
    public static FakeGameConnection authenticated(int id, long accountId) {
        FakeGameConnection connection = new FakeGameConnection(id);
        connection.account = new Account(accountId, 0, 3, 0, null, null);
        return connection;
    }

    /** An unauthenticated connection: {@code account} stays null, as before login. */
    public static FakeGameConnection unauthenticated(int id) {
        return new FakeGameConnection(id);
    }

    @Override
    public int getID() {
        return id;
    }

    @Override
    public int sendTCP(Object object) {
        sent.add(object);
        return 0;
    }

    /** Every packet this connection was asked to send, in order. */
    public List<Object> sent() {
        return sent;
    }

    /** The single packet of type {@code type} that was sent, failing the caller's assumption otherwise. */
    public <T> T onlySentPacket(Class<T> type) {
        List<Object> matching = sent.stream().filter(type::isInstance).toList();
        if (matching.size() != 1) {
            throw new AssertionError("Expected exactly one " + type.getSimpleName()
                + " but sent packets were: " + sent);
        }
        return type.cast(matching.get(0));
    }

    public boolean sentNothing() {
        return sent.isEmpty();
    }
}
