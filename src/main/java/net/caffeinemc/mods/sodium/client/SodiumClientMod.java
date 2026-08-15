package net.caffeinemc.mods.sodium.client;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mirrors the reference Radium client mod entry: owns the runtime options
 * singleton and loads (and on first run writes) the JSON config.
 *
 * <p>There is no Forge {@code @Mod} entry point: the jar boots mixin via the
 * manifest {@code TweakClass} cascade, which FML 1.8.x records in
 * {@code CoreModManager.loadedCoremods} and skips during {@code @Mod} discovery.
 * The config therefore loads lazily on first {@link #options()} access.
 */
public class SodiumClientMod {
    private static SodiumOptions CONFIG;
    private static final Logger LOGGER = LogManager.getLogger("Radium");

    /** Matches the project version in build.gradle and mcmod.info. */
    private static final String MOD_VERSION = "0.8.15";

    public static SodiumOptions options() {
        if (CONFIG == null) {
            CONFIG = loadConfig();
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
