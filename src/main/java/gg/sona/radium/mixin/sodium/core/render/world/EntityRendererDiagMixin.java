package gg.sona.radium.mixin.sodium.core.render.world;

import gg.sona.radium.diag.Diag;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temporary D4 frame-timing probe: measures the full updateCameraAndRender interval (fps)
 * and the spent time inside the method, throttled to one log line per second.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererDiagMixin {
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void diagFrameHead(float partialTicks, long nanoTime, CallbackInfo ci) {
        Diag.frameTick();
    }

    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void diagFrameTail(float partialTicks, long nanoTime, CallbackInfo ci) {
        Diag.frameDone();
    }
}
