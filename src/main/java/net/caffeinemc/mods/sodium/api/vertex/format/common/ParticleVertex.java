package net.caffeinemc.mods.sodium.api.vertex.format.common;

import net.caffeinemc.mods.sodium.api.vertex.attributes.common.ColorAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.LightAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.PositionAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.TextureAttribute;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import java.nio.ByteBuffer;

/**
 * MCP 1.8.9 port: {@code DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP} exists in both codebases with the same
 * layout (position 3×float, uv 2×float, color 4×ubyte, light 2×ushort, stride 28), so this
 * class ports unchanged except for the ByteBuffer offset convention.
 */
public final class ParticleVertex {
    public static final VertexFormat FORMAT = DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP;

    public static final int STRIDE = 28;

    private static final int OFFSET_POSITION = 0;
    private static final int OFFSET_TEXTURE = 12;
    private static final int OFFSET_COLOR = 20;
    private static final int OFFSET_LIGHT = 24;

    public static void put(ByteBuffer buffer, int offset,
                           float x, float y, float z, float u, float v, int color, int light) {
        PositionAttribute.put(buffer, offset + OFFSET_POSITION, x, y, z);
        TextureAttribute.put(buffer, offset + OFFSET_TEXTURE, u, v);
        ColorAttribute.set(buffer, offset + OFFSET_COLOR, color);
        LightAttribute.set(buffer, offset + OFFSET_LIGHT, light);
    }
}
