package com.workshopcraft.simplemachines.ship.client;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.ship.Ship;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/** 1.21.1 has no select item models: the finish kit picks its texture through a "finish" override predicate instead. */
public final class FinishModel {
    private FinishModel() {}

    public static void register() {
        ItemProperties.register(Ship.finishKit, SimpleMachines.id("finish"), (stack, level, entity, seed) -> {
            final ResourceLocation id = stack.get(Ship.MACHINE_FINISH);
            if (id == null) return 0F;
            return switch (id.toString()) {
                case "simplemachines:ancient" -> 0.25F;
                case "simplemachines:industrial" -> 0.5F;
                case "simplemachines:clean" -> 0.75F;
                default -> 0F;
            };
        });
    }
}
