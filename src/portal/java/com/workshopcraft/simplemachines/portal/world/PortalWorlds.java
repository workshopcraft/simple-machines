package com.workshopcraft.simplemachines.portal.world;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.portal.PortalData;
import com.workshopcraft.simplemachines.portal.PortalPlatform;
import com.workshopcraft.simplemachines.portal.PortalResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Runtime dimensions: one level per portal world, added to and removed from the running server.
 *
 * Vanilla only makes levels in MinecraftServer.createLevels from the level_stem registry, which is frozen.
 * This class builds the same ServerLevel at runtime and puts it into MinecraftServer.levels by reflection
 * (26.1 is not obfuscated on either loader, so the mojmap names are the runtime names).
 * Changes to the level map only happen at the end of a server tick, never while levels are ticking.
 * Worlds are kept in PortalData and made again when the server starts, before anyone can join.
 * Clients need nothing extra: the respawn packet names the dimension and carries the dimension type,
 * and the portal dimension types are ordinary data pack entries that sync at login.
 */
public final class PortalWorlds {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Queue<Consumer<MinecraftServer>> QUEUE = new ArrayDeque<>();
    private static @Nullable Field levelsField, executorField, storageField;

    private PortalWorlds() {}

    public static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, SimpleMachines.id("portal/" + id));
    }

    public static @Nullable String idOf(ResourceKey<Level> key) {
        final Identifier id = key.identifier();
        return id.getNamespace().equals(SimpleMachines.MODID) && id.getPath().startsWith("portal/") ? id.getPath().substring(7) : null;
    }

    public static boolean isPortal(Level level) {
        return idOf(level.dimension()) != null;
    }

    public static @Nullable ServerLevel level(MinecraftServer server, String id) {
        return server.getLevel(key(id));
    }

    /** Runs the job at the end of this server tick, when the level map can change safely. */
    public static synchronized void later(Consumer<MinecraftServer> job) {
        QUEUE.add(job);
    }

    /** Called by the loader at the end of every server tick. */
    public static void tick(MinecraftServer server) {
        while (true) {
            final Consumer<MinecraftServer> job;
            synchronized (PortalWorlds.class) {
                job = QUEUE.poll();
            }
            if (job == null) break;
            try {
                job.accept(server);
            } catch (Exception e) {
                LOG.error("Simple Machines portal world job failed", e);
            }
        }
    }

    /** Called by the loader once the server has started: makes every saved world again and deletes folders left from last time. */
    public static void serverStarted(MinecraftServer server) {
        final PortalData data = PortalData.get(server);
        for (String id : new ArrayList<>(data.pendingDelete())) {
            if (deleteFolder(server, id)) data.pendingDelete().remove(id);
        }
        data.setDirty();
        for (PortalData.World w : data.worlds()) {
            try {
                load(server, w);
            } catch (Exception e) {
                LOG.error("Simple Machines could not load portal world {}", w.id, e);
            }
        }
    }

    /** Called by the loader when the server stops. Vanilla saves and closes every level in the map, ours included. */
    public static void serverStopped() {
        synchronized (PortalWorlds.class) {
            QUEUE.clear();
        }
    }

    /** Creates or loads the level for a saved world record. Only call at a safe point (see later). */
    public static ServerLevel load(MinecraftServer server, PortalData.World world) {
        final ResourceKey<Level> key = key(world.id);
        final ServerLevel existing = server.getLevel(key);
        if (existing != null) return existing;
        final WorldPreset preset = PortalResources.presets(server).get(world.preset);
        if (preset == null) throw new IllegalStateException("unknown portal preset " + world.preset);
        final HolderLookup.Provider reg = server.registryAccess();
        final Holder<DimensionType> type = reg.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, preset.type()));
        final Holder<NoiseGeneratorSettings> noise = reg.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(ResourceKey.create(Registries.NOISE_SETTINGS, preset.noiseSettings()));
        final PortalChunkGenerator.WorldPlan plan = plan(server, preset, world);
        final PortalChunkGenerator generator = new PortalChunkGenerator(biomes(server, preset, world.seed), noise, plan);
        final LevelStem stem = PortalPlatform.stem.create(type, generator, world.seed);
        final PortalLevel level = new PortalLevel(server, executor(server), storage(server),
                new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), key, stem, world.seed, world.id);
        level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(level);
        levels(server).put(key, level);
        PortalPlatform.worldsChanged.accept(server);
        PortalPlatform.loaded.accept(level);
        LOG.info("Simple Machines loaded portal world {} ({}, seed {})", world.id, world.preset, world.seed);
        return level;
    }

    /**
     * Saves and removes a runtime world. Players inside are sent to fallback first.
     * With delete the folder is removed too; if the files are still busy it is tried again at the next start.
     */
    public static void unload(MinecraftServer server, String id, boolean delete, Consumer<ServerPlayer> evacuate) {
        final ResourceKey<Level> key = key(id);
        if (delete && occupiedOffline(server, id)) {
            // never delete a world a logged-out player is standing in; the cleanup tries again once they have left
            LOG.info("Simple Machines kept portal world {}: a logged-out player is still in it", id);
            return;
        }
        final ServerLevel level = server.getLevel(key);
        com.workshopcraft.simplemachines.portal.PortalRifts.levelGone(server, key);
        if (level != null) {
            for (ServerPlayer p : List.copyOf(level.players())) evacuate.accept(p);
            for (ServerPlayer p : List.copyOf(level.players())) toSpawn(server, p);
            level.save(null, true, false);
            levels(server).remove(key);
            PortalPlatform.worldsChanged.accept(server);
            PortalPlatform.unloaded.accept(level);
            try {
                level.close();
            } catch (Exception e) {
                LOG.warn("Simple Machines could not close portal world {}: {}", id, e.toString());
            }
            LOG.info("Simple Machines unloaded portal world {}", id);
        }
        if (delete) {
            final PortalData data = PortalData.get(server);
            data.remove(id);
            if (!deleteFolder(server, id)) {
                data.pendingDelete().add(id);
                data.setDirty();
            }
        }
    }

    /**
     * Whether a player who is not online was last in this world: the logout record, and the saved player files too
     * (they also catch players who logged out before the record existed, or a crash that lost it).
     */
    public static boolean occupiedOffline(MinecraftServer server, String id) {
        final PortalData data = PortalData.get(server);
        if (data.hasOccupants(id)) return true;
        final String dim = key(id).identifier().toString();
        final Path dir = storage(server).getLevelPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(dir)) return false;
        boolean found = false;
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                final String name = f.getFileName().toString();
                if (!name.endsWith(".dat")) continue;
                final java.util.UUID uuid;
                try {
                    uuid = java.util.UUID.fromString(name.substring(0, name.length() - 4));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (server.getPlayerList().getPlayer(uuid) != null) continue;
                if (dim.equals(savedDimension(f))) {
                    data.addOccupant(id, uuid);
                    found = true;
                }
            }
        } catch (IOException e) {
            LOG.warn("Simple Machines could not read player files, keeping portal world {}: {}", id, e.toString());
            return true;
        }
        return found;
    }

    private static final java.util.Map<Path, Pair<Long, String>> SAVED_DIMS = new java.util.concurrent.ConcurrentHashMap<>();

    private static @Nullable String savedDimension(Path file) {
        try {
            final long stamp = Files.getLastModifiedTime(file).toMillis();
            final Pair<Long, String> cached = SAVED_DIMS.get(file);
            if (cached != null && cached.getFirst() == stamp) return cached.getSecond();
            final String dim = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap())
                    .getStringOr("Dimension", "minecraft:overworld");
            SAVED_DIMS.put(file, Pair.of(stamp, dim));
            return dim;
        } catch (Exception e) {
            return null;
        }
    }

    public static void toSpawn(MinecraftServer server, ServerPlayer p) {
        final ServerLevel overworld = server.overworld();
        final BlockPos spawn = overworld.getRespawnData().pos();
        p.teleport(new TeleportTransition(overworld, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
    }

    public static Path folder(MinecraftServer server, String id) {
        return storage(server).getDimensionPath(key(id));
    }

    private static boolean deleteFolder(MinecraftServer server, String id) {
        final Path dir = folder(server, id);
        if (!Files.exists(dir)) return true;
        try {
            FileUtils.deleteDirectory(dir.toFile());
            return true;
        } catch (Exception e) {
            LOG.warn("Simple Machines will delete {} at the next start: {}", dir, e.toString());
            return false;
        }
    }

    // --- world plan and biome source ---

    public static PortalChunkGenerator.WorldPlan plan(MinecraftServer server, WorldPreset preset, PortalData.World world) {
        final List<WorldPreset.Ore> ores = new ArrayList<>(preset.ores());
        final Map<MobCategory, List<WorldPreset.Mob>> mobs = new EnumMap<>(MobCategory.class);
        for (var e : preset.mobs().entrySet()) mobs.put(e.getKey(), new ArrayList<>(e.getValue()));
        for (String f : world.forced) {
            final int c = f.indexOf(':');
            if (c < 0) continue;
            final String kind = f.substring(0, c);
            final Identifier what = Identifier.tryParse(f.substring(c + 1));
            if (what == null) continue;
            if (kind.equals("ore") && BuiltInRegistries.BLOCK.containsKey(what)) {
                ores.add(new WorldPreset.Ore(what, 3, 6, -56, 96));
            } else if (kind.equals("mob")) {
                final EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getOptional(what).orElse(null);
                if (t == null) continue;
                final MobCategory cat = t.getCategory() == MobCategory.MISC ? MobCategory.CREATURE : t.getCategory();
                mobs.computeIfAbsent(cat, k -> new ArrayList<>()).add(new WorldPreset.Mob(what, 20, 1, 3));
            }
        }
        BlockPos objective = null;
        Identifier block = null, structure = null, site = Identifier.withDefaultNamespace("polished_andesite");
        if (preset.objective() != null) {
            final RandomSource r = RandomSource.create(world.seed ^ 0x5EEDL);
            final double angle = r.nextDouble() * Math.PI * 2;
            final int d = Math.max(16, preset.objective().distance());
            objective = new BlockPos((int) Math.round(Math.cos(angle) * d), 0, (int) Math.round(Math.sin(angle) * d));
            block = preset.objective().block();
            structure = preset.objective().structure();
            site = preset.objective().siteBlock();
        }
        return new PortalChunkGenerator.WorldPlan(world.seed, preset.richness(), List.copyOf(ores), mobs, preset.replaceMobs(),
                objective, block, structure, site, preset.loot());
    }

    /** One biome gives a fixed source; more give a multi noise source with each biome at its own temperature and humidity. */
    public static BiomeSource biomes(MinecraftServer server, WorldPreset preset, long seed) {
        final HolderLookup.RegistryLookup<Biome> lookup = server.registryAccess().lookupOrThrow(Registries.BIOME);
        final Set<Holder<Biome>> found = new LinkedHashSet<>();
        for (String s : preset.biomes()) {
            if (s.startsWith("#")) {
                final Identifier tag = Identifier.tryParse(s.substring(1));
                if (tag != null) lookup.get(TagKey.create(Registries.BIOME, tag)).ifPresent(set -> set.forEach(found::add));
            } else {
                final Identifier id = Identifier.tryParse(s);
                if (id != null) lookup.get(ResourceKey.create(Registries.BIOME, id)).ifPresent(found::add);
            }
        }
        if (found.isEmpty()) found.add(lookup.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS));
        if (found.size() == 1) return new FixedBiomeSource(found.iterator().next());
        final RandomSource r = RandomSource.create(seed ^ 0xB10BEL);
        final List<Pair<Climate.ParameterPoint, Holder<Biome>>> list = new ArrayList<>();
        final Climate.Parameter any = Climate.Parameter.span(-2.0F, 2.0F);
        int i = 0;
        final int n = found.size();
        for (Holder<Biome> b : found) {
            final float t = -1.0F + 2.0F * (i + 0.5F) / n;
            final float h = r.nextFloat() * 2.0F - 1.0F;
            list.add(Pair.of(new Climate.ParameterPoint(Climate.Parameter.point(t), Climate.Parameter.point(h), any, any, any, any, 0L), b));
            i++;
        }
        return MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(list));
    }

    // --- reflection on MinecraftServer ---

    @SuppressWarnings("unchecked")
    private static Map<ResourceKey<Level>, ServerLevel> levels(MinecraftServer server) {
        try {
            if (levelsField == null) levelsField = field("levels");
            return (Map<ResourceKey<Level>, ServerLevel>) levelsField.get(server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Simple Machines cannot reach MinecraftServer.levels", e);
        }
    }

    private static Executor executor(MinecraftServer server) {
        try {
            if (executorField == null) executorField = field("executor");
            return (Executor) executorField.get(server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Simple Machines cannot reach MinecraftServer.executor", e);
        }
    }

    private static LevelStorageSource.LevelStorageAccess storage(MinecraftServer server) {
        try {
            if (storageField == null) storageField = field("storageSource");
            return (LevelStorageSource.LevelStorageAccess) storageField.get(server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Simple Machines cannot reach MinecraftServer.storageSource", e);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        final Field f = MinecraftServer.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
