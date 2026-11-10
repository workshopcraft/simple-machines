package com.workshopcraft.simplemachines.neoforge.ship;

import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.client.CountdownRenderer;
import com.workshopcraft.simplemachines.ship.client.CryopodRenderer;
import com.workshopcraft.simplemachines.ship.client.SlidingDoorRenderer;
import com.workshopcraft.simplemachines.ship.client.TerminalRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ShipNeoForgeClient {
    private ShipNeoForgeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(Ship.SLIDING_DOOR_ENTITY, SlidingDoorRenderer::new);
            e.registerBlockEntityRenderer(Ship.CRYOPOD_ENTITY, CryopodRenderer::new);
            e.registerBlockEntityRenderer(Ship.TERMINAL_ENTITY, TerminalRenderer::new);
            e.registerBlockEntityRenderer(Ship.COUNTDOWN_ENTITY, CountdownRenderer::new);
        });
    }
}
