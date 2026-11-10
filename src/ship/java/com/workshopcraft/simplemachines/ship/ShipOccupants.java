package com.workshopcraft.simplemachines.ship;

import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * The cryopod occupant list: every data/<ns>/simplemachines/cryopod_occupants/*.json ("occupants": [names]) in file
 * order, then ship_cryopod_occupants from the server config (comma separated). Pods take names in turn.
 */
public final class ShipOccupants {
    private ShipOccupants() {}

    public static List<String> all(MinecraftServer server) {
        return ShipResources.occupants(server);
    }

    public static String next(MinecraftServer server) {
        final List<String> all = all(server);
        if (all.isEmpty()) return "";
        return all.get(Math.floorMod(ShipData.get(server).takeOccupant(), all.size()));
    }
}
