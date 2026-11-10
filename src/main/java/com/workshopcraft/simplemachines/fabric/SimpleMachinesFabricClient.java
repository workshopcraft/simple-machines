package com.workshopcraft.simplemachines.fabric;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.client.EnergyScreen;
import com.workshopcraft.simplemachines.client.MachineScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.workshopcraft.simplemachines.client.ToolEffects;
import com.workshopcraft.simplemachines.client.WorktopRenderer;
import net.minecraft.client.gui.screens.MenuScreens;
import com.workshopcraft.simplemachines.assembly.Core;
import com.workshopcraft.simplemachines.client.PoweredScreen;
import com.workshopcraft.simplemachines.machine.PoweredMenu;
import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class SimpleMachinesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ToolEffects::tick);
        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(com.workshopcraft.simplemachines.client.BackpackKey.KEY);
        ClientTickEvents.END_CLIENT_TICK.register(com.workshopcraft.simplemachines.client.BackpackKey::tick);
        com.workshopcraft.simplemachines.client.BackpackKey.send = () -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.INSTANCE);
        com.workshopcraft.simplemachines.compat.jei.ViewerRecipes.source = SimpleMachinesFabricClient::synced;
        com.workshopcraft.simplemachines.client.ToolRunning.registerDirect(SimpleMachines.id("tool_running"));
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(SimpleMachines.HOT_SPARK,
                com.workshopcraft.simplemachines.client.HotSparkParticle::provider);
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(SimpleMachines.WHITE_SPARK,
                com.workshopcraft.simplemachines.client.HotSparkParticle::whiteProvider);
        com.workshopcraft.simplemachines.client.ToolSpin.registerDirect(SimpleMachines.id("spin"));
        com.workshopcraft.simplemachines.client.BatteryCharge.registerDirect(SimpleMachines.id("charge"));
        com.workshopcraft.simplemachines.client.TankItemRenderer.Unbaked.registerDirect(SimpleMachines.id("tank_fluid"));
        MenuScreens.register(SimpleMachines.ALLOY_MENU, MachineScreen::new);
        MenuScreens.register(SimpleMachines.FILTER_MENU, com.workshopcraft.simplemachines.client.FilterScreen::new);
        MenuScreens.register(SimpleMachines.CRAFTER_MENU, com.workshopcraft.simplemachines.client.AutoCrafterScreen::new);
        MenuScreens.register(SimpleMachines.PROGRAMMER_MENU, com.workshopcraft.simplemachines.client.EntangledProgrammerScreen::new);
        MenuScreens.register(SimpleMachines.BUFFER_MENU, com.workshopcraft.simplemachines.client.EntangledBufferScreen::new);
        MenuScreens.register(SimpleMachines.TANK_MENU, com.workshopcraft.simplemachines.client.EntangledTankScreen::new);
        MenuScreens.register(SimpleMachines.RELAY_MENU, com.workshopcraft.simplemachines.client.EntangledRelayScreen::new);
        MenuScreens.register(SimpleMachines.PAD_MENU, com.workshopcraft.simplemachines.client.EntangledPadScreen::new);
        MenuScreens.register(SimpleMachines.EXTRUDER_MENU, MachineScreen::new);
        MenuScreens.register(SimpleMachines.GENERATOR_MENU, EnergyScreen::new);
        MenuScreens.register(SimpleMachines.ROLLING_MILL_MENU, EnergyScreen::new);
        MenuScreens.register(SimpleMachines.ELECTRIC_FURNACE_MENU, EnergyScreen::new);
        for (MenuType<PoweredMenu> m : SimpleMachines.MENUS.values()) MenuScreens.register(m, PoweredScreen::new);
        for (Core core : SimpleMachines.NEW_CORES) renderer(SimpleMachines.MACHINE_ENTITIES.get(core));
        BlockEntityRenderers.register(SimpleMachines.chassisEntity, WorktopRenderer::new);
        BlockEntityRenderers.register(SimpleMachines.conduitEntity, com.workshopcraft.simplemachines.client.ConduitRenderer::new);
        BlockEntityRenderers.register(SimpleMachines.windEntity, com.workshopcraft.simplemachines.client.WindTurbineRenderer::new);
        BlockEntityRenderers.register(SimpleMachines.tankEntity, com.workshopcraft.simplemachines.client.TankRenderer::new);
        BlockEntityRenderers.register(SimpleMachines.toolStandEntity, com.workshopcraft.simplemachines.client.ToolStandRenderer::new);
        BlockEntityRenderers.register(SimpleMachines.generatorEntity, WorktopRenderer::new);
        BlockEntityRenderers.register(SimpleMachines.rollingMillEntity, WorktopRenderer::new);
        BlockEntityRenderers.register(SimpleMachines.electricFurnaceEntity, WorktopRenderer::new);
        //? if ship {
        com.workshopcraft.simplemachines.fabric.ship.ShipFabricClient.init();
        //?}
    }

    @SuppressWarnings("unchecked")
    private static <T extends WorkBlockEntity> void renderer(BlockEntityType<?> type) {
        if (type == SimpleMachines.MACHINE_ENTITIES.get(Core.QUARRY)) {
            BlockEntityRenderers.register((BlockEntityType<com.workshopcraft.simplemachines.quarry.QuarryBlockEntity>) type,
                    com.workshopcraft.simplemachines.client.QuarryRenderer::new);
            return;
        }
        BlockEntityRenderers.register((BlockEntityType<T>) type, WorktopRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.Collection<? extends net.minecraft.world.item.crafting.RecipeHolder<?>> synced(net.minecraft.world.item.crafting.RecipeType<?> type) {
        final var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return java.util.List.of();
        return level.recipeAccess().getSynchronizedRecipes().getAllOfType((net.minecraft.world.item.crafting.RecipeType) type);
    }
}
