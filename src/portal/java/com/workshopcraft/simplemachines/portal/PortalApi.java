package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import com.workshopcraft.simplemachines.portal.world.WorldPreset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public portal API (brief A9). Server thread only.
 *
 * Gates are found by level and controller position. A gate's target is a card definition id
 * (data/&lt;ns&gt;/simplemachines/portal_cards). A card set here is hidden: it needs no item and players do not see it,
 * and it wins over the item in the card slot. Story success and the objective event work the same for both.
 *
 * Closed mode, as Homebound uses it: setClosedMode(true) (or portal_player_programming=false), arriveAtSystem(gate, id)
 * when the ship reaches a system, setTarget(gate, card, forced) for the planet to visit and open(gate). Each card is the
 * same planet for as long as the ship stays; departSystem puts the gate in transit and lets the planets go. Players
 * only add pellets and power, and may press Open if portal_closed_mode_player_open allows. Whoever comes out on the far side opens a return rift; onRift reports it opening,
 * flickering and closing, and holdRift/dropRift hold or drop it by hand. onSystemChanged reports arrivals and departures.
 */
public final class PortalApi {
    private static final List<TravelListener> TRAVEL = new CopyOnWriteArrayList<>();
    private static final List<ObjectiveListener> OBJECTIVE = new CopyOnWriteArrayList<>();
    private static final List<ClosedModeHook> CLOSED = new CopyOnWriteArrayList<>();
    private static final List<RiftListener> RIFT = new CopyOnWriteArrayList<>();
    private static final List<SystemListener> SYSTEM = new CopyOnWriteArrayList<>();

    private PortalApi() {}

    /** What one jump takes from the gate: pellets from its pellet slots and FE from its buffer. */
    public record JumpCost(int pellets, int energy) {}

    /** Fired before anything moves. Return false from before to stop the trip. */
    public interface TravelListener {
        default boolean before(Entity entity, GlobalPos gate, ResourceKey<Level> target) {
            return true;
        }

        default void after(Entity entity, GlobalPos gate, ResourceKey<Level> target) {}
    }

    /**
     * Fired exactly once per portal world, when its objective is collected.
     * card is the card definition the world was made from (null for none); storyUsedUp tells whether the story card was used up.
     */
    public interface ObjectiveListener {
        void collected(ServerPlayer player, String worldId, @Nullable Identifier card, @Nullable GlobalPos gate, boolean storyUsedUp);
    }

    /** A gate's own closed mode flag was changed through setClosedMode. */
    public interface ClosedModeHook {
        void changed(ServerLevel level, BlockPos gate, boolean closed);
    }

    /**
     * Return rifts. opened fires when a rift forms (a player came through, or holdRift); low when its fuel warning starts
     * or stops; closed when it goes, with why (EMPTY: everyone went back; OUT_OF_FUEL and DROPPED leave anyone still
     * on the far side stranded; DEPARTED: the ship left the system (forced), stranding them too; GATE_GONE: the controller
     * was broken; WORLD_GONE: a level was unloaded).
     */
    public interface RiftListener {
        default void opened(GlobalPos gate, ResourceKey<Level> level, BlockPos rift) {}

        default void low(GlobalPos gate, ResourceKey<Level> level, BlockPos rift, boolean low) {}

        default void closed(GlobalPos gate, ResourceKey<Level> level, BlockPos rift, PortalRifts.Reason reason) {}
    }

    public static void onTravel(TravelListener l) {
        TRAVEL.add(l);
    }

    public static void onObjectiveCollected(ObjectiveListener l) {
        OBJECTIVE.add(l);
    }

    public static void onClosedModeChanged(ClosedModeHook h) {
        CLOSED.add(h);
    }

    public static void onRift(RiftListener l) {
        RIFT.add(l);
    }

    // --- gates ---

    public static @Nullable GateControllerBlockEntity gate(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GateControllerBlockEntity g ? g : null;
    }

    /** The world the gate's active card is bound to, or null. */
    public static @Nullable String getBoundWorld(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g == null ? null : g.activeBound();
    }

    /** The card the gate will use next: the hidden card if one is set, otherwise the card item in its slot. */
    public static @Nullable Identifier getTarget(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g == null ? null : g.activeCard();
    }

    /**
     * Applies a hidden card (no item) to a gate, or clears it with null. forced programs it the same way a Research Station
     * programs a jump card ("ore:&lt;block&gt;", "mob:&lt;entity&gt;", "preset:&lt;preset&gt;"). A hidden jump card binds to its first world
     * per gate, like an item card; applying it again links back. A story card makes a new attempt world each jump until
     * its objective is collected. Returns false if there is no gate or no such card.
     */
    public static boolean setTarget(ServerLevel level, BlockPos pos, @Nullable Identifier card, List<String> forced) {
        final GateControllerBlockEntity g = gate(level, pos);
        if (g == null) return false;
        if (card != null && !PortalResources.cards(level.getServer()).containsKey(card)) return false;
        g.setHiddenCard(card, forced);
        return true;
    }

