package gg.sona.radium.mixin.sodium.core.world.biome;

import net.caffeinemc.mods.sodium.client.world.BiomeSeedProvider;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldClient.class)
public class ClientLevelMixin implements BiomeSeedProvider {
    @Unique
    private long biomeZoomSeed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureSeed(NetHandlerPlayClient netHandler, WorldSettings settings, int dimension, EnumDifficulty difficulty, Profiler profiler, CallbackInfo ci) {
        this.biomeZoomSeed = settings.getSeed();
    }

    @Override
    public long sodium$getBiomeZoomSeed() {
        return this.biomeZoomSeed;
    }
}