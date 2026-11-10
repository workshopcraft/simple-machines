package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pipes, cables and struts: a bar through the block along its axis, half width w pixels. */
public class PillarShapeBlock extends RotatedPillarBlock {
    private final VoxelShape x, y, z;
    private MapCodec<PillarShapeBlock> codec;

    public PillarShapeBlock(Properties properties, double half) {
        super(properties);
        final double a = 8 - half, b = 8 + half;
        x = Block.box(0, a, a, 16, b, b);
        y = Block.box(a, 0, a, b, 16, b);
        z = Block.box(a, a, 0, b, b, 16);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        if (codec == null) codec = simpleCodec(p -> this);
        return codec;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        final Direction.Axis axis = state.getValue(AXIS);
        return axis == Direction.Axis.X ? x : axis == Direction.Axis.Z ? z : y;
    }
}
