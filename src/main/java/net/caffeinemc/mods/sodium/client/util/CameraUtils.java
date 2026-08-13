package net.caffeinemc.mods.sodium.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public final class CameraUtils {
    private CameraUtils() {
    }

    public static BlockPos getBlockPosition() {
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
        if (entity == null) {
            return new BlockPos(0, 0, 0);
        }
        return new BlockPos(MathHelper.floor_double(entity.posX), MathHelper.floor_double(entity.posY), MathHelper.floor_double(entity.posZ));
    }

    public static double[] getCameraPosition() {
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
        if (entity == null) {
            return new double[] { 0.0, 0.0, 0.0 };
        }
        return new double[] { entity.posX, entity.posY, entity.posZ };
    }
}
