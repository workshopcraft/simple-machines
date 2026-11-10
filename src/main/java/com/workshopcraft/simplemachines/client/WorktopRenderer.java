package com.workshopcraft.simplemachines.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.math.Axis;
import com.workshopcraft.simplemachines.assembly.ChassisBlock;
import com.workshopcraft.simplemachines.assembly.Core;
import com.workshopcraft.simplemachines.assembly.Worktops;
import com.workshopcraft.simplemachines.machine.MachineBlock;
import com.workshopcraft.simplemachines.machine.PoweredMachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.machine.Ported;
import com.workshopcraft.simplemachines.pipe.ItemPipeBlock;
import com.workshopcraft.simplemachines.assembly.ChassisBlockEntity;
import com.workshopcraft.simplemachines.assembly.Upgrade;
import net.minecraft.world.level.Level;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.core.Direction.*;

/** Draws the worktop of a chassis or machine from whichever mod it came from, with the core's cut-out. */
public class WorktopRenderer<T extends BlockEntity & Worktops.Holder> implements BlockEntityRenderer<T, WorktopRenderer.State> {
    private static final int SHADE = 0xFFB8B8B8;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        Core core = Core.NONE;
        String worktop = "";
        boolean loose;
        int topLight;
        int ports;
        final int[] portLight = new int[6];
        final Upgrade[] cards = new Upgrade[Upgrade.MAX_SLOTS];
        int slots = Upgrade.SLOTS;
        int looseCard = -1;
        //? if ship {
        net.minecraft.resources.Identifier finish;
        //?}
    }

    public WorktopRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, breaking);
        final BlockState state = be.getBlockState();
        s.worktop = be.worktop();
        java.util.Arrays.fill(s.cards, null);
        s.looseCard = -1;
        if (state.getBlock() instanceof ChassisBlock && be instanceof ChassisBlockEntity chassis && state.getValue(ChassisBlock.STEP) >= 4) {
            int i = 0;
            s.slots = Upgrade.slots(state.getValue(ChassisBlock.CORE));
            for (Upgrade u : chassis.upgrades()) if (i < s.slots) s.cards[i++] = u;
            if (chassis.loose() != null && i < s.slots) {
                s.looseCard = i;
                s.cards[i] = chassis.loose();
            }
        }
        if (state.getBlock() instanceof ChassisBlock) {
            s.facing = state.getValue(ChassisBlock.FACING);
            s.core = state.getValue(ChassisBlock.CORE);
            s.loose = true;
            if (state.getValue(ChassisBlock.STEP) != 7) s.worktop = "";
        } else if (state.getBlock() instanceof PoweredMachineBlock machine) {
            s.facing = state.getValue(MachineBlock.FACING);
            s.core = machine.core();
            s.loose = false;
            ports(be, state, s);
        } else {
            s.worktop = "";
        }
        if (!(state.getBlock() instanceof PoweredMachineBlock)) s.ports = 0;
        //? if ship {
        s.finish = com.workshopcraft.simplemachines.ship.MachineFinishes.of(be);
        //?}
        s.topLight = be.getLevel() == null ? s.lightCoords : RenderCompat.light(be.getLevel(), be.getBlockPos().above());
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        //? if ship {
        if (s.finish != null) com.workshopcraft.simplemachines.ship.client.MachineFinishRenderer.submit(s.finish, s.facing, s.lightCoords, pose, out);
        //?}
        if (s.ports != 0) {
            final TextureAtlasSprite port = get(Identifier.fromNamespaceAndPath(SimpleMachines.MODID, "block/port"));
            out.submitCustomGeometry(pose, RenderCompat.cutoutBlocks(), (p, vc) -> {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if ((s.ports & Ported.bit(d)) != 0) port(new Quads(p, vc, s.portLight[d.get3DDataValue()], OverlayTexture.NO_OVERLAY), d, port);
                }
            });
        }
        if (s.cards[0] != null) cards(s, pose, out);
        if (s.worktop.isEmpty()) return;
        final TextureAtlasSprite sprite = sprite(s.worktop);
        final float[][] rects = rects(s.core);
        final float lift = s.loose ? 1.5F : 0F;
        pose.pushPose();
        pose.translate(0.5F, lift / 16F, 0.5F);
        RenderCompat.rotate(pose, Axis.YP.rotationDegrees(180F - s.facing.toYRot()));
        pose.translate(-0.5F, 0, -0.5F);
        out.submitCustomGeometry(pose, RenderCompat.cutoutBlocks(), (p, vc) -> draw(new Quads(p, vc, s.topLight, OverlayTexture.NO_OVERLAY), rects, sprite));
        if (s.breakProgress != null) {
            final ModelFeatureRenderer.CrumblingOverlay c = s.breakProgress;
            out.submitCustomGeometry(pose, ModelBakery.DESTROY_TYPES.get(c.progress()), (p, vc) ->
                    draw(new Quads(p, new SheetedDecalTextureGenerator(vc, c.cameraPose(), 1F), s.topLight, OverlayTexture.NO_OVERLAY), rects, sprite));
        }
        pose.popPose();
    }

    // upgrade cards stand on the inner side opposite the control board, a loose one sits raised
    private static void cards(State s, PoseStack pose, SubmitNodeCollector out) {
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        RenderCompat.rotate(pose, Axis.YP.rotationDegrees(180F - s.facing.toYRot()));
        pose.translate(-0.5F, 0, -0.5F);
        out.submitCustomGeometry(pose, RenderCompat.cutoutBlocks(), (p, vc) -> {
            final Quads q = new Quads(p, vc, s.lightCoords, OverlayTexture.NO_OVERLAY);
            for (int i = 0; i < s.slots; i++) {
                if (s.cards[i] == null) continue;
                final TextureAtlasSprite sp = get(SimpleMachines.id("block/" + s.cards[i].id));
                final float z0 = s.slots > 2 ? 2.5F + 4 * i : i == 0 ? 4 : 8.5F, z1 = z0 + 3.5F;
                final float y0 = i == s.looseCard ? 6 : 3, y1 = y0 + 8;
                final float x0 = 2.25F, x1 = 3;
                q.face(EAST, x0, y0, z0, x1, y1, z1, sp, 4, 0, 12, 16);
                q.face(WEST, x0, y0, z0, x1, y1, z1, sp, 4, 0, 12, 16, SHADE);
                for (Direction d : new Direction[]{UP, DOWN, NORTH, SOUTH}) q.face(d, x0, y0, z0, x1, y1, z1, sp, 0, 0, 1, 16, SHADE);
            }
        });
        pose.popPose();
    }

    private static final float PORT_OUT = 0.45F;

    private static void ports(BlockEntity be, BlockState state, State s) {
        s.ports = 0;
        final Level level = be.getLevel();
        if (level == null) return;
        final int outputs = be instanceof Ported ported ? ported.outputSides() : 0;
        final Direction facing = state.getValue(MachineBlock.FACING);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == facing) continue;
            final BlockPos n = be.getBlockPos().relative(d);
            if ((outputs & Ported.bit(d)) != 0 || ItemPipeBlock.link(level.getBlockState(n), d.getOpposite()).inventory()) {
                s.ports |= Ported.bit(d);
                s.portLight[d.get3DDataValue()] = RenderCompat.light(level, n);
            }
        }
    }

    // a square socket on the outside of the side panel
    private static void port(Quads q, Direction d, TextureAtlasSprite sp) {
        final float lo = PORT_OUT, hi = 16 - PORT_OUT;
        switch (d) {
            case NORTH -> q.face(NORTH, 6, 6, lo, 10, 10, lo, sp, 6, 6, 10, 10);
            case SOUTH -> q.face(SOUTH, 6, 6, hi, 10, 10, hi, sp, 6, 6, 10, 10);
            case WEST -> q.face(WEST, lo, 6, 6, lo, 10, 10, sp, 6, 6, 10, 10);
            case EAST -> q.face(EAST, hi, 6, 6, hi, 10, 10, sp, 6, 6, 10, 10);
            default -> {}
        }
    }

    // same surface as a Much More Storage worktop, minus the opening over the core
    private static void draw(Quads q, float[][] rects, TextureAtlasSprite sp) {
        for (float[] r : rects) {
            final float a0 = r[0], b0 = r[1], a1 = r[2], b1 = r[3];
            q.face(UP, a0, 14, b0, a1, 16, b1, sp, a0, b0, a1, b1);
            q.face(DOWN, a0, 14, b0, a1, 16, b1, sp, a0, b0, a1, b1, SHADE);
            q.face(NORTH, a0, 14, b0, a1, 16, b1, sp, 16 - a1, 0, 16 - a0, 2);
            q.face(SOUTH, a0, 14, b0, a1, 16, b1, sp, a0, 14, a1, 16);
            q.face(EAST, a0, 14, b0, a1, 16, b1, sp, 16 - b1, 0, 16 - b0, 2);
            q.face(WEST, a0, 14, b0, a1, 16, b1, sp, b0, 0, b1, 2);
        }
    }

    private static float[][] rects(Core core) {
        final float[] hole = switch (core) {
            case FURNACE -> new float[]{5, 5, 11, 11};
            case ROLLING -> new float[]{4, 6, 12, 10};
            case CRUSHER -> new float[]{4, 4, 12, 12};
            case ALLOY, WATER -> new float[]{5, 5, 11, 11};
            case SAW -> new float[]{7, 3, 9, 13};
            case PAD -> new float[]{3, 3, 13, 13};
            default -> null;
        };
        if (hole == null) return new float[][]{{0, 0, 16, 16}};
        return new float[][]{{0, 0, 16, hole[1]}, {0, hole[3], 16, 16}, {0, hole[1], hole[0], hole[3]}, {hole[2], hole[1], 16, hole[3]}};
    }

    /** ns:block/path of the worktop item, falling back to the vanilla block it is cut from. */
    public static TextureAtlasSprite sprite(String worktop) {
        Identifier id = Identifier.tryParse(worktop);
        if (id == null) id = Identifier.parse(Worktops.DEFAULT);
        TextureAtlasSprite sp = get(id.withPrefix("block/"));
        if (missing(sp) && id.getPath().endsWith("_worktop")) {
            sp = get(Identifier.withDefaultNamespace("block/" + id.getPath().substring(0, id.getPath().length() - 8)));
        }
        return sp;
    }

    static TextureAtlasSprite get(Identifier id) {
        return Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, id));
    }

    private static boolean missing(TextureAtlasSprite sp) {
        return sp.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }
}
