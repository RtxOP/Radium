package net.caffeinemc.mods.sodium.client;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mirrors the reference Radium client mod entry: owns the runtime options
 * singleton, loads (and on first run writes) the JSON config at Forge init.
 */
public class SodiumClientMod {
    private static SodiumOptions CONFIG;
    private static final Logger LOGGER = LogManager.getLogger("Radium");

    private static String MOD_VERSION;

    public static void onInitialization(String version) {
        MOD_VERSION = version;

        CONFIG = loadConfig();
    }

    public static SodiumOptions options() {
        if (CONFIG == null) {
            throw new IllegalStateException("Config not yet available");
        }

        return CONFIG;
    }

    public static Logger logger() {
        if (LOGGER == null) {
            throw new IllegalStateException("Logger not yet available");
        }

        return LOGGER;
    }

    public static String getVersion() {
        return MOD_VERSION;
    }

    private static SodiumOptions loadConfig() {
        try {
            return SodiumOptions.loadFromDisk();
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration file", e);
            LOGGER.error("Using default configuration file in read-only mode", e);

            SodiumOptions config = SodiumOptions.defaults();
            config.setReadOnly();

            return config;
        }
    }

    public static void restoreDefaultOptions() {
        CONFIG = SodiumOptions.defaults();
    }
}
