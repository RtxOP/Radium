package gg.sona.radium.mixin.sodium.features.gui.hooks.debug;

import com.google.common.collect.Lists;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;

@Mixin(GuiOverlayDebug.class)
public abstract class DebugScreenOverlayMixin {
    @Redirect(method = "getDebugInfoRight", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList([Ljava/lang/Object;)Ljava/util/ArrayList;", remap = false))
    private ArrayList<String> redirectRightTextEarly(Object[] elements) {
        ArrayList<String> strings = Lists.newArrayList((String[]) elements);
        strings.add("");
        strings.add(String.format("%sRadium Renderer (%s)", getVersionColor(), SodiumClientMod.getVersion()));

        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();

        if (renderer != null) {
            strings.addAll(renderer.getDebugStrings());
        }

        for (int i = 0; i < strings.size(); i++) {
            String str = strings.get(i);

            if (str.startsWith("Allocated:")) {
                strings.add(i + 1, getNativeMemoryString());

                break;
            }
        }

        return strings;
    }

    @Unique
    private static EnumChatFormatting getVersionColor() {
        String version = SodiumClientMod.getVersion();
        EnumChatFormatting color;

        if (version.contains("-local")) {
            color = EnumChatFormatting.RED;
        } else if (version.contains("-snapshot")) {
            color = EnumChatFormatting.LIGHT_PURPLE;
        } else {
            color = EnumChatFormatting.GREEN;
        }

        return color;
    }

    @Unique
    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    @Unique
    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }
}
