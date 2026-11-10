package com.workshopcraft.simplemachines.fabric.portal;

import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.RiftHudPayload;
import com.workshopcraft.simplemachines.portal.client.GateScreen;
import com.workshopcraft.simplemachines.portal.client.ResearchScreen;
import com.workshopcraft.simplemachines.portal.client.RiftHud;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;

public final class PortalFabricClient {
    private PortalFabricClient() {}

    public static void init() {
        MenuScreens.register(Portal.GATE_MENU, GateScreen::new);
        MenuScreens.register(Portal.RESEARCH_MENU, ResearchScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(RiftHudPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> RiftHud.receive(payload)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("simplemachines", "rift_hud"),
                (graphics, delta) -> RiftHud.render(graphics));
    }
}
