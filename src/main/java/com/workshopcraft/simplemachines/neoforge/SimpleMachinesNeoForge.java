package com.workshopcraft.simplemachines.neoforge;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.item.PoweredToolItem;
import net.minecraft.world.item.Item;
import com.workshopcraft.simplemachines.machine.EnergyBuffer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import com.workshopcraft.simplemachines.machine.ChargerBlockEntity;
import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import com.workshopcraft.simplemachines.pipe.Transfer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import com.workshopcraft.simplemachines.assembly.Assembly;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import com.workshopcraft.simplemachines.recipe.RecipeUnlocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import com.workshopcraft.simplemachines.machine.BatteryBlockEntity;
import com.workshopcraft.simplemachines.assembly.Core;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

@Mod(SimpleMachines.MODID)
public class SimpleMachinesNeoForge {
    public SimpleMachinesNeoForge(IEventBus modBus) {
        modBus.addListener(SimpleMachinesNeoForge::onRegister);
        modBus.addListener(SimpleMachinesNeoForge::onCapabilities);
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) -> e.registrar("1").playToServer(
                com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.TYPE, com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer p) com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.openWorn(p);
                })));
        NeoForge.EVENT_BUS.addListener(SimpleMachinesNeoForge::onUseBlock);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) ->
                com.workshopcraft.simplemachines.entangled.ChunkLoaders.onServerStarted(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) ->
                com.workshopcraft.simplemachines.entangled.ChunkLoaders.get(e.getServer()).tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) com.workshopcraft.simplemachines.entangled.ChunkLoaders.onLeave(p);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent e) -> e.sendRecipes(SimpleMachines.ALLOYING,
                SimpleMachines.EXTRUDING, SimpleMachines.ROLLING, SimpleMachines.CRUSHING, SimpleMachines.SAWING, SimpleMachines.COMPRESSING));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                RecipeUnlocks.onLogin(p);
                com.workshopcraft.simplemachines.entangled.ChunkLoaders.onJoin(p);
            }
        });
        EnergyBuffer.pusher = (level, pos, side, amount) -> {
            final EnergyHandler h = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
            if (h == null) return 0;
            try (Transaction tx = Transaction.openRoot()) {
                final int n = h.insert(amount, tx);
                tx.commit();
                return n;
            }
        };
        NeoTransfer.install();
        ChargerBlockEntity.itemEnergy = new ChargerBlockEntity.ItemEnergy() {
            @Override
            public boolean chargeable(ItemStack stack) {
                if (stack.isEmpty()) return false;
                final EnergyHandler h = ItemAccess.forStack(stack.copy()).getCapability(Capabilities.Energy.ITEM);
                return h != null && h.getCapacityAsLong() > 0;
            }

            @Override
            public int insert(Container container, int slot, int amount) {
                final EnergyHandler h = item(container, slot);
                if (h == null) return 0;
                try (Transaction tx = Transaction.openRoot()) {
                    final int n = h.insert(amount, tx);
                    tx.commit();
                    return n;
                }
            }

            @Override
            public boolean full(Container container, int slot) {
                final EnergyHandler h = item(container, slot);
                if (h == null || h.getAmountAsLong() >= h.getCapacityAsLong()) return true;
                try (Transaction tx = Transaction.openRoot()) {
                    return h.insert(1, tx) == 0;
                }
            }

            @Override
            public int charge(Container container, int slot) {
                final EnergyHandler h = item(container, slot);
                return h == null || h.getCapacityAsLong() <= 0 ? 0 : (int) (1000 * h.getAmountAsLong() / h.getCapacityAsLong());
            }
        };
        EnergyBuffer.lookup = (level, pos, side) -> level.getCapability(Capabilities.Energy.BLOCK, pos, side) != null;
        //? if ship {
        com.workshopcraft.simplemachines.neoforge.ship.ShipNeoForge.init(modBus);
        //?}
    }

    private static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (Assembly.screwdriver(e.getLevel(), e.getPos(), e.getEntity(), e.getItemStack(), e.getHitVec())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void onRegister(RegisterEvent e) {
        e.register(Registries.DATA_COMPONENT_TYPE, h -> SimpleMachines.registerComponents(h::register));
        e.register(Registries.SOUND_EVENT, h -> SimpleMachines.registerSounds(h::register));
        e.register(Registries.PARTICLE_TYPE, h -> SimpleMachines.registerParticles(h::register));
        e.register(Registries.BLOCK, h -> SimpleMachines.registerBlocks(h::register));
        e.register(Registries.ITEM, h -> SimpleMachines.registerItems(h::register));
        e.register(Registries.CREATIVE_MODE_TAB, h -> SimpleMachines.registerTabs(h::register));
        e.register(Registries.TICKET_TYPE, h -> SimpleMachines.registerTicketTypes(h::register));
        e.register(Registries.BLOCK_ENTITY_TYPE, h -> SimpleMachines.registerBlockEntities(h::register));
        e.register(Registries.MENU, h -> SimpleMachines.registerMenus(h::register));
        e.register(Registries.RECIPE_TYPE, h -> SimpleMachines.registerRecipeTypes(h::register));
        e.register(Registries.RECIPE_SERIALIZER, h -> SimpleMachines.registerRecipeSerializers(h::register));
    }

    private static void onCapabilities(RegisterCapabilitiesEvent e) {
        for (BlockEntityType<? extends WorkBlockEntity> type : SimpleMachines.WORK_ENTITIES) work(e, type);
        e.registerBlockEntity(Capabilities.Item.BLOCK, SimpleMachines.alloyFurnaceEntity, WorldlyContainerWrapper::new);
        e.registerBlockEntity(Capabilities.Item.BLOCK, SimpleMachines.extruderEntity, WorldlyContainerWrapper::new);
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, SimpleMachines.tankEntity, (be, side) -> new NeoTank(be));
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, lavaEntity(), (be, side) -> new NeoTank(be));
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, entangledTankEntity(), (be, side) -> be.exposed() ? new NeoTank(be) : null);
        e.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) ->
                level instanceof ServerLevel server ? new CableEnergyHandler(server, pos, side) : null, SimpleMachines.CABLES.toArray(new net.minecraft.world.level.block.Block[0]));
        e.registerBlockEntity(Capabilities.Energy.BLOCK, SimpleMachines.transformerEntity, (be, side) -> new BufferEnergyHandler(be.input(side), true, false));
        e.registerBlockEntity(Capabilities.Energy.BLOCK, SimpleMachines.solarEntity, (be, side) -> new BufferEnergyHandler(be.energy, false, true));
        e.registerBlockEntity(Capabilities.Energy.BLOCK, SimpleMachines.windEntity, (be, side) -> new BufferEnergyHandler(be.energy, false, true));
        e.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, SimpleMachines.ENERGY,
                BatteryBlockEntity.itemCapacity(stack), BatteryBlockEntity.RATE), SimpleMachines.MACHINES.get(Core.BATTERY));
        e.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, SimpleMachines.ENERGY,
                PoweredToolItem.capacity(stack), PoweredToolItem.RATE), SimpleMachines.TOOLS.toArray(new Item[0]));
        e.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, SimpleMachines.ENERGY,
                com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.CAPACITY, PoweredToolItem.RATE), SimpleMachines.entangledBackpack);
    }

    private static <T extends WorkBlockEntity> void work(RegisterCapabilitiesEvent e, BlockEntityType<T> type) {
        e.registerBlockEntity(Capabilities.Item.BLOCK, type, (be, side) -> be.exposed() ? new WorldlyContainerWrapper(be, side) : null);
        e.registerBlockEntity(Capabilities.Energy.BLOCK, type, (be, side) ->
                be.exposed() ? new BufferEnergyHandler(be.energy(), be.energyIn(side), be.energyOut(side)) : null);
    }

    private static EnergyHandler item(Container container, int slot) {
        final ItemStack stack = container.getItem(slot);
        return stack.isEmpty() ? null : ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.level.block.entity.BlockEntityType<com.workshopcraft.simplemachines.machine.LavaGeneratorBlockEntity> lavaEntity() {
        return (net.minecraft.world.level.block.entity.BlockEntityType<com.workshopcraft.simplemachines.machine.LavaGeneratorBlockEntity>)
                SimpleMachines.MACHINE_ENTITIES.get(com.workshopcraft.simplemachines.assembly.Core.LAVA);
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<com.workshopcraft.simplemachines.entangled.EntangledTankBlockEntity> entangledTankEntity() {
        return (BlockEntityType<com.workshopcraft.simplemachines.entangled.EntangledTankBlockEntity>)
                SimpleMachines.MACHINE_ENTITIES.get(com.workshopcraft.simplemachines.assembly.Core.TANK);
    }
}
