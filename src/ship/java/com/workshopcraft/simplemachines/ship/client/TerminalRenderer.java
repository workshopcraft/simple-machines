package com.workshopcraft.simplemachines.ship.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.simplemachines.ship.block.FacingEntityBlock;
import com.workshopcraft.simplemachines.ship.block.TerminalBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shows the start of a story log on the terminal screen, green on black like an old monitor. */
public class TerminalRenderer implements BlockEntityRenderer<TerminalBlockEntity, TerminalRenderer.State> {
    // screen area on the model's north face, in pixels
    static final float RIGHT = 14.5F, TOP = 14F, Z = 3F, WIDTH = 13F;
    // one line is 1.5 pixels of the block
    static final float SCALE = 1.5F / 16F / 9.5F;
    static final int LINES = 7;
    private final Font font;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        final List<FormattedCharSequence> lines = new ArrayList<>();
    }

    public TerminalRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TerminalBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, breaking);
        s.facing = be.getBlockState().getValue(FacingEntityBlock.FACING);
        s.lines.clear();
        final int width = (int) (WIDTH / 16F / SCALE);
        String body = be.text();
        if (body.isEmpty()) body = Component.translatable("screen.simplemachines.log_empty").getString();
        if (!be.title().isEmpty()) s.lines.addAll(font.split(Component.literal("> " + be.title()), width));
        for (String para : body.split("\n")) {
            if (s.lines.size() >= LINES) break;
            s.lines.addAll(font.split(Component.literal(para), width));
        }
        while (s.lines.size() > LINES) s.lines.removeLast();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        ShipSprites.face(pose, s.facing);
        ScreenText.begin(pose, RIGHT, TOP, Z, SCALE);
        for (int i = 0; i < s.lines.size(); i++) ScreenText.line(out, pose, font, s.lines.get(i), 0, i * 9.5F, i == 0 ? 0xFF7CFF8A : 0xFF4FD860);
        pose.popPose();
    }
}
