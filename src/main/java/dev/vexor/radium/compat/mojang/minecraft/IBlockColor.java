package dev.vexor.radium.compat.mojang.minecraft;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * MCP 1.8.9 adaptation of Radium's IBlockColor (Yarn). Yarn's {@code BlockView} is
 * mapped to {@link World} here: every call site in the port passes the client world.
 */
public interface IBlockColor {
    int colorMultiplier(IBlockState state, IBlockAccess worldIn, BlockPos pos, int tintIndex);
}
