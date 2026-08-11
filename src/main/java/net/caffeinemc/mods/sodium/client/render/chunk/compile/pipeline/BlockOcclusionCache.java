package net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline;

import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

public class BlockOcclusionCache {
    private final BlockPos.MutableBlockPos cpos = new BlockPos.MutableBlockPos(0, 0, 0);

    public BlockOcclusionCache() {
    }

    /**
     * @param view   The world view for this render context
     * @param pos    The position of the block
     * @param facing The facing direction of the side to check
     * @return True if the block side facing {@param dir} is not occluded, otherwise false
     */
    public boolean shouldDrawSide(IBlockAccess view, BlockPos pos, EnumFacing facing) {
        BlockPos.MutableBlockPos adjPos = this.cpos;

        adjPos.set(
                pos.getX() + facing.getFrontOffsetX(),
                pos.getY() + facing.getFrontOffsetY(),
                pos.getZ() + facing.getFrontOffsetZ()
        );

        Block self = view.getBlockState(pos).getBlock();

        // MCP 1.8.9: legacy-yarn named this method isSideInvisible, but the 1.8.9 vanilla
        // body (and BlockModelRenderer's render gate) treats TRUE as "side should be drawn".
        // Vanilla 1.8.9 BlockModelRenderer: if (blockIn.shouldSideBeRendered(world, pos.offset(f), f))
        return self.shouldSideBeRendered(view, adjPos, facing);
    }
}
