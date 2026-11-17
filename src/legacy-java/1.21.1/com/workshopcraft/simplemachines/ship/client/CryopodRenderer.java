package com.workshopcraft.simplemachines.ship.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.client.RenderCompat;
import com.workshopcraft.simplemachines.client.Submit;
import com.workshopcraft.simplemachines.ship.block.CryopodBlock;
import com.workshopcraft.simplemachines.ship.block.CryopodBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;

/** Draws the sleeper in a decoration cryopod: a player skin by name or UUID, or a bundled skin. */
public class CryopodRenderer implements BlockEntityRenderer<CryopodBlockEntity> {
    private final PlayerModel<?> wide, slim;

    public static class State extends Submit.State {
        Direction facing = Direction.NORTH;
        ResourceLocation texture;
        boolean slim;
    }

    public CryopodRenderer(BlockEntityRendererProvider.Context ctx) {
        wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen(CryopodBlockEntity be) {
        return true;
    }

    public void extractRenderState(CryopodBlockEntity be, State s, float partialTick, Vec3 camera, Submit.Crumbling breaking) {
        s.facing = be.getBlockState().getBlock() instanceof CryopodBlock ? be.getBlockState().getValue(CryopodBlock.FACING) : Direction.NORTH;
        final String who = be.occupant();
        s.texture = null;
        if (who.isEmpty()) return;
        if (who.startsWith("skin:")) {
            final String name = who.substring(5).toLowerCase(Locale.ROOT);
            s.texture = SimpleMachines.id("textures/entity/cryopod/" + name + ".png");
            s.slim = name.endsWith("_slim");
            return;
        }
        final PlayerSkin skin = skin(profile(who));
        s.texture = skin.texture();
        s.slim = skin.model() == PlayerSkin.Model.SLIM;
    }

    /** A live player is on the connection list; an offline name falls back to its offline UUID and default skin. */
    private static GameProfile profile(String who) {
        final ClientPacketListener connection = Minecraft.getInstance().getConnection();
        final PlayerInfo info = connection == null ? null : connection.getPlayerInfo(who);
        if (info != null) return info.getProfile();
        try {
            return new GameProfile(UUID.fromString(who), null);
        } catch (IllegalArgumentException e) {
            return new GameProfile(UUIDUtil.createOfflinePlayerUUID(who), who);
        }
    }

    private static PlayerSkin skin(GameProfile profile) {
        return Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
    }

    public void submit(State s, PoseStack pose, Submit out, Object camera) {
        if (s.texture == null) return;
        pose.pushPose();
        pose.translate(0.5F, 0.12F, 0.5F);
        RenderCompat.rotate(pose, Axis.YP.rotationDegrees(180F - s.facing.toYRot()));
        // lean back a little against the pod (positive: the head goes back, towards the console wall; it used to lean
        // forward and push the sleeper's face through the front glass)
        RenderCompat.rotate(pose, Axis.XP.rotationDegrees(6F));
        pose.scale(0.92F, 0.92F, 0.92F);
        pose.scale(-1F, -1F, 1F);
        pose.translate(0F, -1.501F, 0F);
        final PlayerModel<?> model = s.slim ? slim : wide;
        model.renderToBuffer(pose, out.buffers.getBuffer(RenderType.entityTranslucent(s.texture)), s.lightCoords, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
    }

    @Override
    public void render(CryopodBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        final State s = createRenderState();
        s.lightCoords = light;
        extractRenderState(be, s, partialTick, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), null);
        submit(s, pose, new Submit(buffers), null);
    }
}
