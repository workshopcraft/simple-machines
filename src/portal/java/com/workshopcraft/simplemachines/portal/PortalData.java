package com.workshopcraft.simplemachines.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.workshopcraft.simplemachines.SimpleMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide portal state: the runtime worlds that exist, and research unlocks and scan counts per player.
 * Research is per player, like entangled codes: a station researches for its owner, a scanner for whoever uses it.
 * Keys unlocked for everyone (a pack maker's grant, "everyone_unlocks") count for every player on top of their own.
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
                Codec.BOOL.optionalFieldOf("superseded", false).forGetter(w -> w.superseded),
                Codec.BOOL.optionalFieldOf("one_off", false).forGetter(w -> w.oneOff),
                Codec.STRING.optionalFieldOf("system").forGetter(w -> Optional.ofNullable(w.system)),
                Codec.BOOL.optionalFieldOf("retired", false).forGetter(w -> w.retired)
        ).apply(i, (id, preset, seed, card, forced, gate, landing, collected, hidden, superseded, oneOff, system, retired) -> {
            final World w = new World(id, preset, seed, card.orElse(null), forced, gate.orElse(null));
            w.landing = landing.orElse(null);
            w.collected = collected;
            w.hiddenCard = hidden;
            w.superseded = superseded;
            w.oneOff = oneOff;
            w.system = system.orElse(null);
            w.retired = retired;
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
        /**
         * A throwaway planet: made by a hidden jump card on a gate that is in a star system. It stays the same for every
         * jump to that card while the gate is in that system, and is retired when the gate leaves it.
         */
        public boolean oneOff;
        /** The star system the world belongs to (oneOff worlds). */
        public @Nullable String system;
        /** Its system was left: deleted once empty, unless portal_keep_one_off_worlds. */
        public boolean retired;

        public World(String id, Identifier preset, long seed, @Nullable Identifier card, List<String> forced, @Nullable GlobalPos gate) {
            this.id = id;
            this.preset = preset;
            this.seed = seed;
            this.card = card;
            this.forced = List.copyOf(forced);
            this.gate = gate;
        }
    }

    /**
     * A return rift: held open by the gate at gate, standing at pos in level. away is everyone who came through it and has not
     * gone back yet (offline players stay listed). pelletTicks counts down to the next pellet; held rifts (API) never close
     * for being empty.
     */
    public static final class Rift {
        public static final Codec<Rift> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("gate").forGetter(r -> r.gate),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("level").forGetter(r -> r.level),
                BlockPos.CODEC.fieldOf("pos").forGetter(r -> r.pos),
                Codec.STRING.optionalFieldOf("world").forGetter(r -> Optional.ofNullable(r.world)),
                Codec.INT.optionalFieldOf("tier", 1).forGetter(r -> r.tier),
                Codec.BOOL.optionalFieldOf("low", false).forGetter(r -> r.low),
                Codec.INT.optionalFieldOf("pellet_ticks", 0).forGetter(r -> r.pelletTicks),
                Codec.BOOL.optionalFieldOf("held", false).forGetter(r -> r.held),
                UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("away", List.of()).forGetter(r -> List.copyOf(r.away)),
                Codec.LONG.optionalFieldOf("token", 0L).forGetter(r -> r.token)
        ).apply(i, (gate, level, pos, world, tier, low, pt, held, away, token) -> {
            final Rift r = new Rift(gate, level, pos, world.orElse(null), tier);
            r.low = low;
            r.pelletTicks = pt;
            r.held = held;
            r.away.addAll(away);
            r.token = token;
            return r;
        }));

        public final GlobalPos gate;
        public final ResourceKey<Level> level;
        public final BlockPos pos;
        public final @Nullable String world;
        public final int tier;
        public boolean low;
        public int pelletTicks;
        public boolean held;
        /** Matches the token saved on the controller that opened it, so a replacement controller does not inherit it. */
        public long token;
        public final Set<UUID> away = new LinkedHashSet<>();
        /** Ticks since away became empty. Not saved. */
        public int emptyTicks;
        /** Seconds of fuel left at the last check. Not saved. */
        public int secondsLeft = -1;
        /** Players shown this rift's countdown last second, so those who leave its range are told to hide it. */
        public final Set<UUID> hud = new HashSet<>();

        public Rift(GlobalPos gate, ResourceKey<Level> level, BlockPos pos, @Nullable String world, int tier) {
            this.gate = gate;
            this.level = level;
            this.pos = pos.immutable();
            this.world = world;
            this.tier = Math.max(1, tier);
        }
    }

    public static final Codec<PortalData> CODEC = RecordCodecBuilder.create(i -> i.group(
            World.CODEC.listOf().optionalFieldOf("worlds", List.of()).forGetter(d -> List.copyOf(d.worlds.values())),
            Codec.STRING.listOf().optionalFieldOf("everyone_unlocks", List.of()).forGetter(d -> List.copyOf(d.unlocks)),
            Codec.STRING.listOf().optionalFieldOf("pending_delete", List.of()).forGetter(d -> d.pendingDelete),
            Codec.INT.optionalFieldOf("next", 1).forGetter(d -> d.next),
            Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC.listOf()).optionalFieldOf("occupants", Map.of()).forGetter(d -> d.occupants),
            Rift.CODEC.listOf().optionalFieldOf("rifts", List.of()).forGetter(d -> List.copyOf(d.rifts.values())),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerResearch.CODEC).optionalFieldOf("research", Map.of()).forGetter(d -> d.research)
    ).apply(i, PortalData::new));

    /** One player's research: what they have unlocked and how far each entry has got. */
    public static final class PlayerResearch {
        static final Codec<PlayerResearch> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("unlocks", List.of()).forGetter(r -> List.copyOf(r.unlocks)),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("scans", Map.of()).forGetter(r -> r.scans)
        ).apply(i, (u, sc) -> {
            final PlayerResearch r = new PlayerResearch();
            r.unlocks.addAll(u);
            r.scans.putAll(sc);
            return r;
        }));
        final Set<String> unlocks = new LinkedHashSet<>();
        final Map<String, Integer> scans = new LinkedHashMap<>();
    }
    public static final SavedDataType<PortalData> TYPE = new SavedDataType<>(SimpleMachines.id("portal"), PortalData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    final Map<String, World> worlds = new LinkedHashMap<>();
    final Set<String> unlocks = new LinkedHashSet<>();
    final List<String> pendingDelete = new ArrayList<>();
    int next = 1;
    final Map<String, List<UUID>> occupants = new LinkedHashMap<>();
    final Map<GlobalPos, Rift> rifts = new LinkedHashMap<>();
    final Map<UUID, PlayerResearch> research = new LinkedHashMap<>();

    public PortalData() {
    }

    private PortalData(List<World> worlds, List<String> unlocks, List<String> pending, int next,
                       Map<String, List<UUID>> occupants, List<Rift> rifts, Map<UUID, PlayerResearch> research) {
        this.research.putAll(research);
        for (Rift r : rifts) this.rifts.put(r.gate, r);
        for (Map.Entry<String, List<UUID>> e : occupants.entrySet()) this.occupants.put(e.getKey(), new ArrayList<>(e.getValue()));
        for (World w : worlds) this.worlds.put(w.id, w);
        this.unlocks.addAll(unlocks);
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

    // --- research: per player, plus keys unlocked for everyone ---

    private PlayerResearch of(UUID player) {
        return research.computeIfAbsent(player, k -> new PlayerResearch());
    }

    /** Whether player has key, their own or unlocked for everyone. A null player (an unowned block) only has the latter. */
    public boolean unlocked(@Nullable UUID player, String key) {
        if (unlocks.contains(key)) return true;
        final PlayerResearch r = player == null ? null : research.get(player);
        return r != null && r.unlocks.contains(key);
    }

    /** Everything player has unlocked, keys for everyone included. */
    public Set<String> unlocks(@Nullable UUID player) {
        final Set<String> all = new LinkedHashSet<>(unlocks);
        final PlayerResearch r = player == null ? null : research.get(player);
        if (r != null) all.addAll(r.unlocks);
        return all;
    }

    public Set<String> ownUnlocks(UUID player) {
        final PlayerResearch r = research.get(player);
        return r == null ? Set.of() : Set.copyOf(r.unlocks);
    }

    public boolean unlock(UUID player, String key) {
        final boolean added = of(player).unlocks.add(key);
        if (added) setDirty();
        return added;
    }

    public boolean lock(UUID player, String key) {
        final PlayerResearch r = research.get(player);
        final boolean removed = r != null && r.unlocks.remove(key);
        if (removed) setDirty();
        return removed;
    }

    public Set<String> sharedUnlocks() {
        return Set.copyOf(unlocks);
    }

    public boolean unlockShared(String key) {
        final boolean added = unlocks.add(key);
        if (added) setDirty();
        return added;
    }

    public boolean lockShared(String key) {
        final boolean removed = unlocks.remove(key);
        if (removed) setDirty();
        return removed;
    }

    /** How far the player has got with an entry (items fed or mobs scanned). */
    public int scans(@Nullable UUID player, String research) {
        final PlayerResearch r = player == null ? null : this.research.get(player);
        return r == null ? 0 : r.scans.getOrDefault(research, 0);
    }

    public int addScan(UUID player, String research) {
        of(player).scans.merge(research, 1, Integer::sum);
        setDirty();
        return scans(player, research);
    }

    /** Players with research of their own. */
    public Set<UUID> researchers() {
        return Set.copyOf(research.keySet());
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

    /** The rift the gate at gate holds open, or null. */
    public @Nullable Rift rift(GlobalPos gate) {
        return rifts.get(gate);
    }

    /** The rift standing at pos (its lower block) in level, or null. */
    public @Nullable Rift riftAt(ResourceKey<Level> level, BlockPos pos) {
        for (Rift r : rifts.values()) if (r.level.equals(level) && r.pos.equals(pos)) return r;
        return null;
    }

    public List<Rift> rifts() {
        return List.copyOf(rifts.values());
    }

    public void putRift(Rift r) {
        rifts.put(r.gate, r);
        setDirty();
    }

    public @Nullable Rift removeRift(GlobalPos gate) {
        final Rift r = rifts.remove(gate);
        if (r != null) setDirty();
        return r;
    }

    public List<String> pendingDelete() {
        return pendingDelete;
    }
}
