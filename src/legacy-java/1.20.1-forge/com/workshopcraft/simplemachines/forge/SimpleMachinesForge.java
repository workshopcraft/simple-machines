package com.workshopcraft.simplemachines.forge;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.assembly.Assembly;
import com.workshopcraft.simplemachines.assembly.Core;
import com.workshopcraft.simplemachines.entangled.ChunkLoaders;
import com.workshopcraft.simplemachines.entangled.EntangledBackpackItem;
import com.workshopcraft.simplemachines.entangled.EntangledTankBlockEntity;
import com.workshopcraft.simplemachines.item.PoweredToolItem;
import com.workshopcraft.simplemachines.machine.BatteryBlockEntity;
import com.workshopcraft.simplemachines.machine.ChargerBlockEntity;
import com.workshopcraft.simplemachines.machine.EnergyBuffer;
import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import com.workshopcraft.simplemachines.pipe.FluidStore;
import com.workshopcraft.simplemachines.recipe.RecipeUnlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.RegisterEvent;

import java.util.HashSet;
import java.util.Set;

@Mod(SimpleMachines.MODID)
public class SimpleMachinesForge {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(SimpleMachines.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final ResourceLocation CAP_ID = SimpleMachines.id("caps");

    public SimpleMachinesForge() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(SimpleMachinesForge::onRegister);
        CHANNEL.registerMessage(0, EntangledBackpackItem.OpenPayload.class, (p, buf) -> {
        }, buf -> EntangledBackpackItem.OpenPayload.INSTANCE, (p, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getSender() != null) EntangledBackpackItem.openWorn(ctx.get().getSender());
            });
            ctx.get().setPacketHandled(true);
        });
        MinecraftForge.EVENT_BUS.addListener(SimpleMachinesForge::onUseBlock);
        MinecraftForge.EVENT_BUS.addListener(SimpleMachinesForge::onLootLoad);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent e) -> {
            if (e.getItemStack().is(SimpleMachines.biofuelItem)) e.setBurnTime(2400);
        });
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, SimpleMachinesForge::onBlockCaps);
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, SimpleMachinesForge::onItemCaps);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> ChunkLoaders.onServerStarted(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) ChunkLoaders.get(e.getServer()).tick(e.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) ChunkLoaders.onLeave(p);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                RecipeUnlocks.onLogin(p);
                ChunkLoaders.onJoin(p);
            }
        });
        EnergyBuffer.pusher = (level, pos, side, amount) -> {
            final IEnergyStorage h = Caps.find(level, pos, side, ForgeCapabilities.ENERGY);
            return h == null ? 0 : h.receiveEnergy(amount, false);
        };
        EnergyBuffer.lookup = (level, pos, side) -> Caps.find(level, pos, side, ForgeCapabilities.ENERGY) != null;
        ForgeTransfer.install();
        ChargerBlockEntity.itemEnergy = new ChargerBlockEntity.ItemEnergy() {
            @Override
            public boolean chargeable(ItemStack stack) {
                if (stack.isEmpty()) return false;
                final IEnergyStorage h = stack.copy().getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
                return h != null && h.getMaxEnergyStored() > 0;
            }

            @Override
            public int insert(Container container, int slot, int amount) {
                final IEnergyStorage h = item(container, slot);
                return h == null ? 0 : h.receiveEnergy(amount, false);
            }

            @Override
            public boolean full(Container container, int slot) {
                final IEnergyStorage h = item(container, slot);
                return h == null || h.getEnergyStored() >= h.getMaxEnergyStored() || h.receiveEnergy(1, true) == 0;
            }

            @Override
            public int charge(Container container, int slot) {
                final IEnergyStorage h = item(container, slot);
                return h == null || h.getMaxEnergyStored() <= 0 ? 0 : (int) (1000L * h.getEnergyStored() / h.getMaxEnergyStored());
            }
        };
        com.workshopcraft.simplemachines.forge.ship.ShipForge.init(modBus);
        com.workshopcraft.simplemachines.forge.portal.PortalForge.init(modBus);
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> SimpleMachinesForgeClient.init(modBus));
    }

    private static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (Assembly.screwdriver(e.getLevel(), e.getPos(), e.getEntity(), e.getItemStack(), e.getHitVec())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void onRegister(RegisterEvent e) {
        if (e.getRegistryKey().equals(Registries.SOUND_EVENT)) {
            // the first registry event; components are NBT-backed here and only need their names
            SimpleMachines.registerComponents((id, t) -> t.bind(id.getPath()));
        }
        e.register(Registries.SOUND_EVENT, h -> SimpleMachines.registerSounds(h::register));
        e.register(Registries.PARTICLE_TYPE, h -> SimpleMachines.registerParticles(h::register));
        e.register(Registries.BLOCK, h -> SimpleMachines.registerBlocks(h::register));
        e.register(Registries.ITEM, h -> SimpleMachines.registerItems(h::register));
        e.register(Registries.CREATIVE_MODE_TAB, h -> SimpleMachines.registerTabs(h::register));
        e.register(Registries.BLOCK_ENTITY_TYPE, h -> SimpleMachines.registerBlockEntities(h::register));
        e.register(Registries.MENU, h -> SimpleMachines.registerMenus(h::register));
        e.register(Registries.RECIPE_TYPE, h -> SimpleMachines.registerRecipeTypes(h::register));
        e.register(Registries.RECIPE_SERIALIZER, h -> SimpleMachines.registerRecipeSerializers(h::register));
    }

    private static Set<BlockEntityType<?>> work;

    private static void onBlockCaps(AttachCapabilitiesEvent<BlockEntity> e) {
        final BlockEntity be = e.getObject();
        final BlockEntityType<?> type = be.getType();
        if (work == null) work = new HashSet<>(SimpleMachines.WORK_ENTITIES);
        if (work.contains(type) && be instanceof WorkBlockEntity w) {
            e.addCapability(SimpleMachines.id("items"), Caps.provider(ForgeCapabilities.ITEM_HANDLER, side -> w.exposed() ? items(w, side) : null));
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side ->
                    w.exposed() ? new BufferEnergyHandler(w.energy(), w.energyIn(side), w.energyOut(side)) : null));
        }
        if ((type == SimpleMachines.alloyFurnaceEntity || type == SimpleMachines.extruderEntity) && be instanceof Container c) {
            e.addCapability(SimpleMachines.id("items"), Caps.provider(ForgeCapabilities.ITEM_HANDLER, side -> items(c, side)));
        }
        if (type == SimpleMachines.tankEntity || type == SimpleMachines.MACHINE_ENTITIES.get(Core.LAVA)) {
            e.addCapability(SimpleMachines.id("fluid"), Caps.provider(ForgeCapabilities.FLUID_HANDLER, side -> new ForgeTank((FluidStore) be)));
        }
        if (type == SimpleMachines.MACHINE_ENTITIES.get(Core.TANK) && be instanceof EntangledTankBlockEntity t) {
            e.addCapability(SimpleMachines.id("fluid"), Caps.provider(ForgeCapabilities.FLUID_HANDLER, side -> t.exposed() ? new ForgeTank(t) : null));
        }
        if (SimpleMachines.CABLES.contains(be.getBlockState().getBlock())) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side ->
                    be.getLevel() instanceof ServerLevel server ? new CableEnergyHandler(server, be.getBlockPos(), side) : null));
        }
        if (type == SimpleMachines.transformerEntity && be instanceof com.workshopcraft.simplemachines.cable.TransformerBlockEntity t) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new BufferEnergyHandler(t.input(side), true, false)));
        }
        if (type == SimpleMachines.solarEntity && be instanceof com.workshopcraft.simplemachines.gen.SolarPanelBlockEntity s) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new BufferEnergyHandler(s.energy, false, true)));
        }
        if (type == SimpleMachines.windEntity && be instanceof com.workshopcraft.simplemachines.gen.WindTurbineBlockEntity t) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new BufferEnergyHandler(t.energy, false, true)));
        }
    }

    private static net.minecraftforge.items.IItemHandler items(Container c, Direction side) {
        return side != null && c instanceof WorldlyContainer w ? new SidedInvWrapper(w, side) : new InvWrapper(c);
    }

    private static void onItemCaps(AttachCapabilitiesEvent<ItemStack> e) {
        final ItemStack stack = e.getObject();
        final Item item = stack.getItem();
        if (item == SimpleMachines.MACHINES.get(Core.BATTERY).asItem()) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new StackEnergy(stack, BatteryBlockEntity.itemCapacity(stack), BatteryBlockEntity.RATE)));
        } else if (item == SimpleMachines.entangledBackpack) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new StackEnergy(stack, EntangledBackpackItem.CAPACITY, PoweredToolItem.RATE)));
        } else if (SimpleMachines.TOOLS.contains(item)) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new StackEnergy(stack, PoweredToolItem.capacity(stack), PoweredToolItem.RATE)));
        }
    }

    private static IEnergyStorage item(Container container, int slot) {
        final ItemStack stack = container.getItem(slot);
        return stack.isEmpty() ? null : stack.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
    }

    // Forge 1.20.1 loot tables have no load conditions: the manual table ships empty and gets its book here when Simple Books is present
    private static void onLootLoad(LootTableLoadEvent e) {
        if (!e.getName().equals(new ResourceLocation(SimpleMachines.MODID, "manual")) || !ModList.get().isLoaded("simplebooks")) return;
        Item guide = ForgeRegistries.ITEMS.getValue(new ResourceLocation("simplebooks", "guide"));
        if (guide == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putString("simplebooks:book", SimpleMachines.MODID + ":manual");
        e.getTable().addPool(LootPool.lootPool().add(LootItem.lootTableItem(guide).apply(SetNbtFunction.setTag(tag))).build());
    }
}
