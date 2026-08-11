package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

public class ChunkShaderOptions {
    private final ChunkFogMode fog;
    private final TerrainRenderPass pass;
    private final ChunkVertexType vertexType;
    public ChunkShaderOptions(ChunkFogMode fog, TerrainRenderPass pass, ChunkVertexType vertexType) {
        this.fog = fog;
        this.pass = pass;
        this.vertexType = vertexType;
        }

    public ChunkFogMode fog() {
        return this.fog;
    }

    public TerrainRenderPass pass() {
        return this.pass;
    }

    public ChunkVertexType vertexType() {
        return this.vertexType;
    }

    public ShaderConstants constants() {
        ShaderConstants.Builder constants = ShaderConstants.builder();
        constants.addAll(this.fog.getDefines());

        if (this.pass.supportsFragmentDiscard()) {
            constants.add("USE_FRAGMENT_DISCARD");
        }

        constants.add("USE_VERTEX_COMPRESSION"); // TODO: allow compact vertex format to be disabled

        return constants.build();
    }

}
