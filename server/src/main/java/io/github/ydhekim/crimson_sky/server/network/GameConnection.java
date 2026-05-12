package io.github.ydhekim.crimson_sky.server.network;

import com.esotericsoftware.kryonet.Connection;
import io.github.ydhekim.crimson_sky.server.database.entity.User;
import io.github.ydhekim.crimson_sky.server.database.entity.Account;

public class GameConnection extends Connection {
    public User user;
    public Account account;
}
