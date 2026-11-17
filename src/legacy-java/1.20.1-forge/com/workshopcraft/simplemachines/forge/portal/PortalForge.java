package com.workshopcraft.simplemachines.forge.portal;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.forge.BufferEnergyHandler;
import com.workshopcraft.simplemachines.forge.Caps;
import com.workshopcraft.simplemachines.forge.SimpleMachinesForge;
import com.workshopcraft.simplemachines.forge.StackEnergy;
import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.PortalCommands;
import com.workshopcraft.simplemachines.portal.PortalEvents;
import com.workshopcraft.simplemachines.portal.PortalPlatform;
import com.workshopcraft.simplemachines.portal.RiftHudPayload;
import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import com.workshopcraft.simplemachines.portal.block.ResearchStationBlockEntity;
import com.workshopcraft.simplemachines.portal.item.MobScannerItem;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Forge 1.20.1 side of the portal core. Forge caches the level array it ticks, so markWorldsDirty runs after every
 * change to the level map; LevelEvent.Load and Unload are fired for portal worlds. The rift countdown rides on the
 * main channel. Forge 1.20.1 has no ticket type registry and no LevelStem seed override.
 */
public final class PortalForge {
    /** Set by the client glue; a dedicated server never receives the countdown. */
    public static Consumer<RiftHudPayload> hudReceiver = p -> {};

    private PortalForge() {}

    public static void init(IEventBus modBus) {
        Portal.init();
        Portal.registerComponents((id, t) -> t.bind(id.getPath()));
        PortalPlatform.worldsChanged = MinecraftServer::markWorldsDirty;
        PortalPlatform.loaded = level -> MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(level));
        PortalPlatform.unloaded = level -> MinecraftForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        SimpleMachinesForge.CHANNEL.registerMessage(1, RiftHudPayload.class, (p, buf) -> RiftHudPayload.CODEC.encode(buf, p), RiftHudPayload.CODEC::decode,
                (p, ctx) -> {
                    ctx.get().enqueueWork(() -> hudReceiver.accept(p));
                    ctx.get().setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        PortalPlatform.sendHud = (player, payload) -> SimpleMachinesForge.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.BLOCK, h -> Portal.registerBlocks(h::register));
            e.register(Registries.ITEM, h -> Portal.registerItems(h::register));
            e.register(Registries.CREATIVE_MODE_TAB, h -> Portal.registerTabs(h::register));
            e.register(Registries.BLOCK_ENTITY_TYPE, h -> Portal.registerBlockEntities(h::register));
            e.register(Registries.MENU, h -> Portal.registerMenus(h::register));
            e.register(Registries.CHUNK_GENERATOR, h -> Portal.registerChunkGenerators((id, c) -> h.register(id, c.codec())));
            e.register(Registries.PLACEMENT_MODIFIER_TYPE, h -> Portal.registerPlacementModifiers(h::register));
        });
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, PortalForge::onBlockCaps);
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, PortalForge::onItemCaps);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> PortalCommands.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) PortalEvents.serverTickEnd(e.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> PortalEvents.serverStarted(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> PortalEvents.serverStopped(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) PortalEvents.join(p);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) PortalEvents.leave(p);
        });
    }

    private static void onBlockCaps(AttachCapabilitiesEvent<BlockEntity> e) {
        if (e.getObject() instanceof GateControllerBlockEntity be) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new BufferEnergyHandler(be.energy(), true, false) {
                // HV only: anything lower is refused and browns the gate out
                @Override
                public int receiveEnergy(int amount, boolean simulate) {
                    return amount > 0 && !be.acceptsFrom(side) ? 0 : super.receiveEnergy(amount, simulate);
                }
            }));
            e.addCapability(SimpleMachines.id("items"), Caps.provider(ForgeCapabilities.ITEM_HANDLER, side -> items(be, side)));
        } else if (e.getObject() instanceof ResearchStationBlockEntity be) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new BufferEnergyHandler(be.energy(), true, false)));
            e.addCapability(SimpleMachines.id("items"), Caps.provider(ForgeCapabilities.ITEM_HANDLER, side -> items(be, side)));
        }
    }

    private static IItemHandler items(Container c, Direction side) {
        return side != null && c instanceof WorldlyContainer w ? new SidedInvWrapper(w, side) : new InvWrapper(c);
    }

    private static void onItemCaps(AttachCapabilitiesEvent<ItemStack> e) {
        final ItemStack stack = e.getObject();
        if (stack.getItem() == Portal.mobScanner) {
            e.addCapability(SimpleMachines.id("energy"), Caps.provider(ForgeCapabilities.ENERGY, side -> new StackEnergy(stack, MobScannerItem.CAPACITY, MobScannerItem.RATE)));
        }
    }
}
