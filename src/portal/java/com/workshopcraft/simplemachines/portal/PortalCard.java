package com.workshopcraft.simplemachines.portal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * A card definition, read from data/&lt;namespace&gt;/simplemachines/portal_cards/&lt;name&gt;.json.
 * The same definition is used by a card item in a gate's card slot and by a hidden card applied to a gate
 * with the API, the /simplemachines portal applycard command, or a Simple Books reward.
 *
 * kind "jump": rolls a preset from a weighted pool on every jump; the card is never used up.
 * kind "story": a fixed preset with an objective. Used up when the objective is collected, unless config or the card says keep.
 * kind "nether" / "end": links to the real vanilla dimension.
 */
public record PortalCard(Identifier id, Kind kind, String name, List<PoolEntry> pool, @Nullable Identifier preset,
                         @Nullable Boolean keepOnSuccess, List<String> forced, @Nullable FrameTier minFrame) {

    public enum Kind {
        JUMP, STORY, NETHER, END;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind byName(String s) {
            for (Kind k : values()) if (k.id().equalsIgnoreCase(s)) return k;
            return JUMP;
        }
    }

    /** One pool choice. An entry may name a preset, or "*" for every preset that is not research locked, using each preset's own weight. */
    public record PoolEntry(String preset, int weight) {}

    public static PortalCard parse(Identifier id, JsonObject o) {
        final List<PoolEntry> pool = new ArrayList<>();
        if (o.has("pool")) {
            for (JsonElement e : o.getAsJsonArray("pool")) {
                if (e.isJsonPrimitive()) pool.add(new PoolEntry(e.getAsString(), -1));
                else pool.add(new PoolEntry(GsonHelper.getAsString(e.getAsJsonObject(), "preset"), GsonHelper.getAsInt(e.getAsJsonObject(), "weight", -1)));
            }
        }
        final List<String> forced = new ArrayList<>();
        if (o.has("forced")) for (JsonElement e : o.getAsJsonArray("forced")) forced.add(e.getAsString());
        return new PortalCard(id, Kind.byName(GsonHelper.getAsString(o, "kind", "jump")), GsonHelper.getAsString(o, "name", id.getPath()),
                List.copyOf(pool), o.has("preset") ? Identifier.tryParse(o.get("preset").getAsString()) : null,
                o.has("keep_on_success") ? GsonHelper.getAsBoolean(o, "keep_on_success") : null, List.copyOf(forced),
                o.has("min_frame") ? FrameTier.byName(GsonHelper.getAsString(o, "min_frame")) : null);
    }

    /**
     * Picks the preset for one jump. Story cards always give their preset. Jump cards roll from the pool,
     * skipping presets the frame cannot reach or research has not unlocked.
     */
    public @Nullable Identifier roll(RandomSource random, java.util.Map<Identifier, com.workshopcraft.simplemachines.portal.world.WorldPreset> presets,
                                     Predicate<com.workshopcraft.simplemachines.portal.world.WorldPreset> allowed) {
        if (kind == Kind.STORY) return preset;
        if (kind != Kind.JUMP) return null;
        final List<Identifier> ids = new ArrayList<>();
        final List<Integer> weights = new ArrayList<>();
        final List<PoolEntry> entries = pool.isEmpty() ? List.of(new PoolEntry("*", -1)) : pool;
        for (PoolEntry e : entries) {
            if (e.preset().equals("*")) {
                for (var p : presets.values()) if (allowed.test(p) && p.weight() > 0) {
                    ids.add(p.id());
                    weights.add(p.weight());
                }
                continue;
            }
            final Identifier pid = Identifier.tryParse(e.preset());
            final var p = pid == null ? null : presets.get(pid);
            if (p == null || !allowed.test(p)) continue;
            final int w = e.weight() < 0 ? p.weight() : e.weight();
            if (w <= 0) continue;
            ids.add(pid);
            weights.add(w);
        }
        int total = 0;
        for (int w : weights) total += w;
        if (total <= 0) return null;
        int r = random.nextInt(total);
        for (int i = 0; i < ids.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return ids.get(i);
        }
        return ids.get(ids.size() - 1);
    }
}
