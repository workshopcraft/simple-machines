package com.workshopcraft.simplemachines.neoforge.portal;

import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.client.EmergencyRocketRenderer;
import com.workshopcraft.simplemachines.portal.client.GateScreen;
import com.workshopcraft.simplemachines.portal.client.ResearchScreen;
import com.workshopcraft.simplemachines.portal.client.RiftHud;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class PortalNeoForgeClient {
    private PortalNeoForgeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(Portal.GATE_MENU, GateScreen::new);
            e.register(Portal.RESEARCH_MENU, ResearchScreen::new);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(Portal.emergencyRocketEntity, EmergencyRocketRenderer::new));
        PortalNeoForge.hudReceiver = RiftHud::receive;
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY,
                Identifier.fromNamespaceAndPath("simplemachines", "rift_hud"), (graphics, delta) -> RiftHud.render(graphics)));
    }
}
