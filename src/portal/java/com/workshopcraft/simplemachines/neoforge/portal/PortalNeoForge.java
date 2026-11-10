package com.workshopcraft.simplemachines.neoforge.portal;

import com.workshopcraft.simplemachines.neoforge.BufferEnergyHandler;
import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.PortalCommands;
import com.workshopcraft.simplemachines.portal.PortalEvents;
import com.workshopcraft.simplemachines.portal.PortalPlatform;
import com.workshopcraft.simplemachines.portal.item.MobScannerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

import java.util.OptionalLong;

/**
 * NeoForge side of the portal core. Runtime worlds: NeoForge ticks a cached level array, so markWorldsDirty
 * is called after every change to the level map, and LevelEvent.Load and Unload are posted like vanilla levels get.
 * The LevelStem gets NeoForge's seed override so its structure check uses the world's own seed.
 */
public final class PortalNeoForge {
    private PortalNeoForge() {}

    public static void init(IEventBus modBus) {
        Portal.init();
        PortalPlatform.worldsChanged = server -> server.markWorldsDirty();
        PortalPlatform.loaded = level -> NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        PortalPlatform.unloaded = level -> NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        PortalPlatform.stem = (type, generator, seed) -> new LevelStem(type, generator, OptionalLong.of(seed));
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.DATA_COMPONENT_TYPE, h -> Portal.registerComponents(h::register));
            e.register(Registries.BLOCK, h -> Portal.registerBlocks(h::register));
            e.register(Registries.ITEM, h -> Portal.registerItems(h::register));
            e.register(Registries.CREATIVE_MODE_TAB, h -> Portal.registerTabs(h::register));
            e.register(Registries.BLOCK_ENTITY_TYPE, h -> Portal.registerBlockEntities(h::register));
            e.register(Registries.MENU, h -> Portal.registerMenus(h::register));
            e.register(Registries.CHUNK_GENERATOR, h -> Portal.registerChunkGenerators(h::register));
            e.register(Registries.PLACEMENT_MODIFIER_TYPE, h -> Portal.registerPlacementModifiers(h::register));
        });
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.Energy.BLOCK, Portal.GATE_ENTITY, (be, side) -> new BufferEnergyHandler(be.energy(), true, false) {
                // HV only: anything lower is refused and browns the gate out
                @Override
                public int insert(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                    return amount > 0 && !be.acceptsFrom(side) ? 0 : super.insert(amount, tx);
                }
            });
            e.registerBlockEntity(Capabilities.Energy.BLOCK, Portal.RESEARCH_ENTITY, (be, side) -> new BufferEnergyHandler(be.energy(), true, false));
            e.registerBlockEntity(Capabilities.Item.BLOCK, Portal.GATE_ENTITY, WorldlyContainerWrapper::new);
            e.registerBlockEntity(Capabilities.Item.BLOCK, Portal.RESEARCH_ENTITY, WorldlyContainerWrapper::new);
            e.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, SimpleMachines.ENERGY,
                    MobScannerItem.CAPACITY, MobScannerItem.RATE), Portal.mobScanner);
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> PortalCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> PortalEvents.serverTickEnd(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> PortalEvents.serverStarted(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> PortalEvents.serverStopped(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) PortalEvents.join(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) PortalEvents.leave(p);
        });
    }
}
