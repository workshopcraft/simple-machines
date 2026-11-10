package com.workshopcraft.simplemachines.portal.client;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.item.PowerTooltip;
import com.workshopcraft.simplemachines.portal.GateMenu;
import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class GateScreen extends AbstractContainerScreen<GateMenu> {
    private static final Identifier TEXTURE = SimpleMachines.id("textures/gui/portal_gate_controller.png");
    private static final int BAR_X = 153, BAR_Y = 17, BAR_W = 14, BAR_H = 52;
    private static final int TEXT = 0xFF404040;
    private static final String[] FRAMES = {"none", "iron", "steel", "titanium", "netherite"};
    private Button open;

    public GateScreen(GateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        open = addRenderableWidget(Button.builder(label(), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                closing() ? GateMenu.BUTTON_CLOSE : GateMenu.BUTTON_OPEN)).bounds(leftPos + 96, topPos + 60, 52, 18).build());
        open.active = closing() || menu.playersMayOpen();
    }

    private boolean closing() {
        return menu.open() || menu.riftOpen();
    }

    private Component label() {
        return Component.translatable(menu.open() ? "gui.simplemachines.portal.close"
                : menu.riftOpen() ? "gui.simplemachines.portal.close_rift" : "gui.simplemachines.portal.open");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        open.setMessage(label());
        open.active = closing() || menu.playersMayOpen();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        final int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        // closed mode has no card slot, so its frame is painted over
        if (menu.closedMode()) graphics.fill(x + 7, y + 39, x + 25, y + 57, 0xFFC6C6C6);
        final int h = Mth.ceil(menu.energyFraction() * BAR_H);
        if (h > 0) graphics.fillGradient(x + BAR_X, y + BAR_Y + BAR_H - h, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFFB388FF, 0xFF5E35B1);
        if (menu.needEnergy() > 0) {
            final int mark = y + BAR_Y + BAR_H - Mth.clamp(Mth.ceil((float) menu.needEnergy() / menu.capacity() * BAR_H), 0, BAR_H);
            graphics.fill(x + BAR_X - 2, mark, x + BAR_X + BAR_W + 2, mark + 1, 0xFFFFD54F);
        }
        if (menu.open()) {
            final int w = Mth.ceil(menu.openFraction() * 52);
            graphics.fill(x + 96, y + 80, x + 96 + w, y + 82, 0xFF7C4DFF);
        }
        PortalBar.tag(graphics, font, x, y, BAR_X, BAR_Y, BAR_W, menu.voltage());
        if (mouseX >= x + BAR_X && mouseX < x + BAR_X + BAR_W && mouseY >= y + BAR_Y && mouseY < y + BAR_Y + BAR_H) {
            PortalBar.hover(graphics, font, menu.energy(), menu.capacity(), menu.voltage(), false, mouseX, mouseY, java.util.List.of(
                    Component.translatable("gui.simplemachines.portal.hv_only").withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("gui.simplemachines.portal.hv_warning").withStyle(net.minecraft.ChatFormatting.RED)));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, title, 8, 6, TEXT, false);
        // no inventory label: the pellet row sits where it would go
        final int status = menu.status();
        if (menu.riftOpen() && !menu.open()) {
            // the rift's fuel is what matters while someone is on the far side
            final int left = menu.riftSeconds();
            final Component t = Component.translatable(menu.riftLow() ? "gui.simplemachines.portal.rift_low" : "gui.simplemachines.portal.rift_open",
                    left < 0 ? "..." : clock(left));
            graphics.text(font, t, 30, 20, menu.riftLow() ? 0xFFC75000 : 0xFF2E7D32, false);
        } else {
            final int color = status == GateControllerBlockEntity.OK || status == GateControllerBlockEntity.OPEN ? 0xFF2E7D32 : 0xFFB02020;
            graphics.text(font, Component.translatable("gui.simplemachines.portal.status." + GateControllerBlockEntity.STATUS[status]), 30, 20, color, false);
        }
        final int tier = Mth.clamp(menu.frameTier(), 0, FRAMES.length - 1);
        final Component frame = tier == 0 ? Component.translatable("gui.simplemachines.portal.frame_none")
                : Component.translatable("gui.simplemachines.portal.frame", Component.translatable("gui.simplemachines.portal.tier." + FRAMES[tier]),
                menu.frameWidth(), menu.frameHeight());
        graphics.text(font, frame, 30, 31, TEXT, false);
        if (menu.needPellets() > 0) {
            graphics.text(font, Component.translatable(menu.needPellets() == 1 ? "gui.simplemachines.portal.cost_one" : "gui.simplemachines.portal.cost",
                    menu.needPellets(), shortFe(menu.needEnergy())), 30, 42, TEXT, false);
        }
        if (menu.hasOwner()) {
            final String who = Component.translatable(menu.isPrivate() ? "gui.simplemachines.research.owner_private" : "gui.simplemachines.research.owner_public",
                    ownerName(menu.owner())).getString();
            graphics.text(font, font.plainSubstrByWidth(who, BAR_X - 32), 30, 51, 0xFF2D5AA0, false);
        }
    }

    private Component ownerName(java.util.UUID id) {
        if (minecraft.player != null && minecraft.player.getUUID().equals(id)) return Component.literal(minecraft.player.getGameProfile().name());
        final var info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
        return info != null ? Component.literal(info.getProfile().name()) : Component.translatable("gui.simplemachines.entangled_someone");
    }

    /** 95 as 1:35, 3725 as 1:02:05. */
    static String clock(int seconds) {
        final int h = seconds / 3600, m = seconds / 60 % 60, sec = seconds % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
    }

    /** 1500000 as 1.5M, so the cost fits beside the energy bar. */
    static String shortFe(long fe) {
        if (fe >= 1_000_000) return trim(fe / 1_000_000.0) + "M";
        if (fe >= 1_000) return trim(fe / 1_000.0) + "k";
        return Long.toString(fe);
    }

    private static String trim(double v) {
        final String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
