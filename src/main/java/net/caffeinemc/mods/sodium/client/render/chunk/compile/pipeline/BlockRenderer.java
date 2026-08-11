package net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline;

import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.color.ColorProviderRegistry;
import net.caffeinemc.mods.sodium.client.model.light.LightMode;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.BakedQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.lwjgl.util.vector.Vector3f;

import java.util.Arrays;
import java.util.List;

public class BlockRenderer {
    private final ColorProviderRegistry colorProviderRegistry;
    private final BlockOcclusionCache occlusionCache;

    private final QuadLightData quadLightData = new QuadLightData();

    private final LightPipelineProvider lighters;

    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

    private final boolean useAmbientOcclusion;

    private final int[] quadColors = new int[4];

    private ChunkBuildBuffers buffers;

    public BlockRenderer(ColorProviderRegistry colorRegistry, LightPipelineProvider lighters) {
        this.colorProviderRegistry = colorRegistry;
        this.lighters = lighters;

        this.occlusionCache = new BlockOcclusionCache();
        this.useAmbientOcclusion = Minecraft.isAmbientOcclusionEnabled();
    }

    public void prepare(ChunkBuildBuffers buffers) {
        this.buffers = buffers;
    }

    public void renderModel(BlockRenderContext ctx) {
        Material material = DefaultMaterials.forBlockState(ctx.state());
        ChunkModelBuilder meshBuilder = buffers.get(material);

        ColorProvider colorizer = this.colorProviderRegistry.getColorProvider(ctx.state().getBlock());

        LightPipeline lighter = this.lighters.getLighter(this.getLightingMode(ctx.state(), ctx.model()));
        Vector3f offset = new Vector3f();

        Block.EnumOffsetType offsetType = ctx.state().getBlock().getOffsetType();

        if (offsetType != Block.EnumOffsetType.NONE) {
            int x = ctx.pos().getX();
            int z = ctx.pos().getZ();

            // Taken from MathHelper.hashCode()
            long i = (x * 3129871L) ^ z * 116129781L;
            i = i * i * 42317861L + i * 11L;

            offset.x += (((i >> 16 & 15L) / 15.0F) - 0.5f) * 0.5f;
            offset.z += (((i >> 24 & 15L) / 15.0F) - 0.5f) * 0.5f;

            if (offsetType == Block.EnumOffsetType.XYZ) {
                offset.y += (((i >> 20 & 15L) / 15.0F) - 1.0f) * 0.2f;
            }
        }

        for (EnumFacing face : DirectionUtil.ALL_DIRECTIONS) {
            List<BakedQuad> quads = this.getGeometry(ctx, face);

            if (!quads.isEmpty() && this.isFaceVisible(ctx, face)) {
                this.renderQuadList(ctx, material, lighter, colorizer, offset, meshBuilder, quads, face);
            }
        }

        List<BakedQuad> all = this.getGeometry(ctx, null);

        if (!all.isEmpty()) {
            this.renderQuadList(ctx, material, lighter, colorizer, offset, meshBuilder, all, null);
        }
    }

    private List<BakedQuad> getGeometry(BlockRenderContext ctx, EnumFacing face) {
        IBakedModel model = ctx.model();
        return face == null ? model.getGeneralQuads() : model.getFaceQuads(face);
    }

    private boolean isFaceVisible(BlockRenderContext ctx, EnumFacing face) {
        return this.occlusionCache.shouldDrawSide(ctx.slice(), ctx.pos(), face);
    }

    private void renderQuadList(BlockRenderContext ctx, Material material, LightPipeline lighter, ColorProvider colorizer, Vector3f offset,
                                ChunkModelBuilder builder, List<BakedQuad> quads, EnumFacing cullFace) {

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            BakedQuadView quad = (BakedQuadView) quads.get(i);

            final QuadLightData lightData = this.getVertexLight(ctx, lighter, cullFace, quad);
            final int[] vertexColors = this.getVertexColors(ctx, colorizer, quad);

            this.writeGeometry(ctx, builder, offset, material, quad, vertexColors, lightData);

            TextureAtlasSprite sprite = quad.getSprite();

            if (sprite != null) {
                builder.addTextureAtlasSprite(sprite);
            }
        }
    }

    private QuadLightData getVertexLight(BlockRenderContext ctx, LightPipeline lighter, EnumFacing cullFace, BakedQuadView quad) {
        QuadLightData light = this.quadLightData;
        lighter.calculate(quad, ctx.pos(), light, cullFace, quad.getLightFace(), quad.hasShade(), true);

        return light;
    }

    private int[] getVertexColors(BlockRenderContext ctx, ColorProvider colorProvider, BakedQuadView quad) {
        final int[] vertexColors = this.quadColors;

        if (colorProvider != null && quad.hasColor()) {
            colorProvider.getColors(ctx.slice(), ctx.state(), quad, vertexColors, ctx.pos());
        } else {
            Arrays.fill(vertexColors, 0xFFFFFF);
        }

        return vertexColors;
    }

    private void writeGeometry(BlockRenderContext ctx,
                               ChunkModelBuilder builder,
                               Vector3f offset,
                               Material material,
                               BakedQuadView quad,
                               int[] colors,
                               QuadLightData light)
    {
        ModelQuadOrientation orientation = ModelQuadOrientation.orientByBrightness(light.br, light.lm);
        ChunkVertexEncoder.Vertex[] vertices = this.vertices;

        ModelQuadFacing normalFace = quad.getNormalFace();

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = orientation.getVertexIndex(dstIndex);

            ChunkVertexEncoder.Vertex out = vertices[dstIndex];

            out.x = ctx.origin().x() + quad.getX(srcIndex) + offset.x;
            out.y = ctx.origin().y() + quad.getY(srcIndex) + offset.y;
            out.z = ctx.origin().z() + quad.getZ(srcIndex) + offset.z;

            out.color = ColorARGB.toABGR(colors[srcIndex]) | 0xFF000000;
            out.ao = light.br[srcIndex];

            out.u = quad.getTexU(srcIndex);
            out.v = quad.getTexV(srcIndex);

            out.light = light.lm[srcIndex];
        }

        // collect all translucent quads into the translucency sorting system if enabled,
        // and discard the quad if it's invalid (i.e. not visible)
        if (material.isTranslucent() && ctx.collector != null &&
                ctx.collector.appendQuad(vertices, normalFace, quad.getFaceNormal())) {
            return;
        }

        ChunkMeshBufferBuilder vertexBuffer = builder.getVertexBuffer(normalFace);
        vertexBuffer.push(vertices, material);
    }

    private LightMode getLightingMode(IBlockState state, IBakedModel model) {
        if (this.useAmbientOcclusion && model.isAmbientOcclusion() && state.getBlock().getLightValue() == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
    }
}
