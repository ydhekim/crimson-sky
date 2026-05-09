package io.github.ydhekim.crimson_sky.server.database.entity;

import java.time.OffsetDateTime;

public class User {
    private int id;
    private String username;
    private String passwordHash;
    private OffsetDateTime createdAt;

    public User(int id, String username, String passwordHash, OffsetDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
