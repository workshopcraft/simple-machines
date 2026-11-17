package com.workshopcraft.simplemachines.ship.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.simplemachines.client.RenderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

final class ShipSprites {
    private ShipSprites() {}

    static TextureAtlasSprite get(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }

    /** Turns the pose so a model drawn facing north faces the given way. */
    static void face(PoseStack pose, Direction facing) {
        pose.translate(0.5F, 0, 0.5F);
        RenderCompat.rotate(pose, Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.translate(-0.5F, 0, -0.5F);
    }
}
