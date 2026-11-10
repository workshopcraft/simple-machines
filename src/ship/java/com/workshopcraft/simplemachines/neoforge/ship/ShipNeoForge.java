package com.workshopcraft.simplemachines.neoforge.ship;

import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.ShipCommands;
import com.workshopcraft.simplemachines.ship.ShipEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** NeoForge side of the 2.3 ship blocks. */
public final class ShipNeoForge {
    private ShipNeoForge() {}

    public static void init(IEventBus modBus) {
        Ship.init();
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.DATA_COMPONENT_TYPE, h -> Ship.registerComponents(h::register));
            e.register(Registries.BLOCK, h -> Ship.registerBlocks(h::register));
            e.register(Registries.ITEM, h -> Ship.registerItems(h::register));
            e.register(Registries.CREATIVE_MODE_TAB, h -> Ship.registerTabs(h::register));
            e.register(Registries.BLOCK_ENTITY_TYPE, h -> Ship.registerBlockEntities(h::register));
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ShipCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) ShipEvents.onJoin(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ShipEvents.onServerTick(e.getServer()));
    }
}
