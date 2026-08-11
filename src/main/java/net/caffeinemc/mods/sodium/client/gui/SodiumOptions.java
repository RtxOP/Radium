package net.caffeinemc.mods.sodium.client.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.caffeinemc.mods.sodium.client.util.FileUtil;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User-tweakable knobs that the Sodium renderer reads at runtime, persisted to
 * {@code <game dir>/config/radium-options.json} via GSON (field names are
 * lower_snake_case; private members are excluded from the file).
 *
 * Defaults intentionally match the reference Radium set with our max-speed
 * overrides for useAsyncCulling/smartCull (see Phase D2a).
 */
public class SodiumOptions {
    private static final String DEFAULT_FILE_NAME = "radium-options.json";

    public final Quality quality = new Quality();
    public final Advanced advanced = new Advanced();
    public final Performance performance = new Performance();
    public final NotificationSettings notifications = new NotificationSettings();
    public DebugSettings debug = new DebugSettings();

    private boolean readOnly;

    private SodiumOptions() {
        // NO-OP
    }

    public static SodiumOptions defaults() {
        return new SodiumOptions();
    }

    public static class Performance {
        // Corresponds to Sodium's "quad splitting" option; translucency sorting reads it directly.
        public QuadSplittingMode quadSplittingMode = QuadSplittingMode.SAFE;
        // Chunk build thread pool size; 0 = automatic (ChunkBuilder computes optimal count).
        public int chunkBuilderThreads = 0;
        // Chunk update deferral level (parallel with the translucent-sort setup; see DeferMode).
        public DeferMode chunkBuildDeferMode = DeferMode.ALWAYS;
        // Run occlusion culling on a dedicated async thread.
        public boolean useAsyncCulling = true;
        // Skip rendering chunks fully hidden behind the fog plane.
        public boolean useFogOcclusion = true;
        // Cull per-face geometry sets based on the camera position.
        public boolean useBlockFaceCulling = true;
        // Whether to enable "smart" occlusion culling (hides blocks behind opaque neighbors).
        public boolean smartCull = true;
        // Whether to cull entities against the visible-section graph.
        public boolean useEntityCulling = true;
    }

    public static class Quality {
        public boolean enableClouds = true;
        public boolean enableVignette = true;
        // Consumed by LevelColorCache (chunk meshing biome tinting).
        public int biomeBlendRadius = 2;
        public float cloudHeight = 128.0f;
        // Chunk section fade-in animation duration in milliseconds (chunk section fade-in animation).
        public int chunkSectionFadeInTime = 3500;
        public final LeavesQuality leavesQuality = new LeavesQuality();
        public final WeatherQuality weatherQuality = new WeatherQuality();
        public final CloudQuality cloudQuality = new CloudQuality();
    }

    public static class LeavesQuality {
        public boolean isFancy(boolean fancy) { return fancy; }
    }

    public static class WeatherQuality {
        public boolean isFancy(boolean fancy) { return fancy; }
    }

    public static class CloudQuality {
        public boolean isFancy(boolean fancy) { return fancy; }
    }

    public static class Advanced {
        public boolean cpuRenderAhead = false;
        public int cpuRenderAheadLimit = 1;
        // Consumed by the GL buffer layer (GlBufferStreamer / NativeBuffer).
        public boolean useAdvancedStagingBuffers = true;
        public boolean enableMemoryTracing = false;
    }

    public static class NotificationSettings {
        public boolean hasClearedDonationButton = false;
        public boolean hasSeenDonationPrompt = false;
    }

    public static class DebugSettings {
        public boolean terrainSortingEnabled = true;
    }

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static SodiumOptions loadFromDisk() {
        Path path = getConfigPath();
        SodiumOptions config;

        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                config = GSON.fromJson(reader, SodiumOptions.class);
            } catch (IOException e) {
                throw new RuntimeException("Could not parse config", e);
            }
        } else {
            config = new SodiumOptions();
        }

        try {
            writeToDisk(config);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't update config file", e);
        }

        return config;
    }

    private static Path getConfigPath() {
        return getConfigDirectory().resolve(DEFAULT_FILE_NAME);
    }

    private static Path getConfigDirectory() {
        // Forge convention: <game directory>/config/
        return new File(Minecraft.getMinecraft().mcDataDir, "config").toPath();
    }

    public static void writeToDisk(SodiumOptions config) throws IOException {
        if (config.isReadOnly()) {
            throw new IllegalStateException("Config file is read-only");
        }

        Path path = getConfigPath();
        Path dir = path.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        FileUtil.writeTextRobustly(GSON.toJson(config), path);
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }
}
