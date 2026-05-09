package io.github.ydhekim.crimson_sky.server.network;

import com.esotericsoftware.kryonet.Connection;
import io.github.ydhekim.crimson_sky.server.database.entity.User;

public class GameConnection extends Connection {
    public User user;
}
