package com.workshopcraft.simplemachines.ship.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.simplemachines.client.Submit;
import com.workshopcraft.simplemachines.ship.block.FacingEntityBlock;
import com.workshopcraft.simplemachines.ship.block.TerminalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shows the start of a story log on the terminal screen, green on black like an old monitor. */
public class TerminalRenderer implements BlockEntityRenderer<TerminalBlockEntity> {
    // screen area on the model's north face, in pixels
    static final float RIGHT = 14.5F, TOP = 14F, Z = 3F, WIDTH = 13F;
    // one line is 1.5 pixels of the block
    static final float SCALE = 1.5F / 16F / 9.5F;
    static final int LINES = 7;
    private final Font font;

    public static class State extends Submit.State {
        Direction facing = Direction.NORTH;
        final List<FormattedCharSequence> lines = new ArrayList<>();
    }

    public TerminalRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.getFont();
    }

    public State createRenderState() {
        return new State();
    }

    public void extractRenderState(TerminalBlockEntity be, State s, float partialTick, Vec3 camera, Submit.Crumbling breaking) {
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
        while (s.lines.size() > LINES) s.lines.remove(s.lines.size() - 1);
    }

    public void submit(State s, PoseStack pose, Submit out, Object camera) {
        pose.pushPose();
        ShipSprites.face(pose, s.facing);
        ScreenText.begin(pose, RIGHT, TOP, Z, SCALE);
        for (int i = 0; i < s.lines.size(); i++) ScreenText.line(out, pose, font, s.lines.get(i), 0, i * 9.5F, i == 0 ? 0xFF7CFF8A : 0xFF4FD860);
        pose.popPose();
    }

    @Override
    public void render(TerminalBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        final State s = createRenderState();
        s.lightCoords = light;
        extractRenderState(be, s, partialTick, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), null);
        submit(s, pose, new Submit(buffers), null);
    }
}
