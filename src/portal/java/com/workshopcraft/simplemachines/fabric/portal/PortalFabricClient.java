package com.workshopcraft.simplemachines.fabric.portal;

import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.client.GateScreen;
import com.workshopcraft.simplemachines.portal.client.ResearchScreen;
import net.minecraft.client.gui.screens.MenuScreens;

public final class PortalFabricClient {
    private PortalFabricClient() {}

    public static void init() {
        MenuScreens.register(Portal.GATE_MENU, GateScreen::new);
        MenuScreens.register(Portal.RESEARCH_MENU, ResearchScreen::new);
    }
}
