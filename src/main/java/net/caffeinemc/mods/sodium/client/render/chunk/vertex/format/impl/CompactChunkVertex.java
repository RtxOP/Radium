package net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl;

import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexFormat;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.minecraft.util.MathHelper;

import java.nio.ByteBuffer;

public class CompactChunkVertex implements ChunkVertexType {
    public static final int STRIDE = 20;

    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement(DefaultChunkMeshAttributes.POSITION, ChunkShaderBindingPoints.ATTRIBUTE_POSITION, 0)
            .addElement(DefaultChunkMeshAttributes.COLOR, ChunkShaderBindingPoints.ATTRIBUTE_COLOR, 8)
            .addElement(DefaultChunkMeshAttributes.TEXTURE, ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE, 12)
            .addElement(DefaultChunkMeshAttributes.LIGHT_MATERIAL_INDEX, ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX, 16)
            .build();

    private static final int POSITION_MAX_VALUE = 1 << 20;
    private static final int TEXTURE_MAX_VALUE = 1 << 15;

    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;

    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder getEncoder() {
        return (buffer, offset, materialBits, vertices, section) -> {
            // Calculate the center point of the texture region which is mapped to the quad
            float texCentroidU = 0.0f;
            float texCentroidV = 0.0f;

            for (ChunkVertexEncoder.Vertex vertex : vertices) {
                texCentroidU += vertex.u;
                texCentroidV += vertex.v;
            }

            texCentroidU *= (1.0f / 4.0f);
            texCentroidV *= (1.0f / 4.0f);

            for (int i = 0; i < 4; i++) {
                ChunkVertexEncoder.Vertex vertex = vertices[i];

                int x = quantizePosition(vertex.x);
                int y = quantizePosition(vertex.y);
                int z = quantizePosition(vertex.z);

                int u = encodeTexture(texCentroidU, vertex.u);
                int v = encodeTexture(texCentroidV, vertex.v);

                int light = encodeLight(vertex.light);

                buffer.putInt(offset +  0, packPositionHi(x, y, z));
                buffer.putInt(offset +  4, packPositionLo(x, y, z));
                buffer.putInt(offset +  8, ColorARGB.mulRGB(vertex.color, vertex.ao));
                buffer.putInt(offset + 12, packTexture(u, v));
                buffer.putInt(offset + 16, packLightAndData(light, materialBits, section));

                offset += STRIDE;
            }

            return offset;
        };
    }

    private static int packPositionHi(int x, int y, int z) {
        return  (((x >>> 10) & 0x3FF) <<  0) |
                (((y >>> 10) & 0x3FF) << 10) |
                (((z >>> 10) & 0x3FF) << 20);
    }

    private static int packPositionLo(int x, int y, int z) {
        return  ((x & 0x3FF) <<  0) |
                ((y & 0x3FF) << 10) |
                ((z & 0x3FF) << 20);
    }

    private static int quantizePosition(float position) {
        return ((int) (normalizePosition(position) * POSITION_MAX_VALUE)) & 0xFFFFF;
    }

    private static float normalizePosition(float v) {
        return (MODEL_ORIGIN + v) / MODEL_RANGE;
    }

    private static int packTexture(int u, int v) {
        return ((u & 0xFFFF) << 0) | ((v & 0xFFFF) << 16);
    }

    private static int encodeTexture(float center, float x) {
        // Shrink the texture coordinates (towards the center of the mapped texture region) by the minimum
        // addressable unit (after quantization.) Then, encode the sign of the bias that was used, and apply
        // the inverse transformation on the GPU with a small epsilon.
        int bias = (x < center) ? 1 : -1;
        int quantized = floorInt(x * TEXTURE_MAX_VALUE) + bias;

        return (quantized & 0x7FFF) | (sign(bias) << 15);
    }

    private static int encodeLight(int light) {
        int sky = MathHelper.clamp_int((light >>> 16) & 0xFF, 8, 248);
        int block = MathHelper.clamp_int((light >>>  0) & 0xFF, 8, 248);

        return (block << 0) | (sky << 8);
    }

    private static int packLightAndData(int light, int material, int section) {
        return ((light & 0xFFFF) << 0) |
                ((material & 0xFF) << 16) |
                ((section & 0xFF) << 24);
    }

    private static int sign(int x) {
        return (x >>> 31);
    }

    private static int floorInt(float x) {
        return (int) Math.floor(x);
    }
}
