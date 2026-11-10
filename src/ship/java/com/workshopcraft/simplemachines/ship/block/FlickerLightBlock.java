package com.workshopcraft.simplemachines.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A broken strip light that flickers on and off at random and spits the odd spark. */
public class FlickerLightBlock extends AttachedBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FlickerLightBlock(Properties properties, double[][] floor) {
        super(properties, floor, false);
        registerDefaultState(defaultBlockState().setValue(LIT, true).setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 10);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        final boolean lit = state.getValue(LIT);
        // mostly on, with bursts of short blinks
        final boolean next = lit ? random.nextInt(4) != 0 : random.nextInt(3) == 0;
        if (next != lit) level.setBlock(pos, state.setValue(LIT, next), 2);
        level.scheduleTick(pos, this, next ? 4 + random.nextInt(60) : 1 + random.nextInt(6));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // keeps flickering after a structure placed it without a scheduled tick
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0) return;
        final Direction f = state.getValue(FACING);
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 - f.getStepX() * 0.4, pos.getY() + 0.5 - f.getStepY() * 0.4,
                pos.getZ() + 0.5 - f.getStepZ() * 0.4, 0, -0.05, 0);
    }
}
