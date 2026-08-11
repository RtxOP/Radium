package net.caffeinemc.mods.sodium.client.gui;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Sodium category screen, laid out like the OptiFine 1.8.9 settings screens:
 * a two-column grid of controls (x = width/2 - 155 + i%2 * 160,
 * y = height/6 + 21 * (i/2) - 12) where each control embeds its option name
 * ("Name: Value"), a Done button below, and a hover tooltip after ~0.7 s.
 * Booleans/enums cycle on click; numeric options use vanilla-styled sliders.
 * Every change is applied live and persisted to config/radium-options.json.
 */
public class SodiumOptionsScreen extends GuiScreen {
    public enum Category {
        PERFORMANCE("Performance Options", "Performance..."),
        QUALITY("Quality Options", "Quality..."),
        ADVANCED("Advanced Options", "Advanced...");

        final String title;
        final String buttonLabel;

        Category(String title, String buttonLabel) {
            this.title = title;
            this.buttonLabel = buttonLabel;
        }
    }

    private static final int BUTTON_DONE = 200;

    private final GuiScreen prevScreen;
    private final Category category;

    private final List<Row> rows = new ArrayList<>();

    // ---- tooltip state (OptiFine TooltipManager style) -------------------------
    private int lastMouseX;
    private int lastMouseY;
    private long mouseStillTime;

    private static class Row {
        final GuiButton button;
        final String tooltip;

        Row(GuiButton button, String tooltip) {
            this.button = button;
            this.tooltip = tooltip;
        }
    }

    public SodiumOptionsScreen(GuiScreen prevScreen, Category category) {
        this.prevScreen = prevScreen;
        this.category = category;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.rows.clear();

        switch (this.category) {
            case PERFORMANCE:
                this.buildPerformanceRows();
                break;
            case QUALITY:
                this.buildQualityRows();
                break;
            case ADVANCED:
                this.buildAdvancedRows();
                break;
        }

        this.buttonList.add(new GuiButton(BUTTON_DONE, this.width / 2 - 100, this.height / 6 + 168 + 11, 200, 20, "Done"));
    }

    // ---- row helpers (OptiFine grid) -------------------------------------------

    private GuiButton newRowButton(String text) {
        int i = this.rows.size();
        return new GuiButton(100 + i, this.width / 2 - 155 + (i % 2) * 160,
                this.height / 6 + 21 * (i / 2) - 12, 150, 20, text);
    }

    private void addRow(String tooltip, GuiButton button, Runnable onClick) {
        this.rows.add(new Row(button, tooltip));
        this.buttonList.add(button);
        this.onClicks.put(button.id, onClick);
    }

