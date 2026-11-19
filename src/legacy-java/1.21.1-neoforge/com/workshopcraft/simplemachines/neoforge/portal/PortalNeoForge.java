package com.workshopcraft.simplemachines.neoforge.portal;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.neoforge.BufferEnergyHandler;
import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.PortalCommands;
import com.workshopcraft.simplemachines.portal.PortalEvents;
import com.workshopcraft.simplemachines.portal.PortalPlatform;
import com.workshopcraft.simplemachines.portal.item.MobScannerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge 1.21.1 side of the portal core. Same as the 26.x glue with the 1.21.1 capability classes:
 * markWorldsDirty after every change to the level map, LevelEvent.Load and Unload for portal worlds.
 * 1.21.1 has no ticket type registry and no LevelStem seed override.
 */
public final class PortalNeoForge {
    /** Set by the client glue; a dedicated server never receives the countdown. */
    public static java.util.function.Consumer<com.workshopcraft.simplemachines.portal.RiftHudPayload> hudReceiver = p -> {};
    public static java.util.function.Consumer<com.workshopcraft.simplemachines.portal.PortalFxPayload> fxReceiver = p -> {};

    private PortalNeoForge() {}

    public static void init(IEventBus modBus) {
        Portal.init();
        PortalPlatform.worldsChanged = server -> server.markWorldsDirty();
        PortalPlatform.loaded = level -> NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        PortalPlatform.unloaded = level -> NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        PortalPlatform.sendHud = net.neoforged.neoforge.network.PacketDistributor::sendToPlayer;
        PortalPlatform.sendFx = net.neoforged.neoforge.network.PacketDistributor::sendToPlayer;
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) -> {
            final var r = e.registrar("1").optional();
            r.playToClient(com.workshopcraft.simplemachines.portal.RiftHudPayload.TYPE, com.workshopcraft.simplemachines.portal.RiftHudPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> hudReceiver.accept(payload)));
            r.playToClient(com.workshopcraft.simplemachines.portal.PortalFxPayload.TYPE, com.workshopcraft.simplemachines.portal.PortalFxPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> fxReceiver.accept(payload)));
        });
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.DATA_COMPONENT_TYPE, h -> Portal.registerComponents(h::register));
            e.register(Registries.BLOCK, h -> Portal.registerBlocks(h::register));
            e.register(Registries.ITEM, h -> Portal.registerItems(h::register));
            e.register(Registries.CREATIVE_MODE_TAB, h -> Portal.registerTabs(h::register));
            e.register(Registries.BLOCK_ENTITY_TYPE, h -> Portal.registerBlockEntities(h::register));
            e.register(Registries.ENTITY_TYPE, h -> Portal.registerEntities(h::register));
            e.register(Registries.MENU, h -> Portal.registerMenus(h::register));
            e.register(Registries.CHUNK_GENERATOR, h -> Portal.registerChunkGenerators(h::register));
            e.register(Registries.PLACEMENT_MODIFIER_TYPE, h -> Portal.registerPlacementModifiers(h::register));
        });
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, Portal.GATE_ENTITY, (be, side) -> new BufferEnergyHandler(be.energy(), true, false) {
                // HV only: anything lower is refused and browns the gate out
                @Override
                public int receiveEnergy(int amount, boolean simulate) {
                    return amount > 0 && !be.acceptsFrom(side) ? 0 : super.receiveEnergy(amount, simulate);
                }
            });
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, Portal.RESEARCH_ENTITY, (be, side) -> new BufferEnergyHandler(be.energy(), true, false));
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, Portal.GATE_ENTITY, (be, side) -> side == null ? new InvWrapper(be) : new SidedInvWrapper(be, side));
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, Portal.RESEARCH_ENTITY, (be, side) -> side == null ? new InvWrapper(be) : new SidedInvWrapper(be, side));
            e.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new ComponentEnergyStorage(stack, SimpleMachines.ENERGY,
                    MobScannerItem.CAPACITY, MobScannerItem.RATE, MobScannerItem.RATE), Portal.mobScanner);
        });
        modBus.addListener((InterModEnqueueEvent e) -> InterModComms.sendTo("simplebooks", "plugin",
                () -> "com.workshopcraft.simplemachines.compat.SimpleBooksCompat"));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> PortalCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> PortalEvents.serverStarting(e.getServer()));
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
