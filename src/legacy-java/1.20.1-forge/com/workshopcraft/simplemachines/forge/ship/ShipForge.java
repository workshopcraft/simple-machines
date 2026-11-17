package com.workshopcraft.simplemachines.forge.ship;

import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.ShipCommands;
import com.workshopcraft.simplemachines.ship.ShipEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/** Forge 1.20.1 side of the 2.3 ship blocks. */
public final class ShipForge {
    private ShipForge() {}

    public static void init(IEventBus modBus) {
        Ship.init();
        // components are NBT-backed here and only need their names
        Ship.registerComponents((id, t) -> t.bind(id.getPath()));
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.BLOCK, h -> Ship.registerBlocks(h::register));
            e.register(Registries.ITEM, h -> Ship.registerItems(h::register));
            e.register(Registries.CREATIVE_MODE_TAB, h -> Ship.registerTabs(h::register));
            e.register(Registries.BLOCK_ENTITY_TYPE, h -> Ship.registerBlockEntities(h::register));
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ShipCommands.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) ShipEvents.onJoin(p);
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) ShipEvents.onServerTick(e.getServer());
        });
    }
}
