package com.workshopcraft.simplemachines.portal.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * A world made at runtime by a portal gate. Only the seed differs from a normal secondary level:
 * vanilla reads one seed for every dimension, so each portal world keeps its own here.
 * The seed field is set before super() (Java 25 flexible constructor bodies) because the chunk map
 * asks for the seed while the ServerLevel constructor is still running.
 */
public class PortalLevel extends ServerLevel {
    private final long portalSeed;
    private final String worldId;

    public PortalLevel(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess storage, ServerLevelData data,
                       ResourceKey<Level> key, LevelStem stem, long seed, String worldId) {
        //? if >=26.1 {
        this.portalSeed = seed;
        this.worldId = worldId;
        super(server, executor, storage, data, key, stem, false, BiomeManager.obfuscateSeed(seed), List.of(), false);
        //?} else {
        /*super(server, executor, storage, data, key, stem, pending(seed), false, BiomeManager.obfuscateSeed(seed), List.of(), false, null);
        this.portalSeed = seed;
        this.worldId = worldId;
        PENDING.remove();
        *///?}
    }

    //? if <26.1 {
    /*/^* Java 21 has no statements before super(), so the seed rides in here while the constructor runs. ^/
    private static final ThreadLocal<Long> PENDING = new ThreadLocal<>();

    private static net.minecraft.server.level.progress.ChunkProgressListener pending(long seed) {
        PENDING.set(seed);
        return new net.minecraft.server.level.progress.ChunkProgressListener() {
            @Override public void updateSpawnPos(net.minecraft.world.level.ChunkPos center) {}
            @Override public void onStatusChange(net.minecraft.world.level.ChunkPos pos, net.minecraft.world.level.chunk.status.ChunkStatus status) {}
            @Override public void start() {}
            @Override public void stop() {}
        };
    }
    *///?}

    @Override
    public long getSeed() {
        //? if <26.1
        /*if (worldId == null) return PENDING.get();*/
        return portalSeed;
    }

    public String worldId() {
        return worldId;
    }
}
