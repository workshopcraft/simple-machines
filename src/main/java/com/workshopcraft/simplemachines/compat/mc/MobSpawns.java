package com.workshopcraft.simplemachines.compat.mc;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;

/** Mob spawn data whose constructor changed shape. */
public final class MobSpawns {
    private MobSpawns() {}

    /** A spawn entry with a min/max group size. 26.3 folded the two counts into an IntProvider. */
    public static MobSpawnSettings.SpawnerData spawner(EntityType<?> type, int min, int max) {
        //? if >=26.3 {
        /*return new MobSpawnSettings.SpawnerData(type, UniformInt.of(min, max));
        *///?} else if >=1.21.2 {
        return new MobSpawnSettings.SpawnerData(type, min, max);
        //?} else {
        /*return new MobSpawnSettings.SpawnerData(type, 1, min, max);
        *///?}
    }
}
