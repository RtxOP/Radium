package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

import java.util.Objects;

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

    // Value equality is required: ShaderChunkRenderer caches compiled programs in a
    // Map keyed by ChunkShaderOptions, and begin() constructs a fresh instance every
    // render pass. Without equals/hashCode the cache can never hit, so a brand-new
    // program is compiled and linked every frame (GL object IDs climbed ~50-100 per
    // second in diag output) — the source of the ~100-400 ms world-render segment.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ChunkShaderOptions)) {
            return false;
        }
        ChunkShaderOptions that = (ChunkShaderOptions) obj;
        return this.fog == that.fog && this.pass == that.pass && this.vertexType == that.vertexType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.fog, this.pass, this.vertexType);
    }

}
