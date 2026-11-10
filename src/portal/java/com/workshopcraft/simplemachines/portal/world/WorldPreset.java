package com.workshopcraft.simplemachines.portal.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.workshopcraft.simplemachines.portal.FrameTier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One kind of portal world, read from data/&lt;namespace&gt;/simplemachines/portal_presets/&lt;name&gt;.json.
 * Biomes and terrain may name End and Nether parts; ores, mobs and the objective site are added on top.
 */
public record WorldPreset(Identifier id, String name, String terrain, @Nullable Identifier dimensionType, List<String> biomes,
                          List<Ore> ores, Map<MobCategory, List<Mob>> mobs, boolean replaceMobs, @Nullable Identifier loot,
                          FrameTier minFrame, int tier, float richness, int weight, boolean needsResearch,
                          @Nullable Objective objective, Identifier surfaceBlock) {

    /** An ore vein added during decoration. perChunk is scaled by the world richness. */
    public record Ore(Identifier block, float perChunk, int size, int minY, int maxY) {}

    public record Mob(Identifier type, int weight, int min, int max) {}

    /** A site placed during worldgen at distance blocks from the landing spot, with an objective core at its centre. */
    public record Objective(@Nullable Identifier structure, Identifier block, int distance, Identifier siteBlock) {}

    public static WorldPreset parse(Identifier id, JsonObject o) {
        final List<String> biomes = new ArrayList<>();
        if (o.has("biomes")) for (JsonElement e : o.getAsJsonArray("biomes")) biomes.add(e.getAsString());
        if (biomes.isEmpty()) biomes.add("minecraft:plains");
        final List<Ore> ores = new ArrayList<>();
        if (o.has("ores")) for (JsonElement e : o.getAsJsonArray("ores")) ores.add(ore(e.getAsJsonObject()));
        final Map<MobCategory, List<Mob>> mobs = new EnumMap<>(MobCategory.class);
        if (o.has("mobs")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("mobs").entrySet()) {
                final MobCategory cat = category(e.getKey());
                if (cat == null) continue;
                final List<Mob> list = new ArrayList<>();
                for (JsonElement m : e.getValue().getAsJsonArray()) list.add(mob(m.getAsJsonObject()));
                mobs.put(cat, list);
            }
        }
        Objective objective = null;
        if (o.has("objective")) {
            final JsonObject j = o.getAsJsonObject("objective");
            objective = new Objective(optId(j, "structure"), idOr(j, "block", "simplemachines:objective_core"),
                    GsonHelper.getAsInt(j, "distance", 96), idOr(j, "site_block", "minecraft:polished_andesite"));
        }
        return new WorldPreset(id, GsonHelper.getAsString(o, "name", id.getPath()), GsonHelper.getAsString(o, "terrain", "overworld"),
                optId(o, "dimension_type"), List.copyOf(biomes), List.copyOf(ores), mobs, GsonHelper.getAsBoolean(o, "replace_mobs", false),
                optId(o, "loot"), FrameTier.byName(GsonHelper.getAsString(o, "min_frame", "iron")), Math.max(1, GsonHelper.getAsInt(o, "tier", 1)),
                Math.max(0, GsonHelper.getAsFloat(o, "richness", 1.0F)), Math.max(0, GsonHelper.getAsInt(o, "weight", 10)),
                GsonHelper.getAsBoolean(o, "requires_research", false), objective, idOr(o, "surface_block", "minecraft:grass_block"));
    }

    public static Ore ore(JsonObject j) {
        return new Ore(Identifier.parse(GsonHelper.getAsString(j, "block")), GsonHelper.getAsFloat(j, "per_chunk", 4),
                GsonHelper.getAsInt(j, "size", 6), GsonHelper.getAsInt(j, "min_y", -48), GsonHelper.getAsInt(j, "max_y", 48));
    }

    public static Mob mob(JsonObject j) {
        return new Mob(Identifier.parse(GsonHelper.getAsString(j, "type")), GsonHelper.getAsInt(j, "weight", 10),
                GsonHelper.getAsInt(j, "min", 1), GsonHelper.getAsInt(j, "max", 3));
    }

    static @Nullable MobCategory category(String name) {
        for (MobCategory c : MobCategory.values()) if (c.getName().equalsIgnoreCase(name) || c.name().equalsIgnoreCase(name)) return c;
        return null;
    }

    private static @Nullable Identifier optId(JsonObject o, String key) {
        return o.has(key) ? Identifier.tryParse(o.get(key).getAsString()) : null;
    }

    private static Identifier idOr(JsonObject o, String key, String fallback) {
        final Identifier id = optId(o, key);
        return id == null ? Identifier.parse(fallback) : id;
    }

    /** The vanilla noise settings for a terrain style; any other value is read as a noise_settings id. */
    public Identifier noiseSettings() {
        return switch (terrain.toLowerCase(Locale.ROOT)) {
            case "overworld", "surface", "normal" -> Identifier.withDefaultNamespace("overworld");
            case "amplified" -> Identifier.withDefaultNamespace("amplified");
            case "large_biomes" -> Identifier.withDefaultNamespace("large_biomes");
            case "caves", "caverns" -> Identifier.withDefaultNamespace("caves");
            case "floating_islands", "islands" -> Identifier.withDefaultNamespace("floating_islands");
            case "nether" -> Identifier.withDefaultNamespace("nether");
            case "end" -> Identifier.withDefaultNamespace("end");
            default -> Identifier.parse(terrain);
        };
    }

    /** The dimension type: sky, light and height. Must match the noise settings height. */
    public Identifier type() {
        if (dimensionType != null) return dimensionType;
        final String n = noiseSettings().getPath();
        return com.workshopcraft.simplemachines.SimpleMachines.id(switch (n) {
            case "nether" -> "portal_cavern";
            case "caves" -> "portal_deep";
            case "end", "floating_islands" -> "portal_void";
            default -> "portal_surface";
        });
    }

    static JsonArray array(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }
}
