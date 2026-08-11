package net.caffeinemc.mods.sodium.client.world.cloned;

import dev.vexor.radium.compat.mojang.minecraft.math.BlockBox;
import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;

public class ChunkRenderContext {
    private final SectionPos origin;
    private final ClonedChunkSection[] sections;
    private final BlockBox volume;

    public ChunkRenderContext(SectionPos origin, ClonedChunkSection[] sections, BlockBox volume) {
        this.origin = origin;
        this.sections = sections;
        this.volume = volume;
    }

    public SectionPos origin() {
        return this.origin;
    }

    public ClonedChunkSection[] sections() {
        return this.sections;
    }

    public BlockBox volume() {
        return this.volume;
    }
}
