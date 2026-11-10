package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A hull panel breached to space: frost, drifting snow and a chill on anyone standing on it. */
public class FrostyPanelBlock extends Block {
    public static final MapCodec<FrostyPanelBlock> CODEC = simpleCodec(FrostyPanelBlock::new);

    public FrostyPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        final Direction d = Direction.getRandom(random);
        if (!level.getBlockState(pos.relative(d)).isAir()) return;
        final double x = pos.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? random.nextDouble() - 0.5 : 0);
        final double y = pos.getY() + 0.5 + d.getStepY() * 0.55 + (d.getStepY() == 0 ? random.nextDouble() - 0.5 : 0);
        final double z = pos.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0);
        level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, d.getStepX() * 0.02, -0.01, d.getStepZ() * 0.02);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide() && Hazards.on(level) && entity.canFreeze() && !entity.isSteppingCarefully()) {
            entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze() + 20, entity.getTicksFrozen() + 3));
        }
        super.stepOn(level, pos, state, entity);
    }
}
