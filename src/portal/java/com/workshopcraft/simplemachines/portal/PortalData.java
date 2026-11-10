package com.workshopcraft.simplemachines.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.workshopcraft.simplemachines.SimpleMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide portal state: the runtime worlds that exist, research unlocks and scan counts.
 * Kept in data/simplemachines_portal.dat so the worlds can be made again when the server starts.
 */
public class PortalData extends SavedData {
    /** One runtime world. Its folder is dimensions/simplemachines/portal/&lt;id&gt; in the save. */
    public static final class World {
        public static final Codec<World> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(w -> w.id),
                Identifier.CODEC.fieldOf("preset").forGetter(w -> w.preset),
                Codec.LONG.fieldOf("seed").forGetter(w -> w.seed),
                Identifier.CODEC.optionalFieldOf("card").forGetter(w -> Optional.ofNullable(w.card)),
                Codec.STRING.listOf().optionalFieldOf("forced", List.of()).forGetter(w -> w.forced),
                GlobalPos.CODEC.optionalFieldOf("gate").forGetter(w -> Optional.ofNullable(w.gate)),
                BlockPos.CODEC.optionalFieldOf("landing").forGetter(w -> Optional.ofNullable(w.landing)),
                Codec.BOOL.optionalFieldOf("collected", false).forGetter(w -> w.collected),
                Codec.BOOL.optionalFieldOf("hidden_card", false).forGetter(w -> w.hiddenCard),
                Codec.BOOL.optionalFieldOf("superseded", false).forGetter(w -> w.superseded)
        ).apply(i, (id, preset, seed, card, forced, gate, landing, collected, hidden, superseded) -> {
            final World w = new World(id, preset, seed, card.orElse(null), forced, gate.orElse(null));
            w.landing = landing.orElse(null);
            w.collected = collected;
            w.hiddenCard = hidden;
            w.superseded = superseded;
            return w;
        }));

        public final String id;
        public final Identifier preset;
        public final long seed;
        public final @Nullable Identifier card;
        public final List<String> forced;
        public final @Nullable GlobalPos gate;
        public @Nullable BlockPos landing;
        public boolean collected;
        /** Whether the card came from the API or a command rather than an item in the card slot. */
        public boolean hiddenCard;
        /** A story attempt left without the objective, replaced by a newer attempt. */
        public boolean superseded;

        public World(String id, Identifier preset, long seed, @Nullable Identifier card, List<String> forced, @Nullable GlobalPos gate) {
            this.id = id;
            this.preset = preset;
            this.seed = seed;
            this.card = card;
            this.forced = List.copyOf(forced);
            this.gate = gate;
        }
    }

    public static final Codec<PortalData> CODEC = RecordCodecBuilder.create(i -> i.group(
            World.CODEC.listOf().optionalFieldOf("worlds", List.of()).forGetter(d -> List.copyOf(d.worlds.values())),
            Codec.STRING.listOf().optionalFieldOf("unlocks", List.of()).forGetter(d -> List.copyOf(d.unlocks)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("scans", Map.of()).forGetter(d -> d.scans),
            Codec.STRING.listOf().optionalFieldOf("pending_delete", List.of()).forGetter(d -> d.pendingDelete),
            Codec.INT.optionalFieldOf("next", 1).forGetter(d -> d.next),
            Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC.listOf()).optionalFieldOf("occupants", Map.of()).forGetter(d -> d.occupants)
    ).apply(i, PortalData::new));
    public static final SavedDataType<PortalData> TYPE = new SavedDataType<>(SimpleMachines.id("portal"), PortalData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    final Map<String, World> worlds = new LinkedHashMap<>();
    final Set<String> unlocks = new LinkedHashSet<>();
    final Map<String, Integer> scans = new LinkedHashMap<>();
    final List<String> pendingDelete = new ArrayList<>();
    int next = 1;
    final Map<String, List<UUID>> occupants = new LinkedHashMap<>();

    public PortalData() {
    }

    private PortalData(List<World> worlds, List<String> unlocks, Map<String, Integer> scans, List<String> pending, int next,
                       Map<String, List<UUID>> occupants) {
        for (Map.Entry<String, List<UUID>> e : occupants.entrySet()) this.occupants.put(e.getKey(), new ArrayList<>(e.getValue()));
        for (World w : worlds) this.worlds.put(w.id, w);
        this.unlocks.addAll(unlocks);
        this.scans.putAll(scans);
        this.pendingDelete.addAll(pending);
        this.next = next;
    }

    public static PortalData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public String newId() {
        setDirty();
        return "w" + next++;
    }

    public @Nullable World world(String id) {
        return worlds.get(id);
    }

    public List<World> worlds() {
        return List.copyOf(worlds.values());
    }

    public void put(World w) {
        worlds.put(w.id, w);
        setDirty();
    }

    public @Nullable World remove(String id) {
        final World w = worlds.remove(id);
        setDirty();
        return w;
    }

    public boolean unlocked(String key) {
        return unlocks.contains(key);
    }

    public Set<String> unlocks() {
        return Set.copyOf(unlocks);
    }

    public boolean unlock(String key) {
        final boolean added = unlocks.add(key);
        if (added) setDirty();
        return added;
    }

    public boolean lock(String key) {
        final boolean removed = unlocks.remove(key);
        if (removed) setDirty();
        return removed;
    }

    public int scans(String research) {
        return scans.getOrDefault(research, 0);
    }

    public int addScan(String research) {
        final int n = scans(research) + 1;
        scans.put(research, n);
        setDirty();
        return n;
    }

    public boolean hasOccupants(String world) {
        final List<UUID> l = occupants.get(world);
        return l != null && !l.isEmpty();
    }

    public void addOccupant(String world, UUID player) {
        removeOccupant(player);
        occupants.computeIfAbsent(world, k -> new ArrayList<>()).add(player);
        setDirty();
    }

    public void removeOccupant(UUID player) {
        boolean changed = false;
        for (List<UUID> l : occupants.values()) changed |= l.remove(player);
        occupants.values().removeIf(List::isEmpty);
        if (changed) setDirty();
    }

    public List<String> pendingDelete() {
        return pendingDelete;
    }
}
