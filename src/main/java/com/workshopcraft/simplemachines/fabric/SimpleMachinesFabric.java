package com.workshopcraft.simplemachines.fabric;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.item.PoweredToolItem;
import net.minecraft.world.item.Item;
import com.workshopcraft.simplemachines.machine.EnergyBuffer;
import com.workshopcraft.simplemachines.assembly.Assembly;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import com.workshopcraft.simplemachines.recipe.KeepToolRecipe;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.workshopcraft.simplemachines.recipe.RecipeUnlocks;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import team.reborn.energy.api.EnergyStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import com.workshopcraft.simplemachines.machine.ChargerBlockEntity;
import com.workshopcraft.simplemachines.machine.BatteryBlockEntity;
import com.workshopcraft.simplemachines.assembly.Core;
import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import com.workshopcraft.simplemachines.pipe.Transfer;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.BiConsumer;

public class SimpleMachinesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SimpleMachines.registerComponents(direct(BuiltInRegistries.DATA_COMPONENT_TYPE));
        SimpleMachines.registerSounds(direct(BuiltInRegistries.SOUND_EVENT));
        SimpleMachines.registerParticles(direct(BuiltInRegistries.PARTICLE_TYPE));
        SimpleMachines.registerBlocks(direct(BuiltInRegistries.BLOCK));
        SimpleMachines.registerItems(direct(BuiltInRegistries.ITEM));
        SimpleMachines.registerTabs(direct(BuiltInRegistries.CREATIVE_MODE_TAB));
        SimpleMachines.registerTicketTypes(direct(BuiltInRegistries.TICKET_TYPE));
        SimpleMachines.registerBlockEntities(direct(BuiltInRegistries.BLOCK_ENTITY_TYPE));
        SimpleMachines.registerMenus(direct(BuiltInRegistries.MENU));
        SimpleMachines.registerRecipeTypes(direct(BuiltInRegistries.RECIPE_TYPE));
        SimpleMachines.registerRecipeSerializers(direct(BuiltInRegistries.RECIPE_SERIALIZER));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.TYPE, com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.TYPE,
                (payload, context) -> com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.openWorn(context.player()));
        // Fabric only syncs opted-in serializers to the client; JEI and EMI need the manual recipe
        RecipeSynchronization.synchronizeRecipeSerializer(KeepToolRecipe.SERIALIZER);
        RecipeSynchronization.synchronizeRecipeSerializer(com.workshopcraft.simplemachines.recipe.QuarryAssemblyRecipe.SERIALIZER);
        for (var serializer : java.util.List.of(SimpleMachines.ALLOYING_SERIALIZER, SimpleMachines.EXTRUDING_SERIALIZER,
                SimpleMachines.ROLLING_SERIALIZER, SimpleMachines.CRUSHING_SERIALIZER, SimpleMachines.SAWING_SERIALIZER,
                SimpleMachines.COMPRESSING_SERIALIZER)) {
            RecipeSynchronization.synchronizeRecipeSerializer(serializer);
        }
        for (BlockEntityType<? extends WorkBlockEntity> type : SimpleMachines.WORK_ENTITIES) work(type);
        ItemStorage.SIDED.registerForBlockEntity((be, side) -> ContainerStorage.of(be, side), SimpleMachines.alloyFurnaceEntity);
        ItemStorage.SIDED.registerForBlockEntity((be, side) -> ContainerStorage.of(be, side), SimpleMachines.extruderEntity);
        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity((be, side) -> new FabricTank(be), SimpleMachines.tankEntity);
        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntities((be, side) ->
                be instanceof com.workshopcraft.simplemachines.pipe.FluidStore store
                        ? be instanceof WorkBlockEntity w && !w.exposed() ? Storage.empty() : new FabricTank(store) : null,
                SimpleMachines.MACHINE_ENTITIES.get(Core.LAVA), SimpleMachines.MACHINE_ENTITIES.get(Core.TANK));
        //? if <26.3 {
        net.fabricmc.fabric.api.registry.FuelValueEvents.BUILD.register((builder, context) -> builder.add(SimpleMachines.biofuelItem, 2400));
        //?}
        EnergyStorage.SIDED.registerForBlocks((level, pos, state, be, side) ->
                level instanceof ServerLevel server ? new CableEnergyStorage(server, pos, side) : null, SimpleMachines.CABLES.toArray(new net.minecraft.world.level.block.Block[0]));
        EnergyStorage.SIDED.registerForBlockEntity((be, side) -> new BufferEnergyStorage(be.input(side), true, false), SimpleMachines.transformerEntity);
        EnergyStorage.SIDED.registerForBlockEntity((be, side) -> new BufferEnergyStorage(be.energy, false, true), SimpleMachines.solarEntity);
        EnergyStorage.SIDED.registerForBlockEntity((be, side) -> new BufferEnergyStorage(be.energy, false, true), SimpleMachines.windEntity);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(com.workshopcraft.simplemachines.entangled.ChunkLoaders::onServerStarted);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server ->
                com.workshopcraft.simplemachines.entangled.ChunkLoaders.get(server).tick(server));
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                Assembly.screwdriver(level, hit.getBlockPos(), player, player.getItemInHand(hand), hit) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            RecipeUnlocks.onLogin(handler.player);
            com.workshopcraft.simplemachines.entangled.ChunkLoaders.onJoin(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.workshopcraft.simplemachines.entangled.ChunkLoaders.onLeave(handler.player));
        FabricTransfer.install();
        ChargerBlockEntity.itemEnergy = new ChargerBlockEntity.ItemEnergy() {
            @Override
            public boolean chargeable(ItemStack stack) {
                final EnergyStorage s = EnergyStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
                return s != null && s.supportsInsertion();
            }

            @Override
            public int insert(Container container, int slot, int amount) {
                final EnergyStorage s = item(container, slot);
                if (s == null) return 0;
                try (Transaction tx = Transaction.openOuter()) {
                    final long n = s.insert(amount, tx);
                    tx.commit();
                    return (int) n;
                }
            }

            @Override
            public boolean full(Container container, int slot) {
                final EnergyStorage s = item(container, slot);
                return s == null || !s.supportsInsertion() || s.getAmount() >= s.getCapacity();
            }

            @Override
            public int charge(Container container, int slot) {
                final EnergyStorage s = item(container, slot);
                return s == null || s.getCapacity() <= 0 ? 0 : (int) (1000 * s.getAmount() / s.getCapacity());
            }

            private EnergyStorage item(Container container, int slot) {
                return EnergyStorage.ITEM.find(container.getItem(slot), ContainerItemContext.ofSingleSlot(ContainerStorage.of(container, null).getSlot(slot)));
            }
        };
        EnergyStorage.ITEM.registerForItems((stack, ctx) -> new ItemEnergyStorage(ctx, BatteryBlockEntity.itemCapacity(stack), BatteryBlockEntity.RATE),
                SimpleMachines.MACHINES.get(Core.BATTERY).asItem());
        EnergyStorage.ITEM.registerForItems((stack, ctx) -> new ItemEnergyStorage(ctx,
                com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.CAPACITY, PoweredToolItem.RATE), SimpleMachines.entangledBackpack);
        EnergyStorage.ITEM.registerForItems((stack, ctx) -> new ItemEnergyStorage(ctx, PoweredToolItem.capacity(stack), PoweredToolItem.RATE),
                SimpleMachines.TOOLS.toArray(new Item[0]));
        EnergyBuffer.lookup = (level, pos, side) -> EnergyStorage.SIDED.find(level, pos, side) != null;
        EnergyBuffer.pusher = (level, pos, side, amount) -> {
            final EnergyStorage s = EnergyStorage.SIDED.find(level, pos, side);
            if (s == null || !s.supportsInsertion()) return 0;
            try (Transaction tx = Transaction.openOuter()) {
                final long n = s.insert(amount, tx);
                tx.commit();
                return (int) n;
            }
        };
        //? if ship {
        com.workshopcraft.simplemachines.fabric.ship.ShipFabric.init();
        //?}
    }

    private static <T extends WorkBlockEntity> void work(BlockEntityType<T> type) {
        ItemStorage.SIDED.registerForBlockEntity((be, side) -> be.exposed() ? ContainerStorage.of(be, side) : Storage.empty(), type);
        EnergyStorage.SIDED.registerForBlockEntity((be, side) ->
                be.exposed() ? new BufferEnergyStorage(be.energy(), be.energyIn(side), be.energyOut(side)) : null, type);
    }

    private static <T> BiConsumer<Identifier, T> direct(Registry<T> registry) {
        return (id, value) -> Registry.register(registry, id, value);
    }
}
