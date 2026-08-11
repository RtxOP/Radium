package net.caffeinemc.mods.sodium.client.render.chunk;

import net.caffeinemc.mods.sodium.client.gui.options.TextProvider;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * This port renders options with hardcoded English strings: the mod jar is a
 * Forge coremod, so its assets are never registered as a resource pack and
 * lang keys would render raw. Values here match the UI labels in
 * SodiumOptionsScreen#formatEnum.
 */
public enum DeferMode implements TextProvider {
    ALWAYS("Deferred"),
    ONE_FRAME("Soon"),
    ZERO_FRAMES("Immediate");

    private final IChatComponent name;

    DeferMode(String name) {
        this.name = new ChatComponentText(name);
    }

    @Override
    public IChatComponent getLocalizedName() {
        return this.name;
    }

    public boolean allowsUnlimitedUploadDuration() {
        return this == ZERO_FRAMES;
    }
}
