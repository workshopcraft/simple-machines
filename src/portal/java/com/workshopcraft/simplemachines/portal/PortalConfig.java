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
    /** Off: closed mode for every gate. Players cannot insert or program cards; only the API, commands and quests pick worlds. */
    public static final String PLAYER_PROGRAMMING = "portal_player_programming";
    /** In closed mode players may still press Open and Close once a destination is set. Off: only the API and commands open gates. */
    public static final String CLOSED_PLAYER_OPEN = "portal_closed_mode_player_open";
    /** In closed mode a hidden jump card makes a new world on every jump instead of binding to its first one. */
    public static final String CLOSED_NEW_WORLDS = "portal_closed_mode_new_world_each_jump";
    /** FE a second the gate pays to hold a return rift open, times the world tier. */
    public static final String RIFT_ENERGY = "portal_rift_energy_per_second";
    /** Seconds of rift time one Entangled Pellet buys. */
    public static final String RIFT_PELLET_SECONDS = "portal_rift_seconds_per_pellet";
    /** The rift flickers once it has this many seconds of fuel left. */
    public static final String RIFT_WARNING = "portal_rift_warning_seconds";
    /** The rift closes by itself this long after everyone who came through it has gone back. 0 keeps it open until fuel runs out. */
    public static final String RIFT_EMPTY_CLOSE = "portal_rift_empty_close_seconds";
    /** The gate keeps its own chunk loaded while its rift is open, so the drain and the timer keep running. */
    public static final String RIFT_CHUNK_LOADING = "portal_rift_chunk_loading";
    /** One-off worlds (closed mode jumps, API warps) are deleted once their rift has closed and nobody is inside. True keeps them. */
    public static final String KEEP_ONE_OFF_WORLDS = "portal_keep_one_off_worlds";

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
        ServerConfig.define(PLAYER_PROGRAMMING, "true");
        ServerConfig.define(CLOSED_PLAYER_OPEN, "true");
        ServerConfig.define(CLOSED_NEW_WORLDS, "true");
        ServerConfig.define(RIFT_ENERGY, "20000");
        ServerConfig.define(RIFT_PELLET_SECONDS, "60");
        ServerConfig.define(RIFT_WARNING, "30");
        ServerConfig.define(RIFT_EMPTY_CLOSE, "15");
        ServerConfig.define(RIFT_CHUNK_LOADING, "true");
        ServerConfig.define(KEEP_ONE_OFF_WORLDS, "false");
    }

    public static boolean bool(MinecraftServer server, String key) {
        return server != null && ServerConfig.bool(server, key);
    }

    public static int integer(MinecraftServer server, String key) {
        return server == null ? 0 : ServerConfig.integer(server, key);
    }
}
