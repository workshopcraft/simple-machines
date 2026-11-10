package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Unbreakable see-through hull; joins up with the hull glass next to it like the windows (see {@link ConnectedGlass}). */
public class HullGlassBlock extends TransparentBlock implements Hull {
    public static final MapCodec<HullGlassBlock> CODEC = simpleCodec(HullGlassBlock::new);

    public HullGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(ConnectedGlass.unjoined(stateDefinition.any()));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ConnectedGlass.add(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return ConnectedGlass.joined(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return ConnectedGlass.join(state, dir, neighbour);
    }
}
