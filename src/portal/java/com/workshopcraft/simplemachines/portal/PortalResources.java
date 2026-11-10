package com.workshopcraft.simplemachines.portal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.workshopcraft.simplemachines.portal.world.WorldPreset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Portal data pack files, read when first needed and again after /reload:
 * simplemachines/portal_presets, simplemachines/portal_cards and simplemachines/portal_research.
 */
public final class PortalResources {
    private static ResourceManager seen;
    private static Map<Identifier, WorldPreset> presets = Map.of();
    private static Map<Identifier, PortalCard> cards = Map.of();
    private static Map<Identifier, Research> research = Map.of();

    private PortalResources() {}

    private static synchronized void refresh(MinecraftServer server) {
        final ResourceManager rm = server.getResourceManager();
        if (rm == seen) return;
        seen = rm;
        final Map<Identifier, WorldPreset> p = new LinkedHashMap<>();
        read(rm, "simplemachines/portal_presets", (id, o) -> p.put(id, WorldPreset.parse(id, o)));
        final Map<Identifier, PortalCard> c = new LinkedHashMap<>();
        read(rm, "simplemachines/portal_cards", (id, o) -> c.put(id, PortalCard.parse(id, o)));
        final Map<Identifier, Research> r = new LinkedHashMap<>();
        read(rm, "simplemachines/portal_research", (id, o) -> r.put(id, Research.parse(id, o)));
        presets = Map.copyOf(p);
        cards = Map.copyOf(c);
        research = Map.copyOf(r);
        LogUtils.getLogger().info("Simple Machines portals: {} world presets, {} cards, {} research entries", p.size(), c.size(), r.size());
    }

    private static void read(ResourceManager rm, String dir, BiConsumer<Identifier, JsonObject> out) {
        final Map<Identifier, Resource> found = new TreeMap<>(rm.listResources(dir, id -> id.getPath().endsWith(".json")));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            final String path = e.getKey().getPath();
            final Identifier id = Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(dir.length() + 1, path.length() - 5));
            try (Reader r = e.getValue().openAsReader()) {
                out.accept(id, JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception ex) {
                LogUtils.getLogger().warn("Simple Machines could not read {}: {}", e.getKey(), ex.toString());
            }
        }
    }

    public static Map<Identifier, WorldPreset> presets(MinecraftServer server) {
        refresh(server);
        return presets;
    }

    public static Map<Identifier, PortalCard> cards(MinecraftServer server) {
        refresh(server);
        return cards;
    }

    public static Map<Identifier, Research> research(MinecraftServer server) {
        refresh(server);
        return research;
    }
}
