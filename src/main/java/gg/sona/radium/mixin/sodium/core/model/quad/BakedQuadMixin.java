package gg.sona.radium.mixin.sodium.core.model.quad;

import net.caffeinemc.mods.sodium.client.model.quad.BakedQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.caffeinemc.mods.sodium.client.util.ModelQuadUtil;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges the vanilla 1.8.9 {@link BakedQuad} into sodium's {@link BakedQuadView} API.
 *
 * <p>MCP 1.8.9 divergence: the reference hooks {@code TexturedBakedQuad} to resolve a sprite. 1.8.9's
 * {@code BakedQuad} stores no sprite reference, so {@link #getSprite()} returns null (callers that need a
 * sprite resolve it through other means, e.g. the model or the atlas).</p>
 */
@Mixin(BakedQuad.class)
public abstract class BakedQuadMixin implements BakedQuadView {
    @Shadow
    @Final
    protected EnumFacing face; // MCP 1.8.9: "face" is the light face (the reference's "direction" field)

    @Shadow
    @Final
    protected int[] vertexData;

    @Shadow
    @Final
    protected int tintIndex;

    @Unique
    private int flags;

    @Unique
    private int normal;

    @Unique
    private ModelQuadFacing normalFace = null;

    @Shadow
    public abstract boolean hasTintIndex();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(int[] is, int i, EnumFacing direction, CallbackInfo ci) {
        this.normal = this.calculateNormal();
        this.normalFace = ModelQuadFacing.fromPackedNormal(this.normal);

        this.flags = ModelQuadFlags.getQuadFlags(this, direction);
    }

    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX]);
    }

    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 1]);
    }

    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 2]);
    }

    @Override
    public int getColor(int idx) {
        return this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.COLOR_INDEX];
    }

    @Override
    public int getVertexNormal(int idx) {
        return this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.NORMAL_INDEX];
    }

    @Override
    public int getLight(int idx) {
        return this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.LIGHT_INDEX];
    }

    @Override
    public TextureAtlasSprite getSprite() {
        // MCP 1.8.9: no sprite field on BakedQuad (TexturedBakedQuad is 1.12+); sprite resolution happens
        // through the model/atlas instead.
        return null;
    }

    @Override
    public float getTexU(int idx) {
        return Float.intBitsToFloat(this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX]);
    }

    @Override
    public float getTexV(int idx) {
        return Float.intBitsToFloat(this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX + 1]);
    }

    @Override
    public int getFlags() {
        return this.flags;
    }

    @Override
    public int getTintIndex() {
        return this.tintIndex;
    }

    @Override
    public ModelQuadFacing getNormalFace() {
        return this.normalFace;
    }

    @Override
    public int getFaceNormal() {
        return this.normal;
    }

    @Override
    public EnumFacing getLightFace() {
        return this.face;
    }

    @Override
    public int getMaxLightQuad(int idx) {
        return this.vertexData[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.LIGHT_INDEX];
    }

    @Override
    public boolean hasShade() {
        return this.hasTintIndex();
    }

    @Override
    public boolean hasAO() {
        return true;
    }
}
