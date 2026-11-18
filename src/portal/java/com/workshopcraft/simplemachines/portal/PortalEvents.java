package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.portal.world.PortalWorlds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server lifecycle hooks, called by each loader's glue. */
public final class PortalEvents {
    private PortalEvents() {}

    public static void serverStarting(MinecraftServer server) {
        PortalResources.bind(server);
    }

    public static void serverTickEnd(MinecraftServer server) {
        PortalResources.bind(server);
        PortalWorlds.tick(server);
        PortalTravel.tick(server);
        PortalRifts.keepAwake(server);
    }

    public static void serverStarted(MinecraftServer server) {
        PortalResources.bind(server);
        PortalWorlds.serverStarted(server);
        PortalRifts.restore(server);
    }

    public static void serverStopped(MinecraftServer server) {
        PortalResources.unbind(server);
        PortalWorlds.serverStopped();
        PortalTravel.serverStopped();
        PortalRifts.serverStopped();
    }

    public static void join(ServerPlayer p) {
        PortalTravel.loggedIn(p);
    }

    public static void leave(ServerPlayer p) {
        PortalTravel.loggedOut(p);
    }
}
