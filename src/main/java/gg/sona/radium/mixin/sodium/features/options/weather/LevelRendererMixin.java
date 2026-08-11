package gg.sona.radium.mixin.sodium.features.options.weather;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public class LevelRendererMixin {
    @Redirect(method = "renderRainSnow", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;field_74347_j:Z", remap = false))
    private boolean redirectGetFancyWeather(GameSettings instance) {
        return SodiumClientMod.options().quality.weatherQuality.isFancy(instance.fancyGraphics);
    }

    @Redirect(method = "renderCloudsCheck", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/GameSettings;func_181147_e()I", remap = false))
    private int redirectClouds(GameSettings instance) {
        return SodiumClientMod.options().quality.enableClouds ? 1 : 0;
    }
}