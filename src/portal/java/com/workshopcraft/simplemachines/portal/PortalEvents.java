package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.portal.world.PortalWorlds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server lifecycle hooks, called by each loader's glue. */
public final class PortalEvents {
    private PortalEvents() {}

    public static void serverTickEnd(MinecraftServer server) {
        PortalWorlds.tick(server);
        PortalTravel.tick(server);
    }

    public static void serverStarted(MinecraftServer server) {
        PortalWorlds.serverStarted(server);
        PortalRifts.restore(server);
    }

    public static void serverStopped(MinecraftServer server) {
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
