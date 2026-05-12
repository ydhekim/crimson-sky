package io.github.ydhekim.crimson_sky.server.database.entity;

public record AccountSettings(
    double volumeMaster,
    String language,
    boolean fullscreen
) {}
