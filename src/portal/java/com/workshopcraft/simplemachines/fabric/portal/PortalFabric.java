package com.workshopcraft.simplemachines.fabric.portal;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.fabric.BufferEnergyStorage;
import com.workshopcraft.simplemachines.fabric.ItemEnergyStorage;
import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.PortalCommands;
import com.workshopcraft.simplemachines.portal.PortalEvents;
import com.workshopcraft.simplemachines.portal.PortalPlatform;
import com.workshopcraft.simplemachines.portal.item.MobScannerItem;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import team.reborn.energy.api.EnergyStorage;

import java.util.function.BiConsumer;

/**
 * Fabric side of the portal core. Runtime worlds: Fabric ticks MinecraftServer.levels directly, so nothing needs
 * marking; ServerLevelEvents.LOAD and UNLOAD are fired for each portal world like Fabric fires them for vanilla levels.
 */
public final class PortalFabric {
    private PortalFabric() {}

    public static void init() {
        Portal.init();
        PortalPlatform.loaded = level -> ServerLevelEvents.LOAD.invoker().onLevelLoad(level.getServer(), level);
        PortalPlatform.unloaded = level -> ServerLevelEvents.UNLOAD.invoker().onLevelUnload(level.getServer(), level);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
                com.workshopcraft.simplemachines.portal.RiftHudPayload.TYPE, com.workshopcraft.simplemachines.portal.RiftHudPayload.CODEC);
        PortalPlatform.sendHud = (player, payload) -> {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, payload.type())) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        };
        Portal.registerComponents(direct(BuiltInRegistries.DATA_COMPONENT_TYPE));
        Portal.registerBlocks(direct(BuiltInRegistries.BLOCK));
        Portal.registerItems(direct(BuiltInRegistries.ITEM));
        Portal.registerTabs(direct(BuiltInRegistries.CREATIVE_MODE_TAB));
        Portal.registerBlockEntities(direct(BuiltInRegistries.BLOCK_ENTITY_TYPE));
        Portal.registerMenus(direct(BuiltInRegistries.MENU));
        Portal.registerTicketTypes(direct(BuiltInRegistries.TICKET_TYPE));
        Portal.registerChunkGenerators(direct(BuiltInRegistries.CHUNK_GENERATOR));
        Portal.registerPlacementModifiers(direct(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE));
        EnergyStorage.SIDED.registerForBlockEntity((be, side) -> new BufferEnergyStorage(be.energy(), true, false) {
            // HV only: anything lower is refused and browns the gate out
            @Override
            public long insert(long amount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx) {
                return amount > 0 && !be.acceptsFrom(side) ? 0 : super.insert(amount, tx);
            }
        }, Portal.GATE_ENTITY);
        EnergyStorage.SIDED.registerForBlockEntity((be, side) -> new BufferEnergyStorage(be.energy(), true, false), Portal.RESEARCH_ENTITY);
        ItemStorage.SIDED.registerForBlockEntity((be, side) -> ContainerStorage.of(be, side), Portal.GATE_ENTITY);
        ItemStorage.SIDED.registerForBlockEntity((be, side) -> ContainerStorage.of(be, side), Portal.RESEARCH_ENTITY);
        EnergyStorage.ITEM.registerForItems((stack, ctx) -> new ItemEnergyStorage(ctx, MobScannerItem.CAPACITY, MobScannerItem.RATE), Portal.mobScanner);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES,
                ResourceKey.create(Registries.PLACED_FEATURE, SimpleMachines.id("ore_titanium")));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> PortalCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(PortalEvents::serverTickEnd);
        ServerLifecycleEvents.SERVER_STARTED.register(PortalEvents::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(PortalEvents::serverStopped);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PortalEvents.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PortalEvents.leave(handler.player));
    }

    private static <T> BiConsumer<Identifier, T> direct(Registry<T> registry) {
        return (id, value) -> Registry.register(registry, id, value);
    }
}
