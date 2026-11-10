package com.workshopcraft.simplemachines.portal;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.function.Consumer;

/** What each loader must do differently when a runtime world is added or removed. Set by the loader glue. */
public final class PortalPlatform {
    /** NeoForge caches the level array it ticks; call markWorldsDirty there. Fabric reads the map every tick. */
    public static Consumer<MinecraftServer> worldsChanged = s -> {};
    /** Fires the loader's level load event (NeoForge LevelEvent.Load, Fabric ServerWorldEvents.LOAD). */
    public static Consumer<ServerLevel> loaded = l -> {};
    /** Fires the loader's level unload event. */
    public static Consumer<ServerLevel> unloaded = l -> {};
    /** NeoForge LevelStem carries a seed override, which keeps its structure check in step with the world seed. */
    public static StemFactory stem = (type, generator, seed) -> new LevelStem(type, generator);

    public interface StemFactory {
        LevelStem create(Holder<DimensionType> type, ChunkGenerator generator, long seed);
    }

    private PortalPlatform() {}
}
