package com.workshopcraft.simplemachines.ship.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.simplemachines.client.Quads;
import com.workshopcraft.simplemachines.client.RenderCompat;
import com.workshopcraft.simplemachines.ship.block.SlideBlock;
import com.workshopcraft.simplemachines.ship.block.SlidingDoorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.core.Direction.*;

/**
 * Draws a sliding door, hatch or shutter panel at its slide position. Textures: block/<name>_lower and _upper for two
 * tall doors, block/<name> for one block panels, block/<name>_edge for the panel edges.
 */
public class SlidingDoorRenderer implements BlockEntityRenderer<SlidingDoorBlockEntity, SlidingDoorRenderer.State> {
    private static final int SHADE = 0xFFC8C8C8;

    public static class State extends BlockEntityRenderState {
        SlideBlock.Kind kind = SlideBlock.Kind.DOOR;
        Direction facing = NORTH;
        Identifier name;
        float progress;
    }

    public SlidingDoorRenderer(BlockEntityRendererProvider.Context ctx) {
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
    public void extractRenderState(SlidingDoorBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, breaking);
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof SlideBlock slide)) {
            s.name = null;
            return;
        }
        s.kind = slide.kind;
        s.facing = state.getValue(SlideBlock.FACING);
        s.name = BuiltInRegistries.BLOCK.getKey(slide).withPrefix("block/");
        s.progress = be.progress(partialTick);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.name == null) return;
        pose.pushPose();
        ShipSprites.face(pose, s.facing);
        final float move = s.progress * s.kind.travel;
        final TextureAtlasSprite edge = ShipSprites.get(s.name.withSuffix("_edge"));
        out.submitCustomGeometry(pose, RenderCompat.cutoutBlocks(), (p, vc) -> {
            final Quads q = new Quads(p, vc, s.lightCoords, OverlayTexture.NO_OVERLAY);
            switch (s.kind) {
                case HATCH -> box(q, 0, 13, -move, 16, 16, 16 - move, ShipSprites.get(s.name), edge, true);
                case SHUTTER -> box(q, 0, move, 0, 16, 16 + move, 16, ShipSprites.get(s.name), edge, false);
                default -> {
                    box(q, move, 0, 6, 16 + move, 16, 10, ShipSprites.get(s.name.withSuffix("_lower")), edge, false);
                    box(q, move, 16, 6, 16 + move, 32, 10, ShipSprites.get(s.name.withSuffix("_upper")), edge, false);
                }
            }
        });
        pose.popPose();
    }

    // faces: the big ones get the panel texture, the thin ones the edge
    private static void box(Quads q, float x0, float y0, float z0, float x1, float y1, float z1, TextureAtlasSprite face, TextureAtlasSprite edge, boolean flat) {
        if (flat) {
            q.face(UP, x0, y0, z0, x1, y1, z1, face, 0, 0, 16, 16);
            q.face(DOWN, x0, y0, z0, x1, y1, z1, face, 0, 0, 16, 16, SHADE);
            for (Direction d : new Direction[]{NORTH, SOUTH, EAST, WEST}) q.face(d, x0, y0, z0, x1, y1, z1, edge, 0, 0, 16, 3, SHADE);
            return;
        }
        final float h = Math.min(16, y1 - y0);
        q.face(NORTH, x0, y0, z0, x1, y1, z1, face, 0, 0, 16, 16);
        q.face(SOUTH, x0, y0, z0, x1, y1, z1, face, 0, 0, 16, 16);
        final float depth = z1 - z0;
        q.face(EAST, x0, y0, z0, x1, y1, z1, edge, 0, 0, depth, h, SHADE);
        q.face(WEST, x0, y0, z0, x1, y1, z1, edge, 0, 0, depth, h, SHADE);
        q.face(UP, x0, y0, z0, x1, y1, z1, edge, 0, 0, 16, depth);
        q.face(DOWN, x0, y0, z0, x1, y1, z1, edge, 0, 0, 16, depth, SHADE);
    }
}
