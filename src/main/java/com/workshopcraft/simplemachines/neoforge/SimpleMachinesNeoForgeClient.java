package com.workshopcraft.simplemachines.neoforge;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.client.EnergyScreen;
import com.workshopcraft.simplemachines.client.MachineScreen;
import net.neoforged.api.distmarker.Dist;
import com.workshopcraft.simplemachines.assembly.Core;
import com.workshopcraft.simplemachines.client.PoweredScreen;
import com.workshopcraft.simplemachines.machine.PoweredMenu;
import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import com.workshopcraft.simplemachines.client.ToolEffects;
import net.neoforged.fml.common.Mod;
import com.workshopcraft.simplemachines.client.WorktopRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = SimpleMachines.MODID, dist = Dist.CLIENT)
public class SimpleMachinesNeoForgeClient {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SimpleMachinesNeoForgeClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            ToolEffects.tick(Minecraft.getInstance());
            com.workshopcraft.simplemachines.client.BackpackKey.tick(Minecraft.getInstance());
        });
        com.workshopcraft.simplemachines.client.BackpackKey.send = () -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.INSTANCE);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> e.register(com.workshopcraft.simplemachines.client.BackpackKey.KEY));
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, (net.neoforged.neoforge.client.event.RecipesReceivedEvent e) -> {
            final var map = e.getRecipeMap();
            com.workshopcraft.simplemachines.compat.jei.ViewerRecipes.source = t -> map.byType((net.minecraft.world.item.crafting.RecipeType) t);
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent e) ->
                e.register(SimpleMachines.id("tool_running"), com.workshopcraft.simplemachines.client.ToolRunning.MAP_CODEC));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) ->
        {
            e.registerSpriteSet(SimpleMachines.HOT_SPARK, com.workshopcraft.simplemachines.client.HotSparkParticle::provider);
            e.registerSpriteSet(SimpleMachines.WHITE_SPARK, com.workshopcraft.simplemachines.client.HotSparkParticle::whiteProvider);
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent e) -> {
            e.register(SimpleMachines.id("spin"), com.workshopcraft.simplemachines.client.ToolSpin.MAP_CODEC);
            e.register(SimpleMachines.id("charge"), com.workshopcraft.simplemachines.client.BatteryCharge.MAP_CODEC);
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent e) ->
                e.register(SimpleMachines.id("tank_fluid"), com.workshopcraft.simplemachines.client.TankItemRenderer.Unbaked.MAP_CODEC));
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            for (MenuType<PoweredMenu> m : SimpleMachines.MENUS.values()) e.register(m, PoweredScreen::new);
            e.register(SimpleMachines.ALLOY_MENU, MachineScreen::new);
            e.register(SimpleMachines.FILTER_MENU, com.workshopcraft.simplemachines.client.FilterScreen::new);
            e.register(SimpleMachines.CRAFTER_MENU, com.workshopcraft.simplemachines.client.AutoCrafterScreen::new);
            e.register(SimpleMachines.PROGRAMMER_MENU, com.workshopcraft.simplemachines.client.EntangledProgrammerScreen::new);
            e.register(SimpleMachines.BUFFER_MENU, com.workshopcraft.simplemachines.client.EntangledBufferScreen::new);
            e.register(SimpleMachines.TANK_MENU, com.workshopcraft.simplemachines.client.EntangledTankScreen::new);
            e.register(SimpleMachines.RELAY_MENU, com.workshopcraft.simplemachines.client.EntangledRelayScreen::new);
            e.register(SimpleMachines.PAD_MENU, com.workshopcraft.simplemachines.client.EntangledPadScreen::new);
            e.register(SimpleMachines.EXTRUDER_MENU, MachineScreen::new);
            e.register(SimpleMachines.GENERATOR_MENU, EnergyScreen::new);
            e.register(SimpleMachines.ROLLING_MILL_MENU, EnergyScreen::new);
            e.register(SimpleMachines.ELECTRIC_FURNACE_MENU, EnergyScreen::new);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            for (Core core : SimpleMachines.NEW_CORES) renderer(e, SimpleMachines.MACHINE_ENTITIES.get(core));
            e.registerBlockEntityRenderer(SimpleMachines.chassisEntity, WorktopRenderer::new);
            e.registerBlockEntityRenderer(SimpleMachines.conduitEntity, com.workshopcraft.simplemachines.client.ConduitRenderer::new);
            e.registerBlockEntityRenderer(SimpleMachines.windEntity, com.workshopcraft.simplemachines.client.WindTurbineRenderer::new);
            e.registerBlockEntityRenderer(SimpleMachines.tankEntity, com.workshopcraft.simplemachines.client.TankRenderer::new);
            e.registerBlockEntityRenderer(SimpleMachines.toolStandEntity, com.workshopcraft.simplemachines.client.ToolStandRenderer::new);
            e.registerBlockEntityRenderer(SimpleMachines.generatorEntity, WorktopRenderer::new);
            e.registerBlockEntityRenderer(SimpleMachines.rollingMillEntity, WorktopRenderer::new);
            e.registerBlockEntityRenderer(SimpleMachines.electricFurnaceEntity, WorktopRenderer::new);
        });
        //? if ship {
        com.workshopcraft.simplemachines.neoforge.ship.ShipNeoForgeClient.init(modBus);
        //?}
        //? if portal {
        com.workshopcraft.simplemachines.neoforge.portal.PortalNeoForgeClient.init(modBus);
        //?}
    }

    @SuppressWarnings("unchecked")
    private static <T extends WorkBlockEntity> void renderer(EntityRenderersEvent.RegisterRenderers e, BlockEntityType<?> type) {
        if (type == SimpleMachines.MACHINE_ENTITIES.get(Core.QUARRY)) {
            e.registerBlockEntityRenderer((BlockEntityType<com.workshopcraft.simplemachines.quarry.QuarryBlockEntity>) type,
                    com.workshopcraft.simplemachines.client.QuarryRenderer::new);
            return;
        }
        e.registerBlockEntityRenderer((BlockEntityType<T>) type, WorktopRenderer::new);
    }
}
