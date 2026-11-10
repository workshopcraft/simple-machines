package com.workshopcraft.simplemachines.ship.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.simplemachines.client.RenderCompat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.FormattedCharSequence;

/** Text on the north face of a north-facing screen, in pixel units of the block. */
final class ScreenText {
    static final int FULL_BRIGHT = 0xF000F0;

    private ScreenText() {}

    /** Starts a text pose: (right, top) is the screen's top right corner seen from the front, scale is block units per font unit. */
    static void begin(PoseStack pose, float right, float top, float z, float scale) {
        pose.translate(right / 16F, top / 16F, z / 16F - 0.002F);
        RenderCompat.rotate(pose, Axis.YP.rotationDegrees(180F));
        pose.scale(scale, -scale, scale);
    }

    static void line(SubmitNodeCollector out, PoseStack pose, Font font, FormattedCharSequence text, float x, float y, int colour) {
        out.submitText(pose, x, y, text, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, colour, 0, 0);
    }
}
