package com.workshopcraft.simplemachines.ship.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

import java.util.EnumMap;
import java.util.Map;

/** Block shapes in pixels, drawn for one direction and turned for the others. */
public final class Boxes {
    private Boxes() {}

    public static VoxelShape of(double[][] boxes) {
        VoxelShape s = Shapes.empty();
        for (double[] b : boxes) s = Shapes.or(s, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        return s;
    }

    /** Boxes drawn facing north, turned to each horizontal direction (the way the block's front faces). */
    public static Map<Direction, VoxelShape> horizontal(double[][] north) {
        final Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            final double[][] t = new double[north.length][];
            for (int i = 0; i < north.length; i++) t[i] = turn(north[i], d);
            out.put(d, of(t));
        }
        return out;
    }

    /** Boxes drawn for a block on the floor (facing up), turned so they sit against the face opposite each direction. */
    public static Map<Direction, VoxelShape> attached(double[][] up) {
        final Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            final double[][] t = new double[up.length][];
            for (int i = 0; i < up.length; i++) t[i] = attach(up[i], d);
            out.put(d, of(t));
        }
        return out;
    }

    // rotate a north-facing box about the block centre
    private static double[] turn(double[] b, Direction d) {
        final double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (d) {
            case SOUTH -> new double[]{16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0};
            case WEST -> new double[]{z0, y0, 16 - x1, z1, y1, 16 - x0};
            case EAST -> new double[]{16 - z1, y0, x0, 16 - z0, y1, x1};
            default -> b;
        };
    }

    private static double[] attach(double[] b, Direction d) {
        final double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (d) {
            case UP -> b;
            case DOWN -> new double[]{x0, 16 - y1, 16 - z1, x1, 16 - y0, 16 - z0};
            case NORTH -> new double[]{x0, z0, 16 - y1, x1, z1, 16 - y0};
            case SOUTH -> new double[]{16 - x1, z0, y0, 16 - x0, z1, y1};
            case WEST -> new double[]{16 - y1, z0, 16 - x1, 16 - y0, z1, 16 - x0};
            case EAST -> new double[]{y0, z0, x0, y1, z1, x1};
        };
    }
}
