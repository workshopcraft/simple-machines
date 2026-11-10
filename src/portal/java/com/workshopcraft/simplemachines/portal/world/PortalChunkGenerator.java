package com.workshopcraft.simplemachines.portal.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
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
    private final Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> mobs = new EnumMap<>(MobCategory.class);

    public PortalChunkGenerator(BiomeSource biomes, Holder<NoiseGeneratorSettings> settings, WorldPlan plan) {
        super(biomes, settings);
        this.plan = plan;
        for (Map.Entry<MobCategory, List<WorldPreset.Mob>> e : plan.mobs().entrySet()) {
            final List<Weighted<MobSpawnSettings.SpawnerData>> list = new ArrayList<>();
            for (WorldPreset.Mob m : e.getValue()) {
                final Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(m.type());
                type.ifPresent(t -> list.add(new Weighted<>(new MobSpawnSettings.SpawnerData(t, Math.max(1, m.min()), Math.max(m.min(), m.max())),
                        Math.max(1, m.weight()))));
            }
            mobs.put(e.getKey(), list);
        }
    }

    public WorldPlan plan() {
        return plan;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
                                 ChunkAccess centerChunk, StructureTemplateManager templates, ResourceKey<Level> level) {
        // no vanilla structures in portal worlds; the objective site is placed in applyBiomeDecoration
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        final List<Weighted<MobSpawnSettings.SpawnerData>> extra = mobs.get(category);
        if (extra == null || extra.isEmpty()) return plan.replaceMobs() ? WeightedList.of() : super.getMobsAt(biome, structureManager, category, pos);
        if (plan.replaceMobs()) return WeightedList.of(extra);
        final List<Weighted<MobSpawnSettings.SpawnerData>> all = new ArrayList<>(super.getMobsAt(biome, structureManager, category, pos).unwrap());
        all.addAll(extra);
        return WeightedList.of(all);
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
        final Optional<Holder.Reference<Block>> block = BuiltInRegistries.BLOCK.get(ore.block());
        if (block.isEmpty()) return;
        final BlockState state = block.get().value().defaultBlockState();
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
            int x = random.nextInt(16), y = minY + random.nextInt(maxY - minY + 1), z = random.nextInt(16);
            for (int k = 0; k < Math.max(1, ore.size()); k++) {
                p.set(x0 + x, y, z0 + z);
                if (replaceable(level.getBlockState(p))) level.setBlock(p, state, 2);
                x = Math.clamp(x + random.nextInt(3) - 1, 0, 15);
                y = Math.clamp(y + random.nextInt(3) - 1, minY, maxY);
                z = Math.clamp(z + random.nextInt(3) - 1, 0, 15);
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
            if (!level.getBlockState(p).isSolid()) continue;
            boolean room = true;
            for (int h = 0; h < 4 && room; h++) room = level.getBlockState(p.set(x, y + h, z)).isAir();
            if (room) return y;
        }
        return level.getMinY() + 40;
    }

    private void placeSite(WorldGenLevel level, ChunkAccess chunk, RandomSource random, BlockPos column) {
        final int y = siteY(level, column.getX(), column.getZ());
        final BlockPos centre = new BlockPos(column.getX(), y, column.getZ());
        final Block core = BuiltInRegistries.BLOCK.getValue(plan.objectiveBlock());
        if (plan.structure() != null && level.getServer() != null) {
            final Optional<StructureTemplate> t = level.getLevel().getStructureManager().get(plan.structure());
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
            RandomizableContainer.setBlockEntityLootTable(level, random, chest, ResourceKey.create(Registries.LOOT_TABLE, plan.loot()));
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
