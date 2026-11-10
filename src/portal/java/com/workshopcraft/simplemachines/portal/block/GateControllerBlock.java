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

/** Portal Gate Controller. Part of the bottom edge of a gate frame. HV only. */
public class GateControllerBlock extends MachineBlock {
    public static final MapCodec<GateControllerBlock> CODEC = simpleCodec(GateControllerBlock::new);

    public GateControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<?> entityType() {
        return Portal.GATE_ENTITY;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GateControllerBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(),
                    0.0, 0.3, 0.0);
        }
    }
}
