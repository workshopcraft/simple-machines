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
        com.workshopcraft.simplemachines.client.BackpackKey.send = () -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.INSTANCE);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> e.register(com.workshopcraft.simplemachines.client.BackpackKey.KEY));
        com.workshopcraft.simplemachines.compat.jei.ViewerRecipes.source = t -> {
            final var level = Minecraft.getInstance().level;
            return level == null ? java.util.List.of() : level.getRecipeManager().getAllRecipesFor((net.minecraft.world.item.crafting.RecipeType) t);
        };
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("tool_running"), com.workshopcraft.simplemachines.client.ToolRunning.INSTANCE);
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("spin"), com.workshopcraft.simplemachines.client.ToolSpin.INSTANCE);
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("charge"), com.workshopcraft.simplemachines.client.BatteryCharge.INSTANCE);
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("tool_holder"), com.workshopcraft.simplemachines.client.ToolItemRenderer.HOLDER);
        }));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) ->
        {
            e.registerSpriteSet(SimpleMachines.HOT_SPARK, com.workshopcraft.simplemachines.client.HotSparkParticle::provider);
            e.registerSpriteSet(SimpleMachines.WHITE_SPARK, com.workshopcraft.simplemachines.client.HotSparkParticle::whiteProvider);
        });
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
        com.workshopcraft.simplemachines.client.ConduitRenderer.look = new com.workshopcraft.simplemachines.client.ConduitRenderer.FluidLook() {
            @Override
            public net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(net.minecraft.world.level.material.Fluid f) {
                final var ext = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(f);
                return net.minecraft.client.Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(ext.getStillTexture());
            }

            @Override
            public int tint(net.minecraft.world.level.material.Fluid f, net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
                return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(f).getTintColor(f.defaultFluidState(), level, pos);
            }
        };
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) ->
                e.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                    @Override
                    public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return com.workshopcraft.simplemachines.client.TankItemRenderer.get();
                    }
                }, SimpleMachines.tank.asItem()));
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) -> {
            for (String t : com.workshopcraft.simplemachines.client.ToolItemRenderer.TOOLS)
                e.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                    @Override
                    public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return com.workshopcraft.simplemachines.client.ToolItemRenderer.get();
                    }
                }, net.minecraft.core.registries.BuiltInRegistries.ITEM.get(SimpleMachines.id(t)));
        });
        modBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional e) -> {
            for (var id : com.workshopcraft.simplemachines.client.ToolItemRenderer.leafModels(net.minecraft.client.Minecraft.getInstance().getResourceManager()))
                e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(id));
        });
        modBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult e) -> {
            com.workshopcraft.simplemachines.client.EulerQuads.load(net.minecraft.client.Minecraft.getInstance().getResourceManager());
            e.getModels().forEach((k, m) -> { if (k.id().getNamespace().equals(SimpleMachines.MODID)) com.workshopcraft.simplemachines.client.EulerQuads.fix(m); });
        });
        com.workshopcraft.simplemachines.client.ToolItemRenderer.models = id -> net.minecraft.client.Minecraft.getInstance().getModelManager()
                .getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(id));
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
        com.workshopcraft.simplemachines.neoforge.ship.ShipNeoForgeClient.init(modBus);
        com.workshopcraft.simplemachines.neoforge.portal.PortalNeoForgeClient.init(modBus);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() ->
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
}
