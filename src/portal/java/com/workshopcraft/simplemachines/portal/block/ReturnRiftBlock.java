package com.workshopcraft.simplemachines.portal.block;

import com.workshopcraft.simplemachines.portal.PortalData;
import com.workshopcraft.simplemachines.portal.PortalRifts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The frameless return rift, two blocks tall, held open by a gate in another world. Walk into it to go back.
 * low is the flicker warning. It checks every second that its gate still holds it, and goes away when not.
 */
public class ReturnRiftBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty LOW = BooleanProperty.create("low");
    private static final VoxelShape X = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape Z = Block.box(7, 0, 0, 9, 16, 16);

    public ReturnRiftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(AXIS, Direction.Axis.Z).setValue(LOW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, AXIS, LOW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X : Z;
    }

    private static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer p && !p.isPassenger()) {
            PortalRifts.entered(server, lower(state, pos), p);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide() && !old.is(this)) level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        final PortalData.Rift r = PortalData.get(level.getServer()).riftAt(level.dimension(), lower(state, pos));
        if (r == null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        if (state.getValue(LOW) != r.low) level.setBlock(pos, state.setValue(LOW, r.low), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 20);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        final boolean low = state.getValue(LOW);
        final double x = pos.getX() + 0.5, y = pos.getY() + random.nextDouble(), z = pos.getZ() + 0.5;
        final boolean onX = state.getValue(AXIS) == Direction.Axis.X;
        final double spread = (random.nextDouble() - 0.5) * 0.8;
        if (low) {
            // the flicker: stutters, sparks and the odd dead moment
            if (random.nextInt(4) == 0) return;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, onX ? x + spread : x, y, onX ? z : z + spread, 0, 0.02, 0);
            if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.03, 0);
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER && random.nextInt(30) == 0) {
                level.playLocalSound(pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.25F, 1.6F + random.nextFloat() * 0.3F, false);
            }
        } else {
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.REVERSE_PORTAL, onX ? x + spread : x, y, onX ? z : z + spread, 0, 0.04, 0);
            }
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER && random.nextInt(60) == 0) {
                level.playLocalSound(pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.15F, 1.5F + random.nextFloat() * 0.2F, false);
            }
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }
}
