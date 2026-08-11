package gg.sona.radium.mixin.sodium.features.options.render_layers;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.block.BlockLeaves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockLeaves.class)
public class LeavesBlockMixin {
    @ModifyVariable(method = "setGraphicsLevel", at = @At("HEAD"), argsOnly = true, index = 1)
    private boolean getSodiumLeavesQuality(boolean fast) {
        return SodiumClientMod.options().quality.leavesQuality.isFancy(!fast);
    }
}
