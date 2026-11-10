package com.workshopcraft.simplemachines.neoforge.portal;

import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.client.GateScreen;
import com.workshopcraft.simplemachines.portal.client.ResearchScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class PortalNeoForgeClient {
    private PortalNeoForgeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(Portal.GATE_MENU, GateScreen::new);
            e.register(Portal.RESEARCH_MENU, ResearchScreen::new);
        });
    }
}
