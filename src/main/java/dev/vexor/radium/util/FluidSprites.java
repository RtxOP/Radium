package dev.vexor.radium.util;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;

/**
 * Caches fluid sprites and quickly allows you to access them for maximum efficiency.
 *
 * <p>Port divergence from the reference: the reference fetches both sprite arrays once from a mixin
 * accessor on the fluid renderer; 1.8.9 has {@code BlockFluidRenderer} without cached sprite fields, so
 * the sprites are resolved lazily from the block texture atlas instead (resource names are the vanilla
 * 1.8.9 ones). {@code forFluid} re-resolves on the first call only, so meshing never runs against a
 * not-yet-loaded atlas.</p>
 */
public class FluidSprites {
    private TextureAtlasSprite[] waterSprites;
    private TextureAtlasSprite[] lavaSprites;

    public FluidSprites() {
    }

    public TextureAtlasSprite[] forFluid(BlockLiquid fluidBlock) {
        if (this.waterSprites == null) {
            this.refresh();
        }
        if (fluidBlock.getMaterial() == Material.water) {
            return this.waterSprites;
        }
        return this.lavaSprites;
    }

    private void refresh() {
        TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
        this.waterSprites = new TextureAtlasSprite[] {
                map.getAtlasSprite("minecraft:blocks/water_still"),
                map.getAtlasSprite("minecraft:blocks/water_flow"),
        };
        this.lavaSprites = new TextureAtlasSprite[] {
                map.getAtlasSprite("minecraft:blocks/lava_still"),
                map.getAtlasSprite("minecraft:blocks/lava_flow"),
        };
    }
}
