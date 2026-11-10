package com.workshopcraft.simplemachines.fabric.ship;

import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.client.CountdownRenderer;
import com.workshopcraft.simplemachines.ship.client.CryopodRenderer;
import com.workshopcraft.simplemachines.ship.client.SlidingDoorRenderer;
import com.workshopcraft.simplemachines.ship.client.TerminalRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class ShipFabricClient {
    private ShipFabricClient() {}

    public static void init() {
        BlockEntityRenderers.register(Ship.SLIDING_DOOR_ENTITY, SlidingDoorRenderer::new);
        BlockEntityRenderers.register(Ship.CRYOPOD_ENTITY, CryopodRenderer::new);
        BlockEntityRenderers.register(Ship.TERMINAL_ENTITY, TerminalRenderer::new);
        BlockEntityRenderers.register(Ship.COUNTDOWN_ENTITY, CountdownRenderer::new);
    }
}
