package com.workshopcraft.simplemachines.client;

import com.workshopcraft.simplemachines.SimpleMachines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.function.BiConsumer;

/**
 * Newer versions pick the chunk layer from the texture alpha. 1.21.1 needs it set per block, so the ship and portal
 * blocks with see-through textures are listed here: partly transparent pixels go translucent, fully clear ones cutout.
 */
public final class LegacyLayers {
    private static final String[] CUTOUT = {"ancient_grate", "catwalk", "clean_grate", "debris", "industrial_grate", "lift_shaft",
            "ship_ladder", "sparking_wires"};
    private static final String[] TRANSLUCENT = {"ancient_porthole", "ancient_window", "clean_porthole", "clean_window", "cryopod",
            "cryopod_bed", "holo_table", "hull_glass", "industrial_porthole", "industrial_window", "portal_field", "return_rift"};

    private LegacyLayers() {}

    public static void apply(BiConsumer<Block, RenderType> set) {
        for (String id : CUTOUT) BuiltInRegistries.BLOCK.getOptional(SimpleMachines.id(id)).ifPresent(b -> set.accept(b, RenderType.cutout()));
        for (String id : TRANSLUCENT) BuiltInRegistries.BLOCK.getOptional(SimpleMachines.id(id)).ifPresent(b -> set.accept(b, RenderType.translucent()));
    }
}
