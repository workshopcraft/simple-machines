package com.workshopcraft.simplemachines.ship;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Data pack files under data/<namespace>/simplemachines/..., read when first needed and again after /reload. */
final class ShipResources {
    private static ResourceManager seen;
    private static Map<Identifier, Identifier> repairs = Map.of();
    private static List<String> occupants = List.of();

    private ShipResources() {}

    private static synchronized void refresh(MinecraftServer server) {
        final ResourceManager rm = server.getResourceManager();
        if (rm == seen) return;
        seen = rm;
        final Map<Identifier, Identifier> pairs = new LinkedHashMap<>();
        final List<String> people = new ArrayList<>();
        for (JsonObject o : read(rm, "simplemachines/repairs")) {
            if (!o.has("pairs")) continue;
            for (JsonElement e : o.getAsJsonArray("pairs")) {
                final JsonObject p = e.getAsJsonObject();
                final Identifier broken = Identifier.tryParse(p.get("broken").getAsString());
                final Identifier fixed = Identifier.tryParse(p.get("fixed").getAsString());
                if (broken != null && fixed != null) pairs.put(broken, fixed);
            }
        }
        for (JsonObject o : read(rm, "simplemachines/cryopod_occupants")) {
            if (!o.has("occupants")) continue;
            for (JsonElement e : o.getAsJsonArray("occupants")) people.add(e.getAsString().trim());
        }
        repairs = Map.copyOf(pairs);
        occupants = List.copyOf(people);
    }

    private static List<JsonObject> read(ResourceManager rm, String dir) {
        final List<JsonObject> out = new ArrayList<>();
        final Map<Identifier, Resource> found = new TreeMap<>(rm.listResources(dir, id -> id.getPath().endsWith(".json")));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                out.add(JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception ex) {
                LogUtils.getLogger().warn("Simple Machines could not read {}: {}", e.getKey(), ex.toString());
            }
        }
        return out;
    }

    static Map<Identifier, Identifier> repairs(MinecraftServer server) {
        refresh(server);
        return repairs;
    }

    static List<String> occupants(MinecraftServer server) {
        refresh(server);
        final List<String> all = new ArrayList<>(occupants);
        for (String s : ShipConfig.string(server, ShipConfig.POD_OCCUPANTS).split(",")) if (!s.isBlank()) all.add(s.trim());
        return all;
    }
}
