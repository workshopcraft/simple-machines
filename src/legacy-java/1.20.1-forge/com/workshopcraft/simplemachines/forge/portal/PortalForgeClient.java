package com.workshopcraft.simplemachines.forge.portal;

import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.client.EmergencyRocketRenderer;
import com.workshopcraft.simplemachines.portal.client.GateScreen;
import com.workshopcraft.simplemachines.portal.client.ResearchScreen;
import com.workshopcraft.simplemachines.portal.client.RiftHud;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class PortalForgeClient {
    private PortalForgeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            MenuScreens.register(Portal.GATE_MENU, GateScreen::new);
            MenuScreens.register(Portal.RESEARCH_MENU, ResearchScreen::new);
        }));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(Portal.emergencyRocketEntity, EmergencyRocketRenderer::new));
        PortalForge.hudReceiver = RiftHud::receive;
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "rift_hud",
                (gui, graphics, partialTick, width, height) -> RiftHud.render(graphics)));
    }
}
