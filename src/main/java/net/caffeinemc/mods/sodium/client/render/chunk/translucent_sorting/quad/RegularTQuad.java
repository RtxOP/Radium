package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.quad;

import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;

public class RegularTQuad extends TQuad {
    float[] vertexPositions;

    RegularTQuad(ModelQuadFacing facing, int packedNormal) {
        super(facing, packedNormal);
    }

    public static RegularTQuad fromVertices(ChunkVertexEncoder.Vertex[] vertices, ModelQuadFacing facing, int packedNormal) {
        RegularTQuad quad = new RegularTQuad(facing, packedNormal);

        int sameVertexMap = quad.initExtentsAndCenter(vertices);
        if (isInvalid(sameVertexMap)) {
            return null;
        }

        quad.initVertexPositions(vertices, sameVertexMap);
        quad.initDotProduct();

        return quad;
    }

    void initVertexPositions(ChunkVertexEncoder.Vertex[] vertices, int sameVertexMap) {
        // check if we need to store vertex positions for this quad, only necessary if it's unaligned or rotated (yet aligned)
        boolean needsVertexPositions = (sameVertexMap != 0 || !this.facing.isAligned());
        if (!needsVertexPositions) {
            float posXExtent = this.extents[0];
            float posYExtent = this.extents[1];
            float posZExtent = this.extents[2];
            float negXExtent = this.extents[3];
            float negYExtent = this.extents[4];
            float negZExtent = this.extents[5];

            for (int i = 0; i < 4; i++) {
                ChunkVertexEncoder.Vertex vertex = vertices[i];
                if (vertex.x != posYExtent && vertex.x != negYExtent ||
                        vertex.y != posZExtent && vertex.y != negZExtent ||
                        vertex.z != posXExtent && vertex.z != negXExtent) {
                    needsVertexPositions = true;
                    break;
                }
            }
        }

        if (needsVertexPositions) {
            float[] vertexPositions = new float[12];
            this.vertexPositions = vertexPositions;
            for (int i = 0, itemIndex = 0; i < 4; i++) {
                ChunkVertexEncoder.Vertex vertex = vertices[i];
                vertexPositions[itemIndex++] = vertex.x;
                vertexPositions[itemIndex++] = vertex.y;
                vertexPositions[itemIndex++] = vertex.z;
            }
        }
    }

    public float[] getVertexPositions() {
        // calculate vertex positions from extents if there's no cached value
        // (we don't want to be preemptively collecting vertex positions for all aligned quads)
        if (this.vertexPositions == null) {
            this.vertexPositions = new float[12];

            int facingAxis = this.facing.getAxis();
            int xRange = facingAxis == 0 ? 0 : 3;
            int yRange = facingAxis == 1 ? 0 : 3;
            int zRange = facingAxis == 2 ? 0 : 3;

            int itemIndex = 0;
            for (int x = 0; x <= xRange; x += 3) {
                for (int y = 0; y <= yRange; y += 3) {
                    for (int z = 0; z <= zRange; z += 3) {
                        this.vertexPositions[itemIndex++] = this.extents[x];
                        this.vertexPositions[itemIndex++] = this.extents[y + 1];
                        this.vertexPositions[itemIndex++] = this.extents[z + 2];
                    }
                }
            }
        }
        return this.vertexPositions;
    }
}