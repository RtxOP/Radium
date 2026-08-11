package net.caffeinemc.mods.sodium.client.render.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

/**
 * Utility functions for working with sprites.
 *
 * <p>MCP 1.8.9 divergence: the reference marks sprites "active" through a {@code SpriteExtension} mixin so that
 * only sprites used by visible chunks tick their animations. 1.8.9's {@code TextureMap#tickAnimations()} animates
 * every atlas sprite unconditionally, so this port intentionally makes the active-marking a no-op. The visual
 * result is identical (animation still plays), with only the (small) per-tick cost of animating off-screen sprites
 * retained from vanilla.</p>
 */
public class SpriteUtil {
    public static void markSpriteActive(@Nullable TextureAtlasSprite sprite) {
        // Can happen in some cases, for example if a mod passes a BakedQuad with a null sprite
        // to a VertexConsumer that does not have a texture element.
        // no-op in 1.8.9: all sprites tick their animation through TextureMap#tickAnimations
    }
}
