package net.caffeinemc.mods.sodium.client.render.chunk;

import org.joml.Matrix4f;

public class ChunkRenderMatrices {
    public final Matrix4f projection;
    public final Matrix4f modelView;

    public ChunkRenderMatrices(Matrix4f projection, Matrix4f modelView) {
        this.projection = projection;
        this.modelView = modelView;
    }

    public Matrix4f projection() {
        return this.projection;
    }

    public Matrix4f modelView() {
        return this.modelView;
    }
}
