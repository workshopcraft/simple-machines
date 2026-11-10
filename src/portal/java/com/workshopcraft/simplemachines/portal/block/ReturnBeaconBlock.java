package com.workshopcraft.simplemachines.portal.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.portal.PortalTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Placeholder for the M3 return rift: placed next to the landing spot; use it to go back to the gate you came from. */
public class ReturnBeaconBlock extends BaseEntityBlock {
    public static final MapCodec<ReturnBeaconBlock> CODEC = simpleCodec(ReturnBeaconBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public ReturnBeaconBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReturnBeaconBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            PortalTravel.goBack(sp, level.getBlockEntity(pos) instanceof ReturnBeaconBlockEntity be ? be.gate() : null);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.6);
    }
}
