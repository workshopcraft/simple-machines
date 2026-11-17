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
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(com.workshopcraft.simplemachines.client.BackpackKey.KEY);
        ClientTickEvents.END_CLIENT_TICK.register(com.workshopcraft.simplemachines.client.BackpackKey::tick);
        com.workshopcraft.simplemachines.client.BackpackKey.send = () -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                com.workshopcraft.simplemachines.entangled.EntangledBackpackItem.OpenPayload.INSTANCE);
        com.workshopcraft.simplemachines.compat.jei.ViewerRecipes.source = SimpleMachinesFabricClient::synced;
        net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("tool_running"), com.workshopcraft.simplemachines.client.ToolRunning.INSTANCE);
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(SimpleMachines.HOT_SPARK,
                com.workshopcraft.simplemachines.client.HotSparkParticle::provider);
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(SimpleMachines.WHITE_SPARK,
                com.workshopcraft.simplemachines.client.HotSparkParticle::whiteProvider);
        net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("spin"), com.workshopcraft.simplemachines.client.ToolSpin.INSTANCE);
        net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("charge"), com.workshopcraft.simplemachines.client.BatteryCharge.INSTANCE);
        net.minecraft.client.renderer.item.ItemProperties.registerGeneric(SimpleMachines.id("tool_holder"), com.workshopcraft.simplemachines.client.ToolItemRenderer.HOLDER);
        for (String t : com.workshopcraft.simplemachines.client.ToolItemRenderer.TOOLS)
            net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(SimpleMachines.id(t)),
                    (stack, ctx, pose, buffers, light, overlay) -> com.workshopcraft.simplemachines.client.ToolItemRenderer.get().renderByItem(stack, ctx, pose, buffers, light, overlay));
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(ctx -> {
            var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            ctx.addModels(com.workshopcraft.simplemachines.client.ToolItemRenderer.leafModels(rm));
            com.workshopcraft.simplemachines.client.EulerQuads.load(rm);
            java.util.Map<net.minecraft.client.resources.model.UnbakedModel, net.minecraft.client.resources.model.UnbakedModel> once = new java.util.IdentityHashMap<>();
            ctx.modifyModelBeforeBake().register((m, c) -> {
                if (!(m instanceof net.minecraft.client.renderer.block.model.multipart.MultiPart) || c.topLevelId() == null
                        || !c.topLevelId().id().getNamespace().equals(SimpleMachines.MODID)) return m;
                synchronized (once) { return once.computeIfAbsent(m, BakeOnce::new); }
            });
            ctx.modifyModelAfterBake().register((m, c) -> {
                var id = c.resourceId() != null ? c.resourceId() : c.topLevelId() != null ? c.topLevelId().id() : null;
                return id != null && id.getNamespace().equals(SimpleMachines.MODID) ? com.workshopcraft.simplemachines.client.EulerQuads.fix(m) : m;
            });
        });
        com.workshopcraft.simplemachines.client.ToolItemRenderer.models = id -> net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(id);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(SimpleMachines.tank,
                (stack, ctx, pose, buffers, light, overlay) -> com.workshopcraft.simplemachines.client.TankItemRenderer.get().renderByItem(stack, ctx, pose, buffers, light, overlay));
        com.workshopcraft.simplemachines.client.ConduitRenderer.look = new com.workshopcraft.simplemachines.client.ConduitRenderer.FluidLook() {
            @Override
            public net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(net.minecraft.world.level.material.Fluid f) {
                final var h = net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.get(f);
                return (h == null ? net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.get(net.minecraft.world.level.material.Fluids.WATER) : h)
                        .getFluidSprites(null, null, f.defaultFluidState())[0];
            }

            @Override
            public int tint(net.minecraft.world.level.material.Fluid f, net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
                final var h = net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.get(f);
                return h == null ? 0xFFFFFFFF : h.getFluidColor(level, pos, f.defaultFluidState());
            }
        };
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
        com.workshopcraft.simplemachines.fabric.ship.ShipFabricClient.init();
        com.workshopcraft.simplemachines.fabric.portal.PortalFabricClient.init();
        com.workshopcraft.simplemachines.client.LegacyLayers.apply(net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE::putBlock);
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
        return level.getRecipeManager().getAllRecipesFor((net.minecraft.world.item.crafting.RecipeType) type);
    }

    /** 1.21.1 bakes a multipart once per block state; the chassis has tens of thousands of states, so bake ours once. */
    private static final class BakeOnce implements net.minecraft.client.resources.model.UnbakedModel {
        private final net.minecraft.client.resources.model.UnbakedModel inner;
        private net.minecraft.client.resources.model.BakedModel baked;

        BakeOnce(net.minecraft.client.resources.model.UnbakedModel inner) {
            this.inner = inner;
        }

        @Override
        public java.util.Collection<net.minecraft.resources.ResourceLocation> getDependencies() {
            return inner.getDependencies();
        }

        @Override
        public void resolveParents(java.util.function.Function<net.minecraft.resources.ResourceLocation, net.minecraft.client.resources.model.UnbakedModel> f) {
            inner.resolveParents(f);
        }

        @Override
        public synchronized net.minecraft.client.resources.model.BakedModel bake(net.minecraft.client.resources.model.ModelBaker baker,
                java.util.function.Function<net.minecraft.client.resources.model.Material, net.minecraft.client.renderer.texture.TextureAtlasSprite> sprites,
                net.minecraft.client.resources.model.ModelState state) {
            if (baked == null) baked = inner.bake(baker, sprites, state);
            return baked;
        }
    }
}