    // --- star systems ---

    /**
     * The ship arrives in a star system. From then on each hidden jump card is one planet of that system: the first jump
     * makes it, every later jump to the same card goes back to it. Arriving straight from another system departs it first.
     * Returns false with no gate.
     */
    public static boolean arriveAtSystem(ServerLevel level, BlockPos pos, String system) {
        final GateControllerBlockEntity g = gate(level, pos);
        if (g == null) return false;
        g.arrive(system);
        return true;
    }

    /**
     * The ship leaves its system and is in transit: the gate will not open until arriveAtSystem. The system's planets are
     * deleted once nobody is in them (unless portal_keep_one_off_worlds). While anyone is still on the far side of the
     * rift this is refused, unless force, which collapses the rift and strands them. Returns the problem as a
     * translation key, or null.
     */
    public static @Nullable String departSystem(ServerLevel level, BlockPos pos, boolean force) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g == null ? "message.simplemachines.portal.no_gate" : g.depart(force);
    }

    /** The system the gate is in, or null (none, or in transit). */
    public static @Nullable String getSystem(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g == null ? null : g.system();
    }

    public static boolean isInTransit(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g != null && g.inTransit();
    }

    /** Players who came through the gate's rift and have not gone back (offline ones included). */
    public static int playersAway(ServerLevel level, BlockPos pos) {
        final PortalData.Rift r = PortalRifts.of(level, pos);
        return r == null ? 0 : PortalRifts.away(level.getServer(), r);
    }

    /** arrived is false for a departure; system is the system arrived at or left (null when there was none). */
    public interface SystemListener {
        void changed(GlobalPos gate, @Nullable String system, boolean arrived);
    }

    public static void onSystemChanged(SystemListener l) {
        SYSTEM.add(l);
    }

    public static boolean setEnabled(ServerLevel level, BlockPos pos, boolean enabled) {
        final GateControllerBlockEntity g = gate(level, pos);
        if (g == null) return false;
        g.setEnabled(enabled);
        return true;
    }

    public static boolean isEnabled(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g != null && g.enabled();
    }

    /** Starts a jump as if a player pressed Open. Returns the problem as a translation key, or null when the jump started. */
    public static @Nullable String open(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g == null ? "message.simplemachines.portal.no_gate" : g.tryOpen(null);
    }

    /**
     * Closed mode for one gate (brief A7). Players get no card slot and cannot program or choose the destination; only
     * pellets and energy go in. The target comes from setTarget (hidden cards). portal_player_programming=false puts
     * every gate on the server in closed mode, whatever this flag says.
     */
    public static boolean setClosedMode(ServerLevel level, BlockPos pos, boolean closed) {
        final GateControllerBlockEntity g = gate(level, pos);
        if (g == null) return false;
        g.setClosed(closed);
        for (ClosedModeHook h : CLOSED) h.changed(level, pos, closed);
        return true;
    }

    /** Whether the gate is in closed mode, from its own flag or from the server config. */
    public static boolean isClosedMode(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g != null && g.closedMode();
    }

    /** False when portal_player_programming is off: closed mode everywhere, and Research Stations do not program cards. */
    public static boolean playerProgramming(MinecraftServer server) {
        return PortalConfig.bool(server, PortalConfig.PLAYER_PROGRAMMING);
    }

    // --- return rifts ---

    /**
     * Holds a rift open in the world the gate last opened to, beside its landing spot, with no one travelling. A held rift
     * stays until dropRift or until the gate's fuel runs out; it does not close for being empty. Returns the problem as a
     * translation key, or null.
     */
    public static @Nullable String holdRift(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g == null ? "message.simplemachines.portal.no_gate" : g.holdRift();
    }

    /** Collapses the gate's rift. Anyone still on the far side is stranded. */
    public static boolean dropRift(ServerLevel level, BlockPos pos) {
        final GateControllerBlockEntity g = gate(level, pos);
        return g != null && g.dropRift(level, PortalRifts.Reason.DROPPED);
    }

    public static boolean isRiftOpen(ServerLevel level, BlockPos pos) {
        return PortalRifts.of(level, pos) != null;
    }

    /** Where the gate's rift stands, or null. */
    public static @Nullable GlobalPos riftPos(ServerLevel level, BlockPos pos) {
        final PortalData.Rift r = PortalRifts.of(level, pos);
        return r == null ? null : GlobalPos.of(r.level, r.pos);
    }

    /** Seconds of fuel the gate's rift has left at the current drain, -1 with no rift or before the first check. */
    public static int riftSecondsLeft(ServerLevel level, BlockPos pos) {
        final PortalData.Rift r = PortalRifts.of(level, pos);
        return r == null ? -1 : r.secondsLeft;
    }

    // --- cost ---

    /** The cost of a jump to a preset through a frame with the given opening size. */
    public static JumpCost jumpCost(MinecraftServer server, WorldPreset preset, int openingArea) {
        return cost(server, preset.tier(), preset.richness(), openingArea);
    }

    /** The cost of a jump with a card: the Nether costs as tier 1, the End as tier 3; a jump card costs as its dearest pool world. */
    public static @Nullable JumpCost jumpCost(MinecraftServer server, Identifier card, int openingArea) {
        final PortalCard c = PortalResources.cards(server).get(card);
        if (c == null) return null;
        return switch (c.kind()) {
            case NETHER -> cost(server, 1, 1, openingArea);
            case END -> cost(server, 3, 1, openingArea);
            case STORY -> {
                final WorldPreset p = c.preset() == null ? null : PortalResources.presets(server).get(c.preset());
                yield p == null ? null : jumpCost(server, p, openingArea);
            }
            case JUMP -> {
                JumpCost best = null;
                for (WorldPreset p : PortalResources.presets(server).values()) {
                    final JumpCost k = jumpCost(server, p, openingArea);
                    if (best == null || k.energy() > best.energy()) best = k;
                }
                yield best;
            }
        };
    }

    public static JumpCost cost(MinecraftServer server, int tier, float richness, int openingArea) {
        final float rich = Math.max(0.5F, richness);
        final int pellets = Math.max(1, Math.round(tier * rich));
        final double size = 1 + Math.max(0, openingArea - 6) / 12.0;
        final double fe = (double) PortalConfig.integer(server, PortalConfig.JUMP_ENERGY) * tier * Math.max(1, richness) * size;
        return new JumpCost(pellets, (int) Math.min(GateControllerBlockEntity.CAPACITY, fe));
    }

    // --- research and unlocks ---

    /*
     * Research is per player, like entangled codes. A Research Station researches for its owner (whoever placed it), a
     * Mob Scanner for whoever uses it, and a gate rolls and checks world types with its owner's unlocks. A quest reward
     * should unlock for the rewarded player. The shared pool (data from before alpha.5, and the old server-wide forms
     * below) counts for every player.
     */

    /**
     * Unlocks a key for one player: "preset:&lt;id&gt;" (a world type, e.g. a designed world from a quest), "ore:&lt;block&gt;",
     * "mob:&lt;entity&gt;", or "research:&lt;entry&gt;" (marks a research entry done). Returns false if they had it.
     */
    public static boolean unlock(MinecraftServer server, UUID player, String key) {
        return PortalData.get(server).unlock(player, key);
    }

    /** Takes a key back from one player. A key in the shared pool still counts for them. */
    public static boolean lock(MinecraftServer server, UUID player, String key) {
        return PortalData.get(server).lock(player, key);
    }

    /** Whether the player has the key, their own or shared. */
    public static boolean isUnlocked(MinecraftServer server, UUID player, String key) {
        return PortalData.get(server).unlocked(player, key);
    }

    /** Everything the player has unlocked, shared keys included. */
    public static Set<String> unlocks(MinecraftServer server, UUID player) {
        return PortalData.get(server).unlocks(player);
    }

    /** The owner of a gate or Research Station (whose research it uses), or null. */
    public static @Nullable UUID owner(ServerLevel level, BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GateControllerBlockEntity g) return g.owner();
        if (be instanceof com.workshopcraft.simplemachines.portal.block.ResearchStationBlockEntity r) return r.owner();
        return null;
    }

    /** Server wide: unlocks the key for every player (the shared pool). Prefer the per-player form. */
    @Deprecated
    public static boolean unlock(MinecraftServer server, String key) {
        return PortalData.get(server).unlockShared(key);
    }

    /** Whether the key is in the shared pool (unlocked for everyone). Prefer the per-player form. */
    @Deprecated
    public static boolean isUnlocked(MinecraftServer server, String key) {
        return PortalData.get(server).sharedUnlocks().contains(key);
    }

    // --- used by the mod ---

    static boolean fireBefore(Entity e, GlobalPos gate, ResourceKey<Level> target) {
        for (TravelListener l : TRAVEL) if (!l.before(e, gate, target)) return false;
        return true;
    }

    static void fireAfter(Entity e, GlobalPos gate, ResourceKey<Level> target) {
        for (TravelListener l : TRAVEL) l.after(e, gate, target);
    }

    static void fireRift(PortalData.Rift r, boolean opened, PortalRifts.@Nullable Reason reason) {
        for (RiftListener l : RIFT) {
            if (opened) l.opened(r.gate, r.level, r.pos);
            else l.closed(r.gate, r.level, r.pos, reason);
        }
    }

    public static void fireSystem(GlobalPos gate, @Nullable String system, boolean arrived) {
        for (SystemListener l : SYSTEM) l.changed(gate, system, arrived);
    }

    static void fireRiftLow(PortalData.Rift r, boolean low) {
        for (RiftListener l : RIFT) l.low(r.gate, r.level, r.pos, low);
    }

    static void fireObjective(ServerPlayer p, String world, @Nullable Identifier card, @Nullable GlobalPos gate, boolean usedUp) {
        for (ObjectiveListener l : OBJECTIVE) l.collected(p, world, card, gate, usedUp);
    }
}
