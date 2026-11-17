package com.workshopcraft.simplemachines.compat.mc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;

/** Chunk loader tickets that load and tick like a forced chunk. */
public final class ChunkTickets {
    private static final TicketType<ChunkPos> TYPE = TicketType.create("simplemachines:chunk_loader", Comparator.comparingLong(ChunkPos::toLong));

    /** A portal gate holding its own chunk while its rift is open; separate so a Chunk Loader in the same chunk never shares it. */
    private static final TicketType<ChunkPos> RIFT = TicketType.create("simplemachines:portal_rift", Comparator.comparingLong(ChunkPos::toLong));

    private ChunkTickets() {}

    public static void addRift(ServerLevel level, ChunkPos pos, int radius) {
        level.getChunkSource().addRegionTicket(RIFT, pos, radius, pos, true);
    }

    public static void removeRift(ServerLevel level, ChunkPos pos, int radius) {
        level.getChunkSource().removeRegionTicket(RIFT, pos, radius, pos, true);
    }

    public static void add(ServerLevel level, ChunkPos pos, int radius) {
        level.getChunkSource().addRegionTicket(TYPE, pos, radius, pos, true);
    }

    public static void remove(ServerLevel level, ChunkPos pos, int radius) {
        level.getChunkSource().removeRegionTicket(TYPE, pos, radius, pos, true);
    }
}
