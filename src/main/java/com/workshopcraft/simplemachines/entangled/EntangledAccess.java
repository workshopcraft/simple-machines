package com.workshopcraft.simplemachines.entangled;

import com.workshopcraft.simplemachines.cable.ConduitBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Private entangled devices only deal with pipes and cables their owner placed. While one of ours moves something,
 * the device it touches is marked with that conduit's placer; anything else asking (hoppers, other mods) gets nothing.
 */
public final class EntangledAccess {
    private record Key(Level level, BlockPos pos) {
    }

    private static final Map<Key, UUID> CURRENT = new HashMap<>();

    private EntangledAccess() {
    }

    public static boolean allows(@Nullable Level level, BlockPos pos, @Nullable EntangledLink link) {
        if (link == null || !link.priv()) return true;
        if (level == null || level.isClientSide()) return false;
        return link.owner().equals(actor(level, pos));
    }

    /** The placer of the Simple Machines pipe or cable currently inserting at pos, or null for a hopper or another mod. */
    public static @Nullable UUID actor(@Nullable Level level, BlockPos pos) {
        if (level == null) return null;
        synchronized (CURRENT) {
            return CURRENT.get(new Key(level, pos));
        }
    }

    public static int through(Level level, BlockPos device, BlockPos conduit, IntSupplier body) {
        final Key key = new Key(level, device.immutable());
        final UUID old = mark(key, ConduitBlockEntity.ownerAt(level, conduit));
        try {
            return body.getAsInt();
        } finally {
            restore(key, old);
        }
    }

    public static boolean check(Level level, BlockPos device, BlockPos conduit, BooleanSupplier body) {
        return through(level, device, conduit, () -> body.getAsBoolean() ? 1 : 0) != 0;
    }

    private static @Nullable UUID mark(Key key, @Nullable UUID who) {
        synchronized (CURRENT) {
            return who == null ? CURRENT.remove(key) : CURRENT.put(key, who);
        }
    }

    private static void restore(Key key, @Nullable UUID old) {
        synchronized (CURRENT) {
            if (old == null) CURRENT.remove(key);
            else CURRENT.put(key, old);
        }
    }
}
