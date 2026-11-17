package com.workshopcraft.simplemachines.portal.client;

import com.workshopcraft.simplemachines.cable.Voltage;
import com.workshopcraft.simplemachines.item.PowerTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Copy of the machine energy bar tag and hover lines (that class is package private in main). */
final class PortalBar {
    private PortalBar() {
    }

    static void tag(GuiGraphics graphics, Font font, int x, int y, int barX, int barY, int barW, int tier) {
        if (tier < 0) return;
        final String s = Voltage.NAME[tier];
        final int ty = y + barY - 10;
        graphics.fill(x + barX - 1, ty - 2, x + barX + barW + 1, ty + 9, 0xFF373737);
        graphics.drawString(font, s, x + barX + (barW - font.width(s) + 1) / 2, ty, 0xFF000000 | Voltage.COLOR[tier], false);
    }

    static void hover(GuiGraphics graphics, Font font, int energy, int capacity, int tier, boolean sendsOnly, int mouseX, int mouseY) {
        hover(graphics, font, energy, capacity, tier, sendsOnly, mouseX, mouseY, List.of());
    }

    static void hover(GuiGraphics graphics, Font font, int energy, int capacity, int tier, boolean sendsOnly, int mouseX, int mouseY,
                      List<Component> extra) {
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(PowerTooltip.num(energy) + " / " + PowerTooltip.num(capacity) + " FE"));
        if (tier >= 0) lines.add(Component.translatable(sendsOnly ? "gui.simplemachines.tier_sends" : "gui.simplemachines.tier_accepts",
                PowerTooltip.tier(tier), PowerTooltip.num(Voltage.RATE[tier])).withStyle(ChatFormatting.GRAY));
        lines.addAll(extra);
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen != null) mc.screen.setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList());
    }
}
