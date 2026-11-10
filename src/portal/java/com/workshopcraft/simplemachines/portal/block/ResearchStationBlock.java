package com.workshopcraft.simplemachines.portal.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.machine.MachineBlock;
import com.workshopcraft.simplemachines.portal.Portal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Research Station: an MV machine that eats large amounts of a resource to unlock it for portal worlds, and imprints jump cards. */
public class ResearchStationBlock extends MachineBlock {
    public static final MapCodec<ResearchStationBlock> CODEC = simpleCodec(ResearchStationBlock::new);

    public ResearchStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> entityType() {
        return Portal.RESEARCH_ENTITY;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchStationBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    random.nextDouble() - 0.5, -0.5, random.nextDouble() - 0.5);
        }
    }
}
