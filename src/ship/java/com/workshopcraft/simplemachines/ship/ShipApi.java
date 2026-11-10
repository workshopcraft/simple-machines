package com.workshopcraft.simplemachines.ship;

import com.workshopcraft.simplemachines.ship.block.CryopodBlockEntity;
import com.workshopcraft.simplemachines.ship.block.CryopodBedBlock;
import com.workshopcraft.simplemachines.ship.block.CountdownBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hooks for other mods (Simple Void, Simple Books quests, pack scripts). Call on the server thread.
 * Every call here also has a /simplemachines command, so data packs and command hooks can do the same.
 */
public final class ShipApi {
    /** Called after an area state changes: key, old state, new state. */
    public interface AreaListener {
        void changed(MinecraftServer server, String key, String before, String after);
    }

    private static final List<AreaListener> LISTENERS = new CopyOnWriteArrayList<>();

    private ShipApi() {}

    // ---- area states, used by locked doors ----

    /**
     * The state of an area key, or "" if it was never set. Keys starting with "void:" read the Simple Void
     * area of that name in the given level when Simple Void is installed.
     */
    public static String getAreaState(ServerLevel level, String key) {
        if (key.startsWith("void:")) return SimpleVoidLink.state(level, key.substring(5));
        return ShipData.get(level.getServer()).area(key);
    }

    /** Sets a Simple Machines area state. "void:" keys are owned by Simple Void and are not set here. */
    public static void setAreaState(MinecraftServer server, String key, String state) {
        final ShipData data = ShipData.get(server);
        final String before = data.area(key);
        data.setArea(key, state);
        for (AreaListener l : LISTENERS) l.changed(server, key, before, state == null ? "" : state);
    }

    public static Map<String, String> areaStates(MinecraftServer server) {
        return ShipData.get(server).areas();
    }

    public static void listenAreas(AreaListener listener) {
        LISTENERS.add(listener);
    }

    // ---- hull ----

    /** True for the survival-proof hull and boundary blocks. */
    public static boolean isHull(BlockState state) {
        return state.getBlock() instanceof com.workshopcraft.simplemachines.ship.block.Hull;
    }

    // ---- broken and fixed pairs ----

    /** The fixed block for a broken one, from data/<ns>/simplemachines/repairs/*.json, or null. */
    public static Block fixedFor(MinecraftServer server, Block broken) {
        final Identifier id = ShipResources.repairs(server).get(BuiltInRegistries.BLOCK.getKey(broken));
        return id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
    }

    /** The broken block for a fixed one, or null. Used to damage a room again. */
    public static Block brokenFor(MinecraftServer server, Block fixed) {
        final Identifier key = BuiltInRegistries.BLOCK.getKey(fixed);
        for (Map.Entry<Identifier, Identifier> e : ShipResources.repairs(server).entrySet()) {
            if (e.getValue().equals(key)) return BuiltInRegistries.BLOCK.getValue(e.getKey());
        }
        return null;
    }

    /** Swaps a broken block for its fixed one, keeping matching block state values. */
    public static boolean repair(ServerLevel level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        final Block fixed = fixedFor(level.getServer(), state.getBlock());
        if (fixed == null) return false;
        level.setBlock(pos, copyState(state, fixed.defaultBlockState()), 3);
        return true;
    }

    /** Swaps a fixed block back to its broken one. */
    public static boolean damage(ServerLevel level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        final Block broken = brokenFor(level.getServer(), state.getBlock());
        if (broken == null || broken == Blocks.AIR) return false;
        level.setBlock(pos, copyState(state, broken.defaultBlockState()), 3);
        return true;
    }

    /** Repairs every broken block in the box (inclusive). Returns how many changed. At most 32768 blocks are checked. */
    public static int repairArea(ServerLevel level, BlockPos a, BlockPos b) {
        int n = 0, checked = 0;
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            if (++checked > 32768) break;
            if (repair(level, p.immutable())) n++;
        }
        return n;
    }

    public static int damageArea(ServerLevel level, BlockPos a, BlockPos b) {
        int n = 0, checked = 0;
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            if (++checked > 32768) break;
            if (damage(level, p.immutable())) n++;
        }
        return n;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState copyState(BlockState from, BlockState to) {
        for (Property p : from.getProperties()) {
            if (to.hasProperty(p)) to = to.setValue(p, from.getValue(p));
        }
        return to;
    }

    // ---- cryopods ----

    /** Sets who is in a decoration cryopod: a player name, a UUID, "skin:<name>" for a bundled skin, or "" for empty. */
    public static boolean setPodOccupant(ServerLevel level, BlockPos pos, String occupant) {
        final CryopodBlockEntity pod = CryopodBlockEntity.at(level, pos);
        if (pod == null) return false;
        pod.setOccupant(occupant);
        return true;
    }

    /** Marks a cryopod bed as a start pod: a new player starts the world in it. */
    public static boolean addStartPod(ServerLevel level, BlockPos pos) {
        final BlockPos head = CryopodBedBlock.head(level, pos);
        if (head == null) return false;
        ShipData.get(level.getServer()).addStartPod(GlobalPos.of(level.dimension(), head));
        return true;
    }

    // ---- displays ----

    /** Starts a jump countdown display from the given number of seconds; 0 stops it. */
    public static boolean setCountdown(ServerLevel level, BlockPos pos, int seconds) {
        if (!(level.getBlockEntity(pos) instanceof CountdownBlockEntity c)) return false;
        c.start(seconds);
        return true;
    }
}
