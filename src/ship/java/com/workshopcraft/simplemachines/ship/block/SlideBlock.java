package com.workshopcraft.simplemachines.ship.block;

import com.workshopcraft.simplemachines.ship.Ship;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Doors, hatches and shutters that slide open. The panel is drawn by SlidingDoorRenderer so it can move. */
public abstract class SlideBlock extends TallBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public enum Kind {
        /** Two tall, slides sideways. Hand or redstone. Opens with the door next to it. */
        DOOR(true, true, 14, 6, 10),
        /** A door that closes every other open airlock door within 8 blocks when it opens. */
        AIRLOCK(true, true, 14, 6, 10),
        /** A door that only opens while its area key has the unlock state (see ShipApi). */
        LOCKED(true, true, 14, 6, 10),
        /** A floor or ceiling panel, slides along its facing. Joined hatches open together. */
        HATCH(false, true, 14, 13, 16),
        /** A full block, slides up. Redstone only; a whole connected wall of shutters moves together. */
        SHUTTER(false, false, 15, 0, 16);

        public final boolean tall, byHand;
        public final float travel, from, to;

        Kind(boolean tall, boolean byHand, float travel, float from, float to) {
            this.tall = tall;
            this.byHand = byHand;
            this.travel = travel;
            this.from = from;
            this.to = to;
        }
    }

    public final Kind kind;
    private final Map<Direction, VoxelShape> closedLower, closedUpper;

    protected SlideBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        if (kind == Kind.HATCH) {
            closedLower = Boxes.horizontal(new double[][]{{0, 13, 0, 16, 16, 16}});
            closedUpper = closedLower;
        } else if (kind == Kind.SHUTTER) {
            closedLower = Boxes.horizontal(new double[][]{{0, 0, 0, 16, 16, 16}});
            closedUpper = closedLower;
        } else {
            closedLower = Boxes.horizontal(new double[][]{{0, 0, 6, 16, 16, 10}});
            closedUpper = closedLower;
        }
    }

    @Override
    protected boolean tall() {
        return kind.tall;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (state.getValue(OPEN)) {
            // a thin lip stays in the frame so the block can still be clicked
            return kind == Kind.HATCH ? Block.box(0, 15, 0, 16, 16, 16) : kind == Kind.SHUTTER ? Block.box(0, 15, 0, 16, 16, 16)
                    : Boxes.horizontal(new double[][]{{14, 0, 6, 16, 16, 10}}).get(state.getValue(FACING));
        }
        return (lower(state) ? closedLower : closedUpper).get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : (lower(state) ? closedLower : closedUpper).get(state.getValue(FACING));
    }

    @Override
    protected BlockState withPartner(BlockState state, BlockState partner) {
        return state.setValue(OPEN, partner.getValue(OPEN)).setValue(POWERED, partner.getValue(POWERED)).setValue(FACING, partner.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return lower(state) ? new SlidingDoorBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() && type == Ship.SLIDING_DOOR_ENTITY ? (l, p, s, be) -> ((SlidingDoorBlockEntity) be).clientTick() : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!kind.byHand) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        final BlockPos base = base(state, pos);
        final BlockState b = level.getBlockState(base);
        if (!b.is(this)) return InteractionResult.PASS;
        if (!canOpen(level, base)) {
            player.sendOverlayMessage(Component.translatable("message.simplemachines.door_locked"));
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.5F);
            return InteractionResult.SUCCESS_SERVER;
        }
        setOpen(level, base, !b.getValue(OPEN));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Locked doors check their key; others always open. */
    public boolean canOpen(Level level, BlockPos base) {
        if (kind != Kind.LOCKED || !(level instanceof ServerLevel sl)) return true;
        return level.getBlockEntity(base) instanceof SlidingDoorBlockEntity d && d.unlocked(sl);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (level.isClientSide()) return;
        final BlockPos base = base(state, pos);
        final BlockState b = level.getBlockState(base);
        if (!b.is(this)) return;
        final boolean powered = level.hasNeighborSignal(base) || (tall() && level.hasNeighborSignal(base.above()));
        if (powered == b.getValue(POWERED)) return;
        setBoth(level, base, b.setValue(POWERED, powered), 2);
        if (powered != b.getValue(OPEN) && canOpen(level, base)) setOpen(level, base, powered);
    }

    /** Opens or closes the door and the ones that move with it. */
    public void setOpen(Level level, BlockPos base, boolean open) {
        final List<BlockPos> group = group(level, base);
        for (BlockPos p : group) {
            final BlockState s = level.getBlockState(p);
            if (s.is(this) && s.getValue(OPEN) != open) setBoth(level, p, s.setValue(OPEN, open), 10);
        }
        level.playSound(null, base, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1F,
                kind == Kind.SHUTTER ? 0.6F : 1.2F);
        if (open && kind == Kind.AIRLOCK) closeOtherAirlocks(level, base, group);
    }

    private void closeOtherAirlocks(Level level, BlockPos base, List<BlockPos> mine) {
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-8, -4, -8), base.offset(8, 4, 8))) {
            final BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof SlideBlock other && other.kind == Kind.AIRLOCK && lower(s) && s.getValue(OPEN) && !mine.contains(p)) {
                other.setOpen(level, p.immutable(), false);
                return;
            }
        }
    }

    /** Lower halves that move together with base. */
    public List<BlockPos> group(Level level, BlockPos base) {
        final BlockState me = level.getBlockState(base);
        if (kind.tall) {
            final List<BlockPos> out = new ArrayList<>();
            out.add(base);
            final Direction f = me.getValue(FACING);
            for (Direction side : new Direction[]{f.getClockWise(), f.getCounterClockWise()}) {
                final BlockPos n = base.relative(side);
                final BlockState s = level.getBlockState(n);
                if (s.is(this) && lower(s) && s.getValue(FACING).getAxis() == f.getAxis()) out.add(n);
            }
            return out;
        }
        final int max = kind == Kind.SHUTTER ? 64 : 16;
        final List<BlockPos> out = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        final ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(base);
        seen.add(base);
        while (!q.isEmpty() && out.size() < max) {
            final BlockPos p = q.poll();
            if (!level.getBlockState(p).is(this)) continue;
            out.add(p);
            for (Direction d : Direction.values()) {
                if (kind == Kind.HATCH && d.getAxis() == Direction.Axis.Y) continue;
                final BlockPos n = p.relative(d);
                if (seen.add(n)) q.add(n);
            }
        }
        return out;
    }
}
