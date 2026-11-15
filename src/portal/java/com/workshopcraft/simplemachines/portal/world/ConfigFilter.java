package com.workshopcraft.simplemachines.portal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.ServerConfig;
import com.workshopcraft.simplemachines.portal.Portal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
//? if <26.3
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** simplemachines:config_enabled: places the feature only while a server config key is true, e.g. portal_titanium_overworld. */
//? if >=26.3 {
/*public class ConfigFilter implements PlacementFilter {
*///?} else {
public class ConfigFilter extends PlacementFilter {
//?}
    public static final MapCodec<ConfigFilter> CODEC = Codec.STRING.fieldOf("key").xmap(ConfigFilter::new, f -> f.key);
    private final String key;

    public ConfigFilter(String key) {
        this.key = key;
    }

    //? if >=26.3 {
    /*@Override
    public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
    *///?} else {
    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
    //?}
        final MinecraftServer server = context.getLevel().getServer();
        return server == null || ServerConfig.bool(server, key);
    }

    //? if >=26.3 {
    /*@Override
    public MapCodec<? extends PlacementFilter> codec() {
        return CODEC;
    }
    *///?} else {
    @Override
    public PlacementModifierType<?> type() {
        return Portal.CONFIG_FILTER;
    }
    //?}
}
