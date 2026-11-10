package com.workshopcraft.simplemachines.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/** Two block tall entity blocks (cryopods, sliding doors). The block entity sits in the lower half. */
public abstract class TallBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    protected TallBlock(Properties properties) {
        super(properties);
    }

    protected boolean tall() {
        return true;
    }

    public static boolean lower(BlockState state) {
        return !state.hasProperty(HALF) || state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    public static BlockPos base(BlockState state, BlockPos pos) {
        return lower(state) ? pos : pos.below();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        final BlockPos pos = ctx.getClickedPos();
        final Level level = ctx.getLevel();
        if (tall() && (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(ctx))) return null;
        BlockState s = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        if (tall()) s = s.setValue(HALF, DoubleBlockHalf.LOWER);
        return s;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (tall()) level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!tall() || lower(state)) return true;
        final BlockState below = level.getBlockState(pos.below());
        return below.is(this) && lower(below);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        if (tall() && dir.getAxis() == Direction.Axis.Y && (lower(state) == (dir == Direction.UP))) {
            final boolean partner = neighbour.is(this) && lower(neighbour) != lower(state);
            if (!partner) return Blocks.AIR.defaultBlockState();
            return withPartner(state, neighbour);
        }
        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbour, random);
    }

    /** Copies shared values (like open) from the other half. */
    protected BlockState withPartner(BlockState state, BlockState partner) {
        return state;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // creative breaking the top half must not drop the bottom
        if (tall() && !level.isClientSide() && player.isCreative() && !lower(state)) {
            final BlockPos b = pos.below();
            if (level.getBlockState(b).is(this)) level.setBlock(b, Blocks.AIR.defaultBlockState(), 35);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Sets a value on both halves. */
    protected void setBoth(Level level, BlockPos base, BlockState lowerState, int flags) {
        level.setBlock(base, lowerState, flags);
        if (tall()) {
            final BlockState up = level.getBlockState(base.above());
            if (up.is(this)) level.setBlock(base.above(), withPartner(up, lowerState), flags);
        }
    }

}
