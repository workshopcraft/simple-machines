package com.workshopcraft.simplemachines.portal.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.workshopcraft.simplemachines.compat.mc.MobSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
//? if >=1.21.2 {
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
//?} else
/*import net.minecraft.util.random.WeightedRandomList;*/
//? if >=1.20.5
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vanilla noise terrain with the preset's biomes, plus extra ores scaled by richness, the preset's mobs and the objective site.
 * Vanilla structures are left out: the per-world seed does not reach vanilla's structure check on every loader.
 */
public class PortalChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<PortalChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
    ).apply(i, (b, s) -> new PortalChunkGenerator(b, s, WorldPlan.EMPTY)));

    private final WorldPlan plan;
    //? if >=1.21.2 {
    private final Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> mobs = new EnumMap<>(MobCategory.class);
    //?} else
    /*private final Map<MobCategory, List<MobSpawnSettings.SpawnerData>> mobs = new EnumMap<>(MobCategory.class);*/

    public PortalChunkGenerator(BiomeSource biomes, Holder<NoiseGeneratorSettings> settings, WorldPlan plan) {
        super(biomes, settings);
        this.plan = plan;
        for (Map.Entry<MobCategory, List<WorldPreset.Mob>> e : plan.mobs().entrySet()) {
            //? if >=1.21.2 {
            final List<Weighted<MobSpawnSettings.SpawnerData>> list = new ArrayList<>();
            for (WorldPreset.Mob m : e.getValue()) {
                final Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(m.type());
                type.ifPresent(t -> list.add(new Weighted<>(MobSpawns.spawner(t, Math.max(1, m.min()), Math.max(m.min(), m.max())),
                        Math.max(1, m.weight()))));
            }
            //?} else {
            /*final List<MobSpawnSettings.SpawnerData> list = new ArrayList<>();
            for (WorldPreset.Mob m : e.getValue()) {
                final Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(m.type());
                type.ifPresent(t -> list.add(new MobSpawnSettings.SpawnerData(t, Math.max(1, m.weight()), Math.max(1, m.min()), Math.max(m.min(), m.max()))));
            }
            *///?}
            mobs.put(e.getKey(), list);
        }
    }

    public WorldPlan plan() {
        return plan;
    }

    //? if >=1.20.5 {
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
    //?} else {
    /*@Override
    public com.mojang.serialization.Codec<? extends ChunkGenerator> codec() {
        return CODEC.codec();
    }
    *///?}

    @Override
    //? if >=1.21.2 {
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
                                 ChunkAccess centerChunk, StructureTemplateManager templates, ResourceKey<Level> level) {
    //?} else {
    /*public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
                                 ChunkAccess centerChunk, StructureTemplateManager templates) {
    *///?}
        // no vanilla structures in portal worlds; the objective site is placed in applyBiomeDecoration
    }

    //? if >=26.3 {
    /*@Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Level level, StructureManager structureManager, MobCategory category, BlockPos pos) {
        final java.util.function.Supplier<WeightedList<MobSpawnSettings.SpawnerData>> vanilla = () -> super.getMobsAt(level, structureManager, category, pos);
    *///?} else if >=1.21.2 {
    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        final java.util.function.Supplier<WeightedList<MobSpawnSettings.SpawnerData>> vanilla = () -> super.getMobsAt(biome, structureManager, category, pos);
    //?}
    //? if >=1.21.2 {
        final List<Weighted<MobSpawnSettings.SpawnerData>> extra = mobs.get(category);
        if (extra == null || extra.isEmpty()) return plan.replaceMobs() ? WeightedList.of() : vanilla.get();
        if (plan.replaceMobs()) return WeightedList.of(extra);
        final List<Weighted<MobSpawnSettings.SpawnerData>> all = new ArrayList<>(vanilla.get().unwrap());
        all.addAll(extra);
        return WeightedList.of(all);
    //?} else {
    /*@Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        final List<MobSpawnSettings.SpawnerData> extra = mobs.get(category);
        if (extra == null || extra.isEmpty()) return plan.replaceMobs() ? WeightedRandomList.create() : super.getMobsAt(biome, structureManager, category, pos);
        if (plan.replaceMobs()) return WeightedRandomList.create(extra);
        final List<MobSpawnSettings.SpawnerData> all = new ArrayList<>(super.getMobsAt(biome, structureManager, category, pos).unwrap());
        all.addAll(extra);
        return WeightedRandomList.create(all);
    *///?}
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        final ChunkPos cp = chunk.getPos();
        final RandomSource random = RandomSource.create(plan.seed() ^ (cp.pack() * 0x9E3779B97F4A7C15L));
        for (WorldPreset.Ore ore : plan.ores()) placeOre(level, chunk, random, ore);
        final BlockPos site = plan.objective();
        if (site != null && plan.objectiveBlock() != null && (site.getX() >> 4) == cp.x() && (site.getZ() >> 4) == cp.z()) {
            placeSite(level, chunk, random, site);
        }
    }

    private void placeOre(WorldGenLevel level, ChunkAccess chunk, RandomSource random, WorldPreset.Ore ore) {
        final Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ore.block());
        if (block.isEmpty()) return;
        final BlockState state = block.get().defaultBlockState();
        final float want = ore.perChunk() * plan.richness();
        int count = (int) want;
        if (random.nextFloat() < want - count) count++;
        final int minY = Math.max(level.getMinY() + 1, ore.minY());
        final int maxY = Math.min(level.getMaxY() - 1, ore.maxY());
        if (maxY < minY) return;
        final int x0 = chunk.getPos().getMinBlockX();
        final int z0 = chunk.getPos().getMinBlockZ();
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int n = 0; n < count; n++) {
            int x = 0, y = minY, z = 0;
            boolean found = false;
            // Islands are mostly air: keep trying until a replaceable stone is found, or give up this vein.
            for (int attempt = 0; attempt < 32; attempt++) {
                x = random.nextInt(16);
                z = random.nextInt(16);
                y = minY + random.nextInt(maxY - minY + 1);
                p.set(x0 + x, y, z0 + z);
                if (replaceable(level.getBlockState(p))) {
                    found = true;
                    break;
                }
            }
            if (!found) continue;
            for (int k = 0; k < Math.max(1, ore.size()); k++) {
                p.set(x0 + x, y, z0 + z);
                if (replaceable(level.getBlockState(p))) level.setBlock(p, state, 2);
                x = Math.max(0, Math.min(15, x + random.nextInt(3) - 1));
                y = Math.max(minY, Math.min(maxY, y + random.nextInt(3) - 1));
                z = Math.max(0, Math.min(15, z + random.nextInt(3) - 1));
            }
        }
    }

    private static boolean replaceable(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(Blocks.END_STONE);
    }

    /** Finds the site height: the surface, or the first roomy air pocket in worlds with a ceiling. */
    public static int siteY(WorldGenLevel level, int x, int z) {
        if (!level.dimensionType().hasCeiling()) {
            final int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            return y <= level.getMinY() + 1 ? 64 : y;
        }
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = level.getMinY() + 24; y < level.getMinY() + 110; y++) {
            p.set(x, y - 1, z);
            final BlockState floor = level.getBlockState(p);
            if (!floor.isSolid() || !floor.getFluidState().isEmpty()) continue;
            boolean room = true;
            for (int h = 0; h < 4 && room; h++) {
                final BlockState s = level.getBlockState(p.set(x, y + h, z));
                room = s.isAir() && s.getFluidState().isEmpty();
            }
            if (room) return y;
        }
        return level.getMinY() + 40;
    }

    private void placeSite(WorldGenLevel level, ChunkAccess chunk, RandomSource random, BlockPos column) {
        final int y = siteY(level, column.getX(), column.getZ());
        final BlockPos centre = new BlockPos(column.getX(), y, column.getZ());
        final Block core = BuiltInRegistries.BLOCK.getValue(plan.objectiveBlock());
        if (plan.structure() != null && level.getServer() != null) {
            // 26.3 renamed ServerLevel#getStructureManager to getStructureTemplateManager
            //? if >=26.3 {
            /*final Optional<StructureTemplate> t = level.getLevel().getStructureTemplateManager().get(plan.structure());
            *///?} else {
            final Optional<StructureTemplate> t = level.getLevel().getStructureManager().get(plan.structure());
            //?}
            if (t.isPresent()) {
                final var size = t.get().getSize();
                final BlockPos origin = centre.offset(-size.getX() / 2, 0, -size.getZ() / 2);
                t.get().placeInWorld(level, origin, origin, new StructurePlaceSettings(), random, 2);
                return;
            }
        }
        final BlockState floor = BuiltInRegistries.BLOCK.getValue(plan.siteBlock()).defaultBlockState();
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(p.set(centre.getX() + dx, y - 1, centre.getZ() + dz), floor, 2);
                for (int dy = 0; dy < 4; dy++) level.setBlock(p.set(centre.getX() + dx, y + dy, centre.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
                    for (int dy = 0; dy < 3; dy++) level.setBlock(p.set(centre.getX() + dx, y + dy, centre.getZ() + dz), floor, 2);
                }
            }
        }
        level.setBlock(centre, core.defaultBlockState(), 2);
        if (plan.loot() != null) {
            final BlockPos chest = centre.offset(2, 0, 0);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
            //? if >=1.20.5 {
            RandomizableContainer.setBlockEntityLootTable(level, random, chest, ResourceKey.create(Registries.LOOT_TABLE, plan.loot()));
            //?} else {
            /*net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.setLootTable(level, random, chest, plan.loot());
            *///?}
        }
    }

    /** Everything a portal world's generator needs, worked out once from the preset, card and research. */
    public record WorldPlan(long seed, float richness, List<WorldPreset.Ore> ores, Map<MobCategory, List<WorldPreset.Mob>> mobs, boolean replaceMobs,
                            @Nullable BlockPos objective, @Nullable Identifier objectiveBlock, @Nullable Identifier structure,
                            Identifier siteBlock, @Nullable Identifier loot) {
        public static final WorldPlan EMPTY = new WorldPlan(0, 1, List.of(), Map.of(), false, null, null, null,
                Identifier.withDefaultNamespace("stone"), null);
    }

    /** For use by ServerLevel helpers that need the plan of a running world. */
    public static @Nullable WorldPlan planOf(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof PortalChunkGenerator g ? g.plan : null;
    }
}
