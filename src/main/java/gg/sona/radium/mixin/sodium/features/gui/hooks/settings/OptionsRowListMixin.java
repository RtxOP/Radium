package gg.sona.radium.mixin.sodium.features.gui.hooks.settings;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsRow;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Appends the Sodium category rows (Performance/Quality + Advanced) to the
 * vanilla scrollable options list on the Video Settings screen, so they line
 * up in the same two-column rows as the vanilla option buttons. The vanilla
 * screen itself is left untouched.
 */
@Mixin(GuiOptionsRowList.class)
public class OptionsRowListMixin {
    @Shadow
    private List<GuiOptionsRowList.Row> field_148184_k;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sodiumAppendCategoryRows(Minecraft mc, int width, int height, int top, int bottom,
                                          int slotHeight, GameSettings.Options[] options, CallbackInfo ci) {
        GuiScreen current = mc.currentScreen;
        if (!(current instanceof GuiVideoSettings)) {
            return;
        }

        this.field_148184_k.add(new SodiumOptionsRow(width, SodiumOptionsScreen.Category.PERFORMANCE,
                SodiumOptionsScreen.Category.QUALITY));
        this.field_148184_k.add(new SodiumOptionsRow(width, SodiumOptionsScreen.Category.ADVANCED, null));
    }
}
