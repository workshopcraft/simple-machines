package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/** A decoration block that faces the player when placed, with a shape drawn facing north. */
public class ShapedBlock extends HorizontalDirectionalBlock {
    private final Map<Direction, VoxelShape> shapes;
    private final boolean solid;
    private MapCodec<ShapedBlock> codec;

    /** solid false: no collision, like a carpet of debris or a screen. */
    public ShapedBlock(Properties properties, double[][] north, boolean solid) {
        super(properties);
        this.shapes = Boxes.horizontal(north);
        this.solid = solid;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public ShapedBlock(Properties properties, double[][] north) {
        this(properties, north, true);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        if (codec == null) codec = simpleCodec(p -> this);
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return solid ? shapes.get(state.getValue(FACING)) : Shapes.empty();
    }
}
