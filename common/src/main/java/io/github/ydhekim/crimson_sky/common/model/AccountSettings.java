package io.github.ydhekim.crimson_sky.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountSettings(
    @JsonProperty("volume_master")
    double volumeMaster,

    @JsonProperty("language")
    String language,

    @JsonProperty("fullscreen")
    boolean fullscreen,

    @JsonProperty("resolution")
    String resolution
) {
    public static final double DEFAULT_VOLUME = 0.5;
    public static final String DEFAULT_LANGUAGE = "en_US";
    public static final boolean DEFAULT_FULLSCREEN = true;
    public static final String DEFAULT_RESOLUTION = "1280x720";

    public static AccountSettings createDefault() {
        return new AccountSettings(DEFAULT_VOLUME, DEFAULT_LANGUAGE, DEFAULT_FULLSCREEN, DEFAULT_RESOLUTION);
    }

    public AccountSettings withVolume(double newVolume) {
        return new AccountSettings(newVolume, this.language, this.fullscreen, this.resolution);
    }

    public AccountSettings withLanguage(String newLanguage) {
        return new AccountSettings(this.volumeMaster, newLanguage, this.fullscreen, this.resolution);
    }

    public AccountSettings withFullscreen(boolean newFullscreen) {
        return new AccountSettings(this.volumeMaster, this.language, newFullscreen, this.resolution);
    }

    public AccountSettings withResolution(String newResolution) {
        return new AccountSettings(this.volumeMaster, this.language, this.fullscreen, newResolution);
    }
}
