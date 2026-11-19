package com.workshopcraft.simplemachines.fabric.portal;

import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.RiftHudPayload;
import com.workshopcraft.simplemachines.portal.client.EmergencyRocketRenderer;
import com.workshopcraft.simplemachines.portal.client.GateScreen;
import com.workshopcraft.simplemachines.portal.client.ResearchScreen;
import com.workshopcraft.simplemachines.portal.client.RiftHud;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//?} else
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;

public final class PortalFabricClient {
    private PortalFabricClient() {}

    public static void init() {
        MenuScreens.register(Portal.GATE_MENU, GateScreen::new);
        MenuScreens.register(Portal.RESEARCH_MENU, ResearchScreen::new);
        EntityRendererRegistry.register(Portal.emergencyRocketEntity, EmergencyRocketRenderer::new);
        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(RiftHudPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> RiftHud.receive(payload)));
        //?} else {
        /*ClientPlayNetworking.registerGlobalReceiver(RiftHudPayload.TYPE.id(), (client, handler, buf, sender) -> {
            final RiftHudPayload payload = RiftHudPayload.CODEC.decode(buf);
            client.execute(() -> RiftHud.receive(payload));
        });
        *///?}
        //? if >=26.1 {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath("simplemachines", "rift_hud"),
                (graphics, delta) -> RiftHud.render(graphics));
        //?} else
        /*HudRenderCallback.EVENT.register((graphics, delta) -> RiftHud.render(graphics));*/
    }
}
