package com.workshopcraft.simplemachines.forge;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.client.EnergyScreen;
import com.workshopcraft.simplemachines.client.MachineScreen;
import com.workshopcraft.simplemachines.assembly.Core;
import com.workshopcraft.simplemachines.client.PoweredScreen;
import com.workshopcraft.simplemachines.machine.PoweredMenu;
import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import com.workshopcraft.simplemachines.client.ToolEffects;
import com.workshopcraft.simplemachines.client.WorktopRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;

final class SimpleMachinesForgeClient {
    private SimpleMachinesForgeClient() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) return;
            ToolEffects.tick(Minecraft.getInstance());
            com.workshopcraft.simplemachines.client.BackpackKey.tick(Minecraft.getInstance());
        });
        com.workshopcraft.simplemachines.client.BackpackKey.send = () -> SimpleMachinesForge.CHANNEL.sendToServer(
                com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.INSTANCE);
        modBus.addListener((net.minecraftforge.client.event.RegisterKeyMappingsEvent e) -> e.register(com.workshopcraft.simplemachines.client.BackpackKey.KEY));
        com.workshopcraft.simplemachines.compat.jei.ViewerRecipes.source = t -> {
            final var level = Minecraft.getInstance().level;
            return level == null ? java.util.List.of() : level.getRecipeManager().getAllRecipesFor((net.minecraft.world.item.crafting.RecipeType) t);
        };
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("tool_running"), com.workshopcraft.simplemachines.client.ToolRunning.INSTANCE);
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("spin"), com.workshopcraft.simplemachines.client.ToolSpin.INSTANCE);
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("charge"), com.workshopcraft.simplemachines.client.BatteryCharge.INSTANCE);
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("tool_holder"), com.workshopcraft.simplemachines.client.ToolItemRenderer.HOLDER);
            for (MenuType<PoweredMenu> m : SimpleMachines.MENUS.values()) net.minecraft.client.gui.screens.MenuScreens.register(m, PoweredScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.ALLOY_MENU, MachineScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.FILTER_MENU, com.workshopcraft.simplemachines.client.FilterScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.CRAFTER_MENU, com.workshopcraft.simplemachines.client.AutoCrafterScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.PROGRAMMER_MENU, com.workshopcraft.simplemachines.client.EntangledProgrammerScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.BUFFER_MENU, com.workshopcraft.simplemachines.client.EntangledBufferScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.TANK_MENU, com.workshopcraft.simplemachines.client.EntangledTankScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.RELAY_MENU, com.workshopcraft.simplemachines.client.EntangledRelayScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.PAD_MENU, com.workshopcraft.simplemachines.client.EntangledPadScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.EXTRUDER_MENU, MachineScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.GENERATOR_MENU, EnergyScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.ROLLING_MILL_MENU, EnergyScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(SimpleMachines.ELECTRIC_FURNACE_MENU, EnergyScreen::new);
            renderProperties(SimpleMachines.tank.asItem(), com.workshopcraft.simplemachines.client.TankItemRenderer::get);
            for (String t : com.workshopcraft.simplemachines.client.ToolItemRenderer.TOOLS)
                renderProperties(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(SimpleMachines.id(t)), com.workshopcraft.simplemachines.client.ToolItemRenderer::get);
        }));
        modBus.addListener((net.minecraftforge.client.event.RegisterParticleProvidersEvent e) ->
        {
            e.registerSpriteSet(SimpleMachines.HOT_SPARK, com.workshopcraft.simplemachines.client.HotSparkParticle::provider);
            e.registerSpriteSet(SimpleMachines.WHITE_SPARK, com.workshopcraft.simplemachines.client.HotSparkParticle::whiteProvider);
        });
        com.workshopcraft.simplemachines.client.ConduitRenderer.look = new com.workshopcraft.simplemachines.client.ConduitRenderer.FluidLook() {
            @Override
            public net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(net.minecraft.world.level.material.Fluid f) {
                final var ext = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(f);
                return net.minecraft.client.Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(ext.getStillTexture());
            }

            @Override
            public int tint(net.minecraft.world.level.material.Fluid f, net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
                return net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(f).getTintColor(f.defaultFluidState(), level, pos);
            }
        };
        modBus.addListener((net.minecraftforge.client.event.ModelEvent.RegisterAdditional e) -> {
            for (var id : com.workshopcraft.simplemachines.client.ToolItemRenderer.leafModels(net.minecraft.client.Minecraft.getInstance().getResourceManager()))
                e.register(id);
        });
        modBus.addListener((net.minecraftforge.client.event.ModelEvent.ModifyBakingResult e) -> {
            com.workshopcraft.simplemachines.client.EulerQuads.load(net.minecraft.client.Minecraft.getInstance().getResourceManager());
            e.getModels().forEach((k, m) -> { if (k.getNamespace().equals(SimpleMachines.MODID)) com.workshopcraft.simplemachines.client.EulerQuads.fix(m); });
        });
        com.workshopcraft.simplemachines.client.ToolItemRenderer.models = id -> net.minecraft.client.Minecraft.getInstance().getModelManager()
                .getModel(id);
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
        com.workshopcraft.simplemachines.forge.ship.ShipForgeClient.init(modBus);
        com.workshopcraft.simplemachines.forge.portal.PortalForgeClient.init(modBus);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() ->
                com.workshopcraft.simplemachines.client.LegacyLayers.apply(net.minecraft.client.renderer.ItemBlockRenderTypes::setRenderLayer)));
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

    /** Forge 1.20.1 only reads item client extensions from Item.initializeClient, so fill the field it stores them in. */
    private static void renderProperties(net.minecraft.world.item.Item item, java.util.function.Supplier<net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer> renderer) {
        try {
            final java.lang.reflect.Field f = net.minecraft.world.item.Item.class.getDeclaredField("renderProperties");
            f.setAccessible(true);
            f.set(item, new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return renderer.get();
                }
            });
        } catch (ReflectiveOperationException e) {
            com.mojang.logging.LogUtils.getLogger().error("Could not set item renderer for {}", item, e);
        }
    }
}
