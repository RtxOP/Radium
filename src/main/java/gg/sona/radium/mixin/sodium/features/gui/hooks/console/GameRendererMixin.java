package gg.sona.radium.mixin.sodium.features.gui.hooks.console;

import net.caffeinemc.mods.sodium.client.gui.console.ConsoleHooks;
import net.caffeinemc.mods.sodium.client.gui.console.FPSCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.Sys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class GameRendererMixin {
    @Shadow
    private Minecraft mc;

    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V", remap = false, shift = At.Shift.AFTER))
    private void onRender(float tickDelta, long nanoTime, CallbackInfo ci) {
        this.mc.mcProfiler.startSection("sodium_console_overlay");

        ConsoleHooks.render(Sys.getTime());

        this.mc.mcProfiler.endSection();
    }

    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;func_175180_a(F)V", remap = false, shift = At.Shift.AFTER))
    private void onRenderTwo(float tickDelta, long nanoTime, CallbackInfo ci) {
        if (!this.mc.gameSettings.showDebugInfo) {
            this.mc.mcProfiler.startSection("radium_fps_overlay");

            FPSCounter.INSTANCE.render();

            this.mc.mcProfiler.endSection();
        }
    }
}
