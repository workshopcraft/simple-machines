package com.workshopcraft.simplemachines.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Connected glass without a connected textures mod: one boolean per side, true when the same block is there. The
 * multipart model draws the dark frame only along face edges whose side is not joined, so a wall of it reads as one
 * sheet with a frame round the outside. The faces between two of them are skipped as for vanilla glass.
 */
public final class ConnectedGlass {
    private ConnectedGlass() {}

    static void add(StateDefinition.Builder<Block, BlockState> builder) {
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) builder.add(p);
    }

    static BlockState unjoined(BlockState state) {
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(p, false);
        return state;
    }

    static BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) state = join(state, d, level.getBlockState(pos.relative(d)));
        return state;
    }

    static BlockState join(BlockState state, Direction d, BlockState neighbour) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), neighbour.is(state.getBlock()));
    }
}
