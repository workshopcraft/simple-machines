package com.workshopcraft.simplemachines.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Loose live wires: sparks, a crackle now and then, and a small burn if you walk through them. */
public class SparkingWiresBlock extends AttachedBlock {
    public SparkingWiresBlock(Properties properties, double[][] floor) {
        super(properties, floor, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        final Direction f = state.getValue(FACING);
        final double x = pos.getX() + 0.5 - f.getStepX() * 0.35 + (random.nextDouble() - 0.5) * 0.6;
        final double y = pos.getY() + 0.5 - f.getStepY() * 0.35 + (random.nextDouble() - 0.5) * 0.6;
        final double z = pos.getZ() + 0.5 - f.getStepZ() * 0.35 + (random.nextDouble() - 0.5) * 0.6;
        for (int i = 0; i < 1 + random.nextInt(3); i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                (random.nextDouble() - 0.5) * 0.2, random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.2);
        if (random.nextInt(12) == 0) level.playLocalSound(x, y, z, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.4F, 1.6F + random.nextFloat() * 0.4F, false);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        Hazards.burn(level, entity);
    }
}
