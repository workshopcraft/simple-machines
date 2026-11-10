package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.ServerConfig;
import net.minecraft.server.MinecraftServer;

/** Portal keys in config/simplemachines-server.properties. */
public final class PortalConfig {
    /** Titanium ore in the Overworld. Portal worlds have their own ores from their presets. */
    public static final String TITANIUM_OVERWORLD = "portal_titanium_overworld";
    /** Whether netherite blocks make a frame of the top tier. */
    public static final String NETHERITE_FRAMES = "portal_netherite_frames";
    /** FE for one jump at world tier 1, richness 1 and the smallest frame. */
    public static final String JUMP_ENERGY = "portal_jump_energy";
    /** How long the field stays open after a jump starts, in seconds. */
    public static final String OPEN_SECONDS = "portal_open_seconds";
    /** Story cards are used up when their objective is collected. Set true to keep them. */
    public static final String KEEP_STORY_CARDS = "portal_keep_story_cards";
    /** The Mob Scanner kills the mob it scans. */
    public static final String SCANNER_KILLS_MOB = "scanner_kills_mob";
    /** Largest gate opening, in blocks across and up. */
    public static final String MAX_OPENING = "portal_max_opening";
    /**
     * Worlds bound to a card are never deleted. Worlds with no card (made by command) are deleted when empty
     * for portal_empty_world_seconds, but only when this is true. Off by default.
     */
    public static final String CLEANUP_UNBOUND = "portal_cleanup_unbound_worlds";
    /** Story attempt worlds left without the objective and replaced by a new attempt are deleted once empty. On by default. */
    public static final String CLEANUP_FAILED_STORY = "portal_cleanup_failed_story_worlds";
    /** Low voltage (LV/MV) feeding the HV gate drains it and burns out the cables. Off: the energy is only refused. */
    public static final String LOW_VOLTAGE_DAMAGE = "portal_low_voltage_damage";
    /** Percent of stored energy a brownout costs. */
    public static final String BROWNOUT_LOSS = "portal_brownout_loss_percent";
    public static final String EMPTY_WORLD_SECONDS = "portal_empty_world_seconds";

    private PortalConfig() {}

    static void init() {
        ServerConfig.define(TITANIUM_OVERWORLD, "true");
        ServerConfig.define(NETHERITE_FRAMES, "true");
        ServerConfig.define(JUMP_ENERGY, "1000000");
        ServerConfig.define(OPEN_SECONDS, "30");
        ServerConfig.define(KEEP_STORY_CARDS, "false");
        ServerConfig.define(SCANNER_KILLS_MOB, "false");
        ServerConfig.define(MAX_OPENING, "6");
        ServerConfig.define(CLEANUP_UNBOUND, "false");
        ServerConfig.define(CLEANUP_FAILED_STORY, "true");
        ServerConfig.define(LOW_VOLTAGE_DAMAGE, "true");
        ServerConfig.define(BROWNOUT_LOSS, "10");
        ServerConfig.define(EMPTY_WORLD_SECONDS, "120");
    }

    public static boolean bool(MinecraftServer server, String key) {
        return server != null && ServerConfig.bool(server, key);
    }

    public static int integer(MinecraftServer server, String key) {
        return server == null ? 0 : ServerConfig.integer(server, key);
    }
}
