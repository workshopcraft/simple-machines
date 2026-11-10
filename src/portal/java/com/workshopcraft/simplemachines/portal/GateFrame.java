package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.portal.block.PortalFieldBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A gate: a flat upright ring of frame blocks with the controller in its bottom edge and an empty opening above it.
 * Corners are not needed. Every frame block must be iron, steel, titanium or netherite (by block tag);
 * the weakest one sets the frame tier. Wood or any other block breaks the ring.
 */
public record GateFrame(Direction.Axis axis, BlockPos low, int width, int height, FrameTier tier) {

    public List<BlockPos> opening() {
        final List<BlockPos> out = new ArrayList<>(width * height);
        final Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        for (int u = 0; u < width; u++) for (int v = 0; v < height; v++) out.add(low.relative(along, u).above(v));
        return out;
    }

    public int area() {
        return width * height;
    }

    public AABB box() {
        final Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        final BlockPos high = low.relative(along, width - 1).above(height - 1);
        return AABB.encapsulatingFullBlocks(low, high);
    }

    public static @Nullable FrameTier tierOf(ServerLevel level, BlockState s) {
        if (s.is(Portal.FRAME_NETHERITE) && PortalConfig.bool(level.getServer(), PortalConfig.NETHERITE_FRAMES)) return FrameTier.NETHERITE;
        if (s.is(Portal.FRAME_TITANIUM)) return FrameTier.TITANIUM;
        if (s.is(Portal.FRAME_STEEL)) return FrameTier.STEEL;
        if (s.is(Portal.FRAME_IRON)) return FrameTier.IRON;
        return null;
    }

    private static boolean open(BlockState s) {
        return s.isAir() || s.getBlock() instanceof PortalFieldBlock;
    }

    /** Looks for a gate along both horizontal axes. Returns null when there is no whole ring. */
    public static @Nullable GateFrame find(ServerLevel level, BlockPos controller, int max) {
        final GateFrame x = find(level, controller, Direction.Axis.X, max);
        return x != null ? x : find(level, controller, Direction.Axis.Z, max);
    }

    private static @Nullable GateFrame find(ServerLevel level, BlockPos controller, Direction.Axis axis, int max) {
        final BlockPos start = controller.above();
        if (!open(level.getBlockState(start))) return null;
        final Direction plus = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        final Direction minus = plus.getOpposite();
        int left = 0;
        while (left < max && open(level.getBlockState(start.relative(minus, left + 1)))) left++;
        int right = 0;
        while (right < max && open(level.getBlockState(start.relative(plus, right + 1)))) right++;
        final int width = left + right + 1;
        if (width < 2 || width > max) return null;
        int height = 1;
        while (height <= max && open(level.getBlockState(start.above(height)))) height++;
        if (height < 3 || height > max) return null;
        final BlockPos low = start.relative(minus, left);
        FrameTier tier = FrameTier.NETHERITE;
        // bottom and top edges
        for (int u = 0; u < width; u++) {
            final BlockPos bottom = low.relative(plus, u).below();
            if (!bottom.equals(controller)) {
                final FrameTier t = tierOf(level, level.getBlockState(bottom));
                if (t == null) return null;
                if (t.level < tier.level) tier = t;
            }
            final FrameTier top = tierOf(level, level.getBlockState(low.relative(plus, u).above(height)));
            if (top == null) return null;
            if (top.level < tier.level) tier = top;
        }
        // sides, and the opening must be empty
        for (int v = 0; v < height; v++) {
            final FrameTier a = tierOf(level, level.getBlockState(low.relative(minus).above(v)));
            final FrameTier b = tierOf(level, level.getBlockState(low.relative(plus, width).above(v)));
            if (a == null || b == null) return null;
            if (a.level < tier.level) tier = a;
            if (b.level < tier.level) tier = b;
            for (int u = 0; u < width; u++) if (!open(level.getBlockState(low.relative(plus, u).above(v)))) return null;
        }
        return new GateFrame(axis, low, width, height, tier);
    }
}
