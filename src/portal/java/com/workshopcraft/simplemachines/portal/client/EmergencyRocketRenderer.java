package com.workshopcraft.simplemachines.portal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.simplemachines.portal.entity.EmergencyRocketEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
//? if >=26.1 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
*///?}

/**
 * Draws the rocket with its item model, standing upright. The model and texture are ordinary
 * assets so they can be replaced without touching this class. Visual launch effects are M5.
 */
//? if >=26.1 {
public class EmergencyRocketRenderer extends EntityRenderer<EmergencyRocketEntity, ThrownItemRenderState> {
    private final ItemModelResolver itemModelResolver;

    public EmergencyRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.3F;
    }

    @Override
    public ThrownItemRenderState createRenderState() {
        return new ThrownItemRenderState();
    }

    @Override
    public void extractRenderState(EmergencyRocketEntity entity, ThrownItemRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.FIXED, entity);
    }

    @Override
    public void submit(ThrownItemRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.0F, 0.4F, 0.0F);
        pose.scale(1.4F, 1.4F, 1.4F);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
//?} else {
/*public class EmergencyRocketRenderer extends EntityRenderer<EmergencyRocketEntity> {
    private final ItemRenderer itemRenderer;

    public EmergencyRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(EmergencyRocketEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.0F, 0.4F, 0.0F);
        pose.scale(1.4F, 1.4F, 1.4F);
        this.itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public Identifier getTextureLocation(EmergencyRocketEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
*///?}
