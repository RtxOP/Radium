package net.caffeinemc.mods.sodium.api.vertex.format.common;

import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.ColorAttribute;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.PositionAttribute;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

/**
 * MCP 1.8.9 port: {@code DefaultVertexFormats.POSITION_COLOR} exists in both codebases and has the
 * same layout (position 3×float + color 4×ubyte, stride 16), so this class ports unchanged
 * except for the LWJGL2 ByteBuffer-based attribute offset convention.
 */
public final class ColorVertex {
    public static final VertexFormat FORMAT = DefaultVertexFormats.POSITION_COLOR;

    public static final int STRIDE = 16;

    private static final int OFFSET_POSITION = 0;
    private static final int OFFSET_COLOR = 12;

    public static void put(ByteBuffer buffer, int offset, Matrix4f matrix, float x, float y, float z, int color) {
        float xt = MatrixHelper.transformPositionX(matrix, x, y, z);
        float yt = MatrixHelper.transformPositionY(matrix, x, y, z);
        float zt = MatrixHelper.transformPositionZ(matrix, x, y, z);

        put(buffer, offset, xt, yt, zt, color);
    }

    public static void put(ByteBuffer buffer, int offset, float x, float y, float z, int color) {
        PositionAttribute.put(buffer, offset + OFFSET_POSITION, x, y, z);
        ColorAttribute.set(buffer, offset + OFFSET_COLOR, color);
    }
}
