package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A broken vent that puffs steam. Standing on it scalds unless you sneak. */
public class SteamVentBlock extends Block {
    public static final MapCodec<SteamVentBlock> CODEC = simpleCodec(SteamVentBlock::new);

    public SteamVentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        final double x = pos.getX() + 0.3 + random.nextDouble() * 0.4, z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
        level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 1.05, z, 0, 0.08 + random.nextDouble() * 0.06, 0);
        if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 1.05, z, 0, 0.12, 0);
        if (random.nextInt(30) == 0) level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 0.6F + random.nextFloat() * 0.3F, false);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        Hazards.burn(level, entity);
        super.stepOn(level, pos, state, entity);
    }
}
