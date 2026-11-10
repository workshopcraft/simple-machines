package com.workshopcraft.simplemachines.ship.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.simplemachines.ship.block.CountdownBlockEntity;
import com.workshopcraft.simplemachines.ship.block.FacingEntityBlock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** The jump countdown: a label and mm:ss, or JUMP when it runs out. */
public class CountdownRenderer implements BlockEntityRenderer<CountdownBlockEntity, CountdownRenderer.State> {
    private final Font font;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        int seconds = -1;
        long time;
    }

    public CountdownRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CountdownBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, breaking);
        s.facing = be.getBlockState().getValue(FacingEntityBlock.FACING);
        s.seconds = be.seconds();
        s.time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        final String label = Component.translatable("screen.simplemachines.jump_in").getString();
        final String big;
        int colour = 0xFFFFB347;
        if (s.seconds == -2) {
            big = Component.translatable("screen.simplemachines.jump").getString();
            colour = (s.time / 5) % 2 == 0 ? 0xFFFF5040 : 0xFFFFE070;
        } else if (s.seconds < 0) big = "--:--";
        else big = String.format("%02d:%02d", s.seconds / 60, s.seconds % 60);
        pose.pushPose();
        ShipSprites.face(pose, s.facing);
        // label, small
        pose.pushPose();
        final float small = 1F / 16F / 9F;
        ScreenText.begin(pose, 8F + font.width(label) * small * 8F, 12.5F, 13.5F, small);
        ScreenText.line(out, pose, font, Component.literal(label).getVisualOrderText(), 0, 0, 0xFFB0C4D8);
        pose.popPose();
        // the time, large and centred
        final float scale = 1F / 16F / 3.2F;
        final float half = font.width(big) * scale * 8F;
        ScreenText.begin(pose, 8F + half, 9.5F, 13.5F, scale);
        ScreenText.line(out, pose, font, Component.literal(big).getVisualOrderText(), 0, 0, colour);
        pose.popPose();
    }
}
