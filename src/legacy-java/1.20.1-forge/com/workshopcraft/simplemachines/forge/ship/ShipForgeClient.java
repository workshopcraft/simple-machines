package com.workshopcraft.simplemachines.forge.ship;

import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.client.CountdownRenderer;
import com.workshopcraft.simplemachines.ship.client.CryopodRenderer;
import com.workshopcraft.simplemachines.ship.client.FinishModel;
import com.workshopcraft.simplemachines.ship.client.SlidingDoorRenderer;
import com.workshopcraft.simplemachines.ship.client.TerminalRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ShipForgeClient {
    private ShipForgeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(Ship.SLIDING_DOOR_ENTITY, SlidingDoorRenderer::new);
            e.registerBlockEntityRenderer(Ship.CRYOPOD_ENTITY, CryopodRenderer::new);
            e.registerBlockEntityRenderer(Ship.TERMINAL_ENTITY, TerminalRenderer::new);
            e.registerBlockEntityRenderer(Ship.COUNTDOWN_ENTITY, CountdownRenderer::new);
        });
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(FinishModel::register));
    }
}
