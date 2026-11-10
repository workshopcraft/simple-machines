package com.workshopcraft.simplemachines.portal.client;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.portal.ResearchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ResearchScreen extends AbstractContainerScreen<ResearchMenu> {
    private static final Identifier TEXTURE = SimpleMachines.id("textures/gui/research_station.png");
    private static final int BAR_X = 153, BAR_Y = 17, BAR_W = 14, BAR_H = 52;
    private static final int TEXT = 0xFF404040;

    public ResearchScreen(ResearchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> click(ResearchMenu.BUTTON_PREV)).bounds(leftPos + 46, topPos + 18, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> click(ResearchMenu.BUTTON_NEXT)).bounds(leftPos + 132, topPos + 18, 14, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.simplemachines.research.imprint"), b -> click(ResearchMenu.BUTTON_IMPRINT))
                .bounds(leftPos + 62, topPos + 52, 50, 18).build());
    }

    private void click(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        final int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        final int h = Mth.ceil(menu.energyFraction() * BAR_H);
        if (h > 0) graphics.fillGradient(x + BAR_X, y + BAR_Y + BAR_H - h, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFFF4785C, 0xFF9E2A22);
        final float done = menu.unlocked() ? 1 : menu.need() <= 0 ? 0 : Mth.clamp((float) menu.count() / menu.need(), 0, 1);
        graphics.fill(x + 46, y + 44, x + 146, y + 48, 0xFF3A3A3A);
        graphics.fill(x + 46, y + 44, x + 46 + Mth.ceil(done * 100), y + 48, menu.unlocked() ? 0xFF43A047 : 0xFF26A69A);
        final int w = Mth.ceil(menu.itemProgress() * 16);
        if (w > 0) graphics.fill(x + 26, y + 71, x + 26 + w, y + 73, 0xFFFFB74D);
        PortalBar.tag(graphics, font, x, y, BAR_X, BAR_Y, BAR_W, menu.voltage());
        if (mouseX >= x + BAR_X && mouseX < x + BAR_X + BAR_W && mouseY >= y + BAR_Y && mouseY < y + BAR_Y + BAR_H) {
            PortalBar.hover(graphics, font, menu.energy(), menu.capacity(), menu.voltage(), false, mouseX, mouseY);
        }
    }

    private Component ownerName(java.util.UUID id) {
        if (minecraft.player != null && minecraft.player.getUUID().equals(id)) return Component.literal(minecraft.player.getGameProfile().name());
        final var info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
        return info != null ? Component.literal(info.getProfile().name()) : Component.translatable("gui.simplemachines.entangled_someone");
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, title, 8, 6, TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        if (menu.hasOwner()) {
            final Component who = Component.translatable(menu.isPrivate() ? "gui.simplemachines.research.owner_private" : "gui.simplemachines.research.owner_public",
                    ownerName(menu.owner()));
            graphics.text(font, who, imageWidth - 8 - font.width(who), inventoryLabelY, 0xFF2D5AA0, false);
        }
        final ItemStack shown = menu.shown();
        if (shown.isEmpty()) {
            graphics.text(font, Component.translatable("gui.simplemachines.research.none"), 62, 21, TEXT, false);
            return;
        }
        final String full = shown.getHoverName().getString();
        final String name = font.width(full) <= 68 ? full : font.plainSubstrByWidth(full, 60) + "..";
        graphics.text(font, Component.literal(name), 62, 21, TEXT, false);
        final Component line = menu.unlocked() ? Component.translatable("gui.simplemachines.research.done")
                : Component.translatable(menu.mob() ? "gui.simplemachines.research.scans" : "gui.simplemachines.research.items",
                menu.count(), menu.need());
        graphics.text(font, line, 46, 34, menu.unlocked() ? 0xFF2E7D32 : TEXT, false);
        graphics.text(font, Component.literal((menu.index() + 1) + "/" + menu.total()), 120, 6, 0xFF808080, false);
    }
}
