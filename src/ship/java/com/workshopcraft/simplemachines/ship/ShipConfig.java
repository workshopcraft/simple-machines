package com.workshopcraft.simplemachines.ship;

import com.workshopcraft.simplemachines.ServerConfig;
import net.minecraft.server.MinecraftServer;

/** Ship block keys in config/simplemachines-server.properties. */
public final class ShipConfig {
    public static final String BULKHEADS_NEED_TOOL = "ship_bulkheads_need_tool";
    public static final String BULKHEAD_MAX_REMOVAL = "ship_bulkhead_max_removal";
    public static final String START_IN_CRYOPOD = "ship_start_in_cryopod";
    public static final String POD_OCCUPANTS = "ship_cryopod_occupants";
    public static final String HAZARDS = "ship_hazard_damage";
    public static final String LOGS_EDITABLE = "ship_story_logs_editable";

    private ShipConfig() {}

    static void init() {
        ServerConfig.define(BULKHEADS_NEED_TOOL, "false");
        ServerConfig.define(BULKHEAD_MAX_REMOVAL, "64");
        ServerConfig.define(START_IN_CRYOPOD, "true");
        ServerConfig.define(POD_OCCUPANTS, "");
        ServerConfig.define(HAZARDS, "true");
        ServerConfig.define(LOGS_EDITABLE, "true");
    }

    public static boolean bool(MinecraftServer server, String key) {
        return server != null && ServerConfig.bool(server, key);
    }

    public static String string(MinecraftServer server, String key) {
        return server == null ? "" : ServerConfig.string(server, key);
    }

    public static int integer(MinecraftServer server, String key) {
        return server == null ? 0 : ServerConfig.integer(server, key);
    }
}
