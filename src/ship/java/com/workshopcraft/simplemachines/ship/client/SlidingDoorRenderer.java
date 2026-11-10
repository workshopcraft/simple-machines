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
        /** Hatch edges that sit against a solid neighbour, local north, south, east, west: not drawn, so they can't z-fight it. */
        final boolean[] covered = new boolean[4];
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
        if (s.kind == SlideBlock.Kind.HATCH && be.getLevel() != null) {
            final Direction[] world = {s.facing, s.facing.getOpposite(), s.facing.getClockWise(), s.facing.getCounterClockWise()};
            for (int i = 0; i < 4; i++) s.covered[i] = be.getLevel().getBlockState(be.getBlockPos().relative(world[i])).isSolidRender();
        }
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
                // the hatch slides into its own frame, not over the next block: what has gone shrinks away at the edge
                case HATCH -> hatch(q, move, ShipSprites.get(s.name), edge, s.covered);
                case SHUTTER -> box(q, 0, move, 0, 16, 16 + move, 16, ShipSprites.get(s.name), ShipSprites.get(s.name), false);
                default -> {
                    box(q, move, 0, 6, 16 + move, 16, 10, ShipSprites.get(s.name.withSuffix("_lower")), edge, false);
                    box(q, move, 16, 6, 16 + move, 32, 10, ShipSprites.get(s.name.withSuffix("_upper")), edge, false);
                }
            }
        });
        pose.popPose();
    }

    private static void hatch(Quads q, float move, TextureAtlasSprite face, TextureAtlasSprite edge, boolean[] covered) {
        final float z1 = 16 - move;
        q.face(UP, 0, 13, 0, 16, 16, z1, face, 0, move, 16, 16);
        q.face(DOWN, 0, 13, 0, 16, 16, z1, face, 0, 0, 16, 16 - move, SHADE);
        final Direction[] sides = {NORTH, SOUTH, EAST, WEST};
        for (int i = 0; i < 4; i++) {
            // the south edge moves in from the block's side as it opens, so only a closed one can sit against a neighbour
            if (covered[i] && (sides[i] != SOUTH || move < 0.01F)) continue;
            q.face(sides[i], 0, 13, 0, 16, 16, z1, edge, 0, 0, 16, 3, SHADE);
        }
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
