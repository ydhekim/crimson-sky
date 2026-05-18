package io.github.ydhekim.crimson_sky.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Manages application configuration, including reading from files and providing defaults.
 * Applies Dependency Inversion Principle by abstracting configuration loading.
 */
public class ConfigurationManager {
    private final Properties properties;

    public ConfigurationManager() {
        this.properties = new Properties();
        loadConfiguration();
    }

    /**
     * Loads configuration from local.properties file.
     * Tries multiple paths: ../local.properties (root directory) and local.properties (current directory).
     */
    private void loadConfiguration() {
        File rootProps = new File("../local.properties");
        File currentProps = new File("local.properties");
        File targetFile = rootProps.exists() ? rootProps : (currentProps.exists() ? currentProps : null);

        if (targetFile != null) {
            try (FileInputStream fis = new FileInputStream(targetFile)) {
                properties.load(fis);
                System.out.println("Configuration loaded from: " + targetFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error reading configuration file: " + e.getMessage());
            }
        } else {
            System.out.println("No configuration file found. Using defaults.");
        }
    }

    /**
     * Gets a string property with a default value fallback.
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Gets a string property or null if not found.
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Gets an integer property with a default value fallback.
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Invalid integer value for property: " + key);
            return defaultValue;
        }
    }

    /**
     * Gets the test identity token from configuration.
     */
    public String getTestIdentityToken() {
        return getProperty("testIdentityToken");
    }

    /**
     * Gets the language code from configuration.
     */
    public String getLangCode() {
        return getProperty("testLangCode");
    }
}

