package com.workshopcraft.simplemachines.ship.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.client.RenderCompat;
import com.workshopcraft.simplemachines.ship.block.CryopodBlock;
import com.workshopcraft.simplemachines.ship.block.CryopodBlockEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Draws the sleeper in a decoration cryopod: a player skin by name or UUID, or a bundled skin. */
public class CryopodRenderer implements BlockEntityRenderer<CryopodBlockEntity, CryopodRenderer.State> {
    private static final Map<String, ResolvableProfile> PROFILES = new ConcurrentHashMap<>();
    private final PlayerModel wide, slim;
    private final PlayerSkinRenderCache skins;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        Identifier texture;
        boolean slim;
    }

    public CryopodRenderer(BlockEntityRendererProvider.Context ctx) {
        wide = new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        skins = ctx.playerSkinRenderCache();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void extractRenderState(CryopodBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, breaking);
        s.facing = be.getBlockState().getBlock() instanceof CryopodBlock ? be.getBlockState().getValue(CryopodBlock.FACING) : Direction.NORTH;
        final String who = be.occupant();
        s.texture = null;
        if (who.isEmpty()) return;
        if (who.startsWith("skin:")) {
            final String name = who.substring(5).toLowerCase(java.util.Locale.ROOT);
            s.texture = SimpleMachines.id("textures/entity/cryopod/" + name + ".png");
            s.slim = name.endsWith("_slim");
            return;
        }
        final PlayerSkin skin = skins.getOrDefault(PROFILES.computeIfAbsent(who, CryopodRenderer::profile)).playerSkin();
        s.texture = skin.body().texturePath();
        s.slim = skin.model() == PlayerModelType.SLIM;
    }

    private static ResolvableProfile profile(String who) {
        try {
            return ResolvableProfile.createUnresolved(UUID.fromString(who));
        } catch (IllegalArgumentException e) {
            return ResolvableProfile.createUnresolved(who);
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.texture == null) return;
        pose.pushPose();
        pose.translate(0.5F, 0.12F, 0.5F);
        RenderCompat.rotate(pose, Axis.YP.rotationDegrees(180F - s.facing.toYRot()));
        // lean back a little against the pod
        RenderCompat.rotate(pose, Axis.XP.rotationDegrees(-6F));
        pose.scale(0.92F, 0.92F, 0.92F);
        pose.scale(-1F, -1F, 1F);
        pose.translate(0F, -1.501F, 0F);
        final AvatarRenderState avatar = new AvatarRenderState();
        out.submitModel(s.slim ? slim : wide, avatar, pose, RenderTypes.entityTranslucent(s.texture), s.lightCoords, OverlayTexture.NO_OVERLAY, 0, null);
        pose.popPose();
    }
}
