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
                menu.open() ? GateMenu.BUTTON_CLOSE : GateMenu.BUTTON_OPEN)).bounds(leftPos + 96, topPos + 60, 52, 18).build());
    }

    private Component label() {
        return Component.translatable(menu.open() ? "gui.simplemachines.portal.close" : "gui.simplemachines.portal.open");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        open.setMessage(label());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        final int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
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
            PortalBar.hover(graphics, font, menu.energy(), menu.capacity(), menu.voltage(), false, mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, title, 8, 6, TEXT, false);
        // no inventory label: the pellet row sits where it would go
        final int status = menu.status();
        final int color = status == GateControllerBlockEntity.OK || status == GateControllerBlockEntity.OPEN ? 0xFF2E7D32 : 0xFFB02020;
        graphics.text(font, Component.translatable("gui.simplemachines.portal.status." + GateControllerBlockEntity.STATUS[status]), 30, 20, color, false);
        final int tier = Mth.clamp(menu.frameTier(), 0, FRAMES.length - 1);
        final Component frame = tier == 0 ? Component.translatable("gui.simplemachines.portal.frame_none")
                : Component.translatable("gui.simplemachines.portal.frame", Component.translatable("gui.simplemachines.portal.tier." + FRAMES[tier]),
                menu.frameWidth(), menu.frameHeight());
        graphics.text(font, frame, 30, 31, TEXT, false);
        if (menu.needPellets() > 0) {
            graphics.text(font, Component.translatable(menu.needPellets() == 1 ? "gui.simplemachines.portal.cost_pellet"
                    : "gui.simplemachines.portal.cost_pellets", menu.needPellets()), 30, 42, TEXT, false);
            graphics.text(font, Component.translatable("gui.simplemachines.portal.cost_energy", shortFe(menu.needEnergy())), 30, 51, TEXT, false);
        }
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
