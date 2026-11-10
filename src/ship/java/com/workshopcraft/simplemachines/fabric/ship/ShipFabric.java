package com.workshopcraft.simplemachines.fabric.ship;

import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.ShipCommands;
import com.workshopcraft.simplemachines.ship.ShipEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;

/** Fabric side of the 2.3 ship blocks. */
public final class ShipFabric {
    private ShipFabric() {}

    public static void init() {
        Ship.init();
        Ship.registerComponents(direct(BuiltInRegistries.DATA_COMPONENT_TYPE));
        Ship.registerBlocks(direct(BuiltInRegistries.BLOCK));
        Ship.registerItems(direct(BuiltInRegistries.ITEM));
        Ship.registerTabs(direct(BuiltInRegistries.CREATIVE_MODE_TAB));
        Ship.registerBlockEntities(direct(BuiltInRegistries.BLOCK_ENTITY_TYPE));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> ShipCommands.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ShipEvents.onJoin(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(ShipEvents::onServerTick);
    }

    private static <T> BiConsumer<Identifier, T> direct(Registry<T> registry) {
        return (id, value) -> Registry.register(registry, id, value);
    }
}