    private void addBoolRow(String name, String tooltip, BooleanSupplier get, Consumer<Boolean> set) {
        final GuiButton b = this.newRowButton(name + ": " + (get.getAsBoolean() ? "ON" : "OFF"));
        this.addRow(tooltip, b, () -> {
            boolean next = !get.getAsBoolean();
            set.accept(next);
            b.displayString = name + ": " + (next ? "ON" : "OFF");
            this.save();
        });
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> void addEnumRow(String name, String tooltip, Supplier<T> get, Consumer<T> set) {
        final T[] values = (T[]) get.get().getClass().getEnumConstants();
        final GuiButton b = this.newRowButton(name + ": " + formatEnum(get.get()));
        this.addRow(tooltip, b, () -> {
            T cur = get.get();
            int i = 0;
            for (i = 0; i < values.length; i++) {
                if (values[i] == cur) {
                    break;
                }
            }
            T next = values[(i + 1) % values.length];
            set.accept(next);
            b.displayString = name + ": " + formatEnum(next);
            this.save();
        });
    }

    private void addIntSliderRow(String name, String tooltip, int min, int max, int value,
                                 IntConsumer set, IntFunction<String> fmt) {
        SodiumSlider slider = new SodiumSlider(100 + this.rows.size(), this.width / 2 - 155 + (this.rows.size() % 2) * 160,
                this.height / 6 + 21 * (this.rows.size() / 2) - 12, 150, name, min, max, value, set, fmt, this::save);
        this.addRow(tooltip, slider, () -> { /* value already applied by the slider */ });
    }

    // ---- category contents ---------------------------------------------------------

    private void buildPerformanceRows() {
        SodiumOptions.Performance p = SodiumClientMod.options().performance;

        this.addEnumRow("Perfect Translucency",
                "Uses quad splitting to make translucent blocks (not entities or items) look correct through translucency sorting even if they are intersecting or have weird shapes. In \"Safe\" mode there is a limit on how much geometry can be generated in extreme cases to prevent crashes or performance degradation. This limit is disabled in \"Unlimited\" mode.",
                () -> p.quadSplittingMode, v -> p.quadSplittingMode = v);
        this.addIntSliderRow("Chunk Update Threads",
                "Specifies the number of threads to use for chunk building and sorting. Using more threads can speed up chunk loading and update speed, but may negatively impact frame times. The default value is usually good enough for all situations.",
                0, 8, p.chunkBuilderThreads, v -> p.chunkBuilderThreads = v, v -> v == 0 ? "Auto" : String.valueOf(v));
        this.addEnumRow("Chunk Updates",
                "If set to \"Deferred\", rendering will never wait for nearby chunk updates to finish, even if they are important. This can greatly improve frame rates in some scenarios, but it may create significant visual lag where blocks take a while to appear or disappear. \"Immediate\" eliminates visual lag by blocking the frame until chunk updates are complete while \"Soon\" allows at most one frame of visual lag.",
                () -> p.chunkBuildDeferMode, v -> p.chunkBuildDeferMode = v);
        this.addBoolRow("Use Async Culling",
                "If enabled, chunk visibility culling runs on a background thread. This can improve smoothness on some systems, but it may regress frame times or stability on others. Disable this to use the older synchronous culling path.",
                () -> p.useAsyncCulling, v -> p.useAsyncCulling = v);
        this.addBoolRow("Use Fog Occlusion",
                "If enabled, chunks which are determined to be fully hidden by fog effects will not be rendered, helping to improve performance. The improvement can be more dramatic when fog effects are heavier (such as while underwater), but it may cause undesirable visual artifacts between the sky and fog in some scenarios.",
                () -> p.useFogOcclusion, v -> p.useFogOcclusion = v);
        this.addBoolRow("Use Block Face Culling",
                "If enabled, only the faces of blocks which are facing the camera will be submitted for rendering. This can eliminate a large number of block faces very early in the rendering process, which greatly improves rendering performance. Some resource packs may have issues with this option, so try disabling it if you are seeing holes in blocks.",
                () -> p.useBlockFaceCulling, v -> p.useBlockFaceCulling = v);
        this.addBoolRow("Use Smart Culling",
                "If enabled, an advanced culling algorithm is used to cull. In some cases, this can result in a huge FPS boost.",
                () -> p.smartCull, v -> p.smartCull = v);
        this.addBoolRow("Use Entity Culling",
                "If enabled, entities which are within the camera viewport, but not inside of a visible chunk, will be skipped during rendering. This optimization uses the visibility data which already exists for chunk rendering and does not add overhead.",
                () -> p.useEntityCulling, v -> p.useEntityCulling = v);
    }

    private void buildQualityRows() {
        SodiumOptions.Quality q = SodiumClientMod.options().quality;

        this.addBoolRow("Clouds",
                "Controls if the clouds are rendered or not.",
                () -> q.enableClouds, v -> q.enableClouds = v);
        this.addBoolRow("Vignette",
                "If enabled, a vignette effect will be applied when the player is in darker areas, which makes the overall image darker and more dramatic.",
                () -> q.enableVignette, v -> q.enableVignette = v);
        this.addIntSliderRow("Biome Blend Radius",
                "The area (in blocks) where biome colors are smoothly blended across. Default is 5x5. Higher values will greatly increase the amount of time it takes to load or update chunks, for diminishing improvements in quality.",
                0, 4, q.biomeBlendRadius, v -> q.biomeBlendRadius = v, v -> String.valueOf(v));
        this.addIntSliderRow("Cloud Height",
                "Controls the height of the clouds.",
                64, 256, (int) q.cloudHeight, v -> q.cloudHeight = v, v -> v + " m");
        this.addIntSliderRow("Chunk Fade",
                "How long in seconds chunks should fade in when they are first rendered, if at all.",
                0, 5000, q.chunkSectionFadeInTime, v -> q.chunkSectionFadeInTime = v, v -> v == 0 ? "Instant" : v + " ms");
    }

    private void buildAdvancedRows() {
        SodiumOptions.Advanced a = SodiumClientMod.options().advanced;

        this.addBoolRow("CPU Render-Ahead",
                "If enabled, the game rendering is ensured to be synced with the game logic.",
                () -> a.cpuRenderAhead, v -> a.cpuRenderAhead = v);
        this.addIntSliderRow("CPU Render-Ahead Limit",
                "For debugging only. Specifies the maximum number of frames which can be in-flight to the GPU.",
                1, 6, a.cpuRenderAheadLimit, v -> a.cpuRenderAheadLimit = v, v -> v + " frame(s)");
        this.addBoolRow("Advanced Staging Buffers",
                "If enabled, a larger pool of staging buffers is used for uploading chunk geometry to the GPU. This can speed up chunk loading on some drivers, at the cost of using more memory.",
                () -> a.useAdvancedStagingBuffers, v -> a.useAdvancedStagingBuffers = v);
        this.addBoolRow("Memory Tracing",
                "Enables detailed logging of memory allocations and releases. Only useful for debugging memory usage; leave disabled for normal use.",
                () -> a.enableMemoryTracing, v -> a.enableMemoryTracing = v);
    }

    // ---- plumbing -----------------------------------------------------------------

    private final java.util.Map<Integer, Runnable> onClicks = new java.util.HashMap<>();

    private static String formatEnum(Enum<?> e) {
        if (e instanceof DeferMode) {
            switch ((DeferMode) e) {
                case ALWAYS:
                    return "Deferred";
                case ONE_FRAME:
                    return "Soon";
                case ZERO_FRAMES:
                    return "Immediate";
            }
        } else if (e instanceof QuadSplittingMode) {
            switch ((QuadSplittingMode) e) {
                case OFF:
                    return "Off";
                case SAFE:
                    return "Safe";
                case UNLIMITED:
                    return "Unlimited";
            }
        }
        return e.name();
    }

    private void save() {
        try {
            SodiumOptions.writeToDisk(SodiumClientMod.options());
        } catch (Exception e) {
            SodiumClientMod.logger().error("Failed to save configuration", e);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof SodiumSlider) {
            return; // the slider applies and saves its own value
        }

        if (button.id == BUTTON_DONE) {
            this.mc.displayGuiScreen(this.prevScreen);
            return;
        }

        Runnable onClick = this.onClicks.get(button.id);
        if (onClick != null) {
            onClick.run();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.mc.fontRendererObj, this.category.title, this.width / 2, 15, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.drawTooltips(mouseX, mouseY);
    }

    /**
     * 1.8.9 GuiScreen has no mouseDragged dispatch, so drag events are forwarded
     * here to the slider that is currently being dragged.
     */
    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        for (GuiButton button : this.buttonList) {
            if (button instanceof SodiumSlider && ((SodiumSlider) button).isDragging()) {
                ((SodiumSlider) button).mouseDragged(this.mc, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) { // Escape
            this.mc.displayGuiScreen(this.prevScreen);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    // ---- tooltips (OptiFine TooltipManager style) ---------------------------------

    private static final int TOOLTIP_DELAY_MS = 700;
    private static final int TOOLTIP_MAX_LINES = 8;

    private void drawTooltips(int mouseX, int mouseY) {
        if (Math.abs(mouseX - this.lastMouseX) > 5 || Math.abs(mouseY - this.lastMouseY) > 5) {
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            this.mouseStillTime = System.currentTimeMillis();
        }

        if (System.currentTimeMillis() < this.mouseStillTime + TOOLTIP_DELAY_MS) {
            return;
        }

        GuiButton hovered = null;
        String tooltip = null;
        for (Row row : this.rows) {
            if (row.button.enabled && mouseX >= row.button.xPosition && mouseX <= row.button.xPosition + row.button.width
                    && mouseY >= row.button.yPosition && mouseY <= row.button.yPosition + row.button.height) {
                hovered = row.button;
                tooltip = row.tooltip;
                break;
            }
        }

        if (hovered == null || tooltip == null) {
            return;
        }

        FontRenderer fr = this.mc.fontRendererObj;
        List<String> lines = wrapTooltip(fr, tooltip, 300 - 12);

        int x = this.width / 2 - 150;
        int y = this.height / 6 - 7;
        if (mouseY <= y + 98) {
            y += 105;
        }

        int boxWidth = 300;
        int boxHeight = lines.size() * 12 + 8;
        if (boxHeight > 94) {
            boxHeight = 94;
        }

        drawRect(x, y, x + boxWidth, y + boxHeight, 0xE0000000);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = 0xDDDDDD;
            if (line.endsWith("!")) {
                color = 0xFF2020;
            }
            fr.drawString(line, x + 6, y + 4 + i * 12, color);
        }
    }

    private static List<String> wrapTooltip(FontRenderer fr, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (fr.getStringWidth(candidate) <= maxWidth || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }

        if (lines.size() > TOOLTIP_MAX_LINES) {
            List<String> trimmed = new ArrayList<>(lines.subList(0, TOOLTIP_MAX_LINES));
            trimmed.set(TOOLTIP_MAX_LINES - 1, trimmed.get(TOOLTIP_MAX_LINES - 1) + " ...");
            return trimmed;
        }
        return lines;
    }
}
