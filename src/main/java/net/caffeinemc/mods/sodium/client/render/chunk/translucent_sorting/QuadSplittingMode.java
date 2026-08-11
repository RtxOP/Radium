package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting;

import net.caffeinemc.mods.sodium.client.gui.options.TextProvider;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;

/**
 * This port renders options with hardcoded English strings: the mod jar is a
 * Forge coremod, so its assets are never registered as a resource pack and
 * lang keys would render raw. Values here match the UI labels in
 * SodiumOptionsScreen#formatEnum.
 */
public enum QuadSplittingMode implements TextProvider {
    OFF("/", 1.0f, true, "Off"),
    SAFE("S", 2.0f, true, "Safe"),
    UNLIMITED("U", Float.POSITIVE_INFINITY, false, "Unlimited");

    private final String shortName;

    // how much bigger the final geometry is allowed to be compared to the input geometry when performing quad splitting.
    private final float maxAmplificationFactor;
    private final boolean quantizeTriggerNormals;
    private final IChatComponent name;

    QuadSplittingMode(String shortName, float maxAmplificationFactor, boolean quantizeTriggerNormals, String name) {
        this.shortName = shortName;
        this.maxAmplificationFactor = maxAmplificationFactor;
        this.quantizeTriggerNormals = quantizeTriggerNormals;
        this.name = new ChatComponentText(name);
    }

    @Override
    public IChatComponent getLocalizedName() {
        return this.name;
    }

    public String getShortName() {
        return this.shortName;
    }

    public boolean allowsSplitting() {
        return this != OFF;
    }

    public boolean quantizeTriggerNormals() {
        return this.quantizeTriggerNormals;
    }

    public int getMaxTotalQuads(int baseQuadCount) {
        if (Float.isInfinite(this.maxAmplificationFactor)) {
            return Integer.MAX_VALUE;
        }
        return MathHelper.ceiling_float_int(baseQuadCount * this.maxAmplificationFactor);
    }
}