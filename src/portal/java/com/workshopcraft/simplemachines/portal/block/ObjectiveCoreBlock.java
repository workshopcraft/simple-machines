package com.workshopcraft.simplemachines.portal.block;

import com.workshopcraft.simplemachines.portal.PortalTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The objective of a portal world. Use it to collect the objective; it goes dim afterwards. */
public class ObjectiveCoreBlock extends Block {
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");

    public ObjectiveCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SPENT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPENT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && !state.getValue(SPENT)) {
            PortalTravel.collect(sp, server, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(SPENT)) return;
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 0.05, 0.08, (random.nextDouble() - 0.5) * 0.05);
    }
}
