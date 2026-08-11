package gg.sona.radium.mixin.sodium.core.access;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface AGameRenderer {
    @Invoker
    void invokeLoadShader(ResourceLocation id);
}
