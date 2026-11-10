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
        this.portalSeed = seed;
        this.worldId = worldId;
        super(server, executor, storage, data, key, stem, false, BiomeManager.obfuscateSeed(seed), List.of(), false);
    }

    @Override
    public long getSeed() {
        return portalSeed;
    }

    public String worldId() {
        return worldId;
    }
}
