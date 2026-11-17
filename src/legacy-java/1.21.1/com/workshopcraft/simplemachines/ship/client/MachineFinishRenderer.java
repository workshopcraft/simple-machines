package com.workshopcraft.simplemachines.ship.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.simplemachines.client.Quads;
import com.workshopcraft.simplemachines.client.RenderCompat;
import com.workshopcraft.simplemachines.client.Submit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a machine finish over a built machine. The finish json lives at
 * assets/<ns>/simplemachines/machine_finishes/<path>.json:
 * <pre>
 * { "textures": { "front": "ns:block/...", "side": "ns:block/...", "back": "...", "top": "..." } }
 * </pre>
 * "side" covers left, right and back unless "back" is given; "front" and "top" are optional. Textures sit in the
 * block atlas (textures/block/...) and may have see-through pixels so the machine shows through.
 */
public final class MachineFinishRenderer {
    private static final float OUT = 0.04F;
    private static final Map<ResourceLocation, Def> CACHE = new ConcurrentHashMap<>();
    private static final long KEEP_MS = 5000;

    record Def(ResourceLocation front, ResourceLocation side, ResourceLocation back, ResourceLocation top, long read) {
    }

    private MachineFinishRenderer() {}

    private static Def def(ResourceLocation id) {
        final Def d = CACHE.get(id);
        if (d != null && System.currentTimeMillis() - d.read < KEEP_MS) return d;
        final Def fresh = load(id);
        CACHE.put(id, fresh);
        return fresh;
    }

    private static Def load(ResourceLocation id) {
        final ResourceLocation file = id.withPath(p -> "simplemachines/machine_finishes/" + p + ".json");
        final long now = System.currentTimeMillis();
        final Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
        if (res.isEmpty()) return new Def(null, null, null, null, now);
        try (Reader r = res.get().openAsReader()) {
            final JsonObject t = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("textures");
            final ResourceLocation side = tex(t, "side");
            final ResourceLocation back = tex(t, "back");
            return new Def(tex(t, "front"), side, back == null ? side : back, tex(t, "top"), now);
        } catch (Exception e) {
            return new Def(null, null, null, null, now);
        }
    }

    private static ResourceLocation tex(JsonObject t, String key) {
        return t != null && t.has(key) ? ResourceLocation.tryParse(t.get(key).getAsString()) : null;
    }

    public static void submit(ResourceLocation finish, Direction facing, int light, PoseStack pose, Submit out) {
        final Def d = def(finish);
        if (d.side == null && d.front == null && d.top == null) return;
        pose.pushPose();
        ShipSprites.face(pose, facing);
        out.submitCustomGeometry(pose, RenderCompat.cutoutBlocks(), (p, vc) -> {
            final Quads q = new Quads(p, vc, light, OverlayTexture.NO_OVERLAY);
            if (d.front != null) q.face(Direction.NORTH, 0, 0, -OUT, 16, 16, -OUT, sprite(d.front), 0, 0, 16, 16);
            if (d.back != null) q.face(Direction.SOUTH, 0, 0, 16 + OUT, 16, 16, 16 + OUT, sprite(d.back), 0, 0, 16, 16);
            if (d.side != null) {
                q.face(Direction.EAST, 16 + OUT, 0, 0, 16 + OUT, 16, 16, sprite(d.side), 0, 0, 16, 16, 0xFFD8D8D8);
                q.face(Direction.WEST, -OUT, 0, 0, -OUT, 16, 16, sprite(d.side), 0, 0, 16, 16, 0xFFD8D8D8);
            }
            if (d.top != null) q.face(Direction.UP, 0, 16 + OUT, 0, 16, 16 + OUT, 16, sprite(d.top), 0, 0, 16, 16);
        });
        pose.popPose();
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return ShipSprites.get(id);
    }
}
