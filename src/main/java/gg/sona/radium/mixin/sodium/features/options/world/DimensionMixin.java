package gg.sona.radium.mixin.sodium.features.options.world;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(WorldProvider.class)
public class DimensionMixin {
    /**
     * @reason Cloud height setting
     * @author Decencies
     */
    @Overwrite
    public float getCloudHeight() {
        return SodiumClientMod.options().quality.cloudHeight;
    }
}
