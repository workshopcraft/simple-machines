package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Two tall sliding doors: plain, airlock and locked. */
public class SlidingDoorBlock extends SlideBlock {
    //? if <26.3 {
    private MapCodec<SlidingDoorBlock> codec;
    //?}

    public SlidingDoorBlock(Properties properties, Kind kind) {
        super(properties, kind);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OPEN, false).setValue(POWERED, false));
    }

    //? if >=1.20.5 <26.3 {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        if (codec == null) codec = simpleCodec(p -> this);
        return codec;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OPEN, POWERED);
    }
}
