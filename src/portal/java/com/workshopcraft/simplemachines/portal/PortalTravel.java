package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import com.workshopcraft.simplemachines.portal.block.ObjectiveCoreBlock;
import com.workshopcraft.simplemachines.portal.world.PortalChunkGenerator;
import com.workshopcraft.simplemachines.portal.world.PortalWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Moving through gates, landing spots, the way back, objectives and cleaning up worlds nobody uses. */
public final class PortalTravel {
    /** Open gates per level: controller position to the box of its opening. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, AABB>> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<String, Long> EMPTY_SINCE = new HashMap<>();
    private static final int COOLDOWN_TICKS = 60;

    private PortalTravel() {}

    public static void activate(ServerLevel level, BlockPos gate, AABB box) {
        ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(gate.immutable(), box);
    }

    public static void deactivate(ServerLevel level, BlockPos gate) {
        final Map<BlockPos, AABB> m = ACTIVE.get(level.dimension());
        if (m != null) m.remove(gate);
    }

    /** The open gate whose opening holds pos, or null. */
    public static @Nullable BlockPos gateAt(ServerLevel level, BlockPos pos) {
        final Map<BlockPos, AABB> m = ACTIVE.get(level.dimension());
        if (m == null) return null;
        final Vec3 c = Vec3.atCenterOf(pos);
        for (Map.Entry<BlockPos, AABB> e : m.entrySet()) if (e.getValue().contains(c)) return e.getKey();
        return null;
    }

    public static void entered(ServerLevel level, BlockPos field, Entity entity) {
        final BlockPos gate = gateAt(level, field);
        if (gate == null) return;
        if (level.getBlockEntity(gate) instanceof GateControllerBlockEntity g) g.travel(entity);
    }

    public static void send(ServerLevel from, BlockPos gate, Entity entity, ResourceKey<Level> targetKey, @Nullable String worldId) {
        final MinecraftServer server = from.getServer();
        final long now = server.getTickCount();
        final Long last = COOLDOWN.get(entity.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) return;
        if (entity.isPassenger() || entity.isVehicle() || !entity.canUsePortal(false)) return;
        final ServerLevel target = server.getLevel(targetKey);
        if (target == null) return;
        final GlobalPos gp = GlobalPos.of(from.dimension(), gate);
        if (!PortalApi.fireBefore(entity, gp, targetKey)) return;
        final PortalData.World w = worldId == null ? null : PortalData.get(server).world(worldId);
        final BlockPos landing = landing(target, w, gate, from);
        COOLDOWN.put(entity.getUUID(), now);
        final Entity moved = entity.teleport(new TeleportTransition(target, Vec3.atBottomCenterOf(landing), Vec3.ZERO, entity.getYRot(),
                entity.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
        if (moved != null) {
            COOLDOWN.put(moved.getUUID(), now);
            if (moved instanceof ServerPlayer p) PortalRifts.arrived(from, gate, target, landing, worldId, p);
            PortalApi.fireAfter(moved, gp, targetKey);
        }
    }

    /** Where a trip arrives: the world's saved landing, or a safe spot worked out and saved on the first trip. */
    public static BlockPos landing(ServerLevel target, PortalData.@Nullable World w, BlockPos gate, ServerLevel from) {
        if (w != null && w.landing != null) return w.landing;
        BlockPos spot;
        if (target.dimension() == Level.END) {
            spot = ServerLevel.END_SPAWN_POINT;
            EndPlatformFeature.createEndPlatform(target, spot.below(), true);
        } else if (target.dimension() == Level.NETHER) {
            final double scale = from.dimensionType().coordinateScale() / target.dimensionType().coordinateScale();
            final int x = (int) Math.floor(gate.getX() * scale), z = (int) Math.floor(gate.getZ() * scale);
            target.getChunk(x >> 4, z >> 4);
            spot = new BlockPos(x, PortalChunkGenerator.siteY(target, x, z), z);
            platform(target, spot, Blocks.OBSIDIAN.defaultBlockState());
        } else {
            target.getChunk(0, 0);
            final int y = target.dimensionType().hasCeiling() ? PortalChunkGenerator.siteY(target, 0, 0)
                    : target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
            spot = new BlockPos(0, y <= target.getMinY() + 1 ? 64 : y, 0);
            final PortalChunkGenerator.WorldPlan plan = PortalChunkGenerator.planOf(target);
            platform(target, spot, plan == null ? Blocks.STONE_BRICKS.defaultBlockState()
                    : BuiltInRegistries.BLOCK.getValue(plan.siteBlock()).defaultBlockState());
        }
        if (w != null) {
            w.landing = spot;
            PortalData.get(target.getServer()).setDirty();
        }
        return spot;
    }

    /** A 3 by 3 floor under the spot and room to stand, so a landing is never in a wall or over a drop. */
    private static void platform(ServerLevel level, BlockPos spot, BlockState floor) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final BlockPos f = spot.offset(dx, -1, dz);
                if (!level.getBlockState(f).isSolid()) level.setBlockAndUpdate(f, floor);
                for (int dy = 0; dy < 2; dy++) {
                    final BlockPos a = spot.offset(dx, dy, dz);
                    if (!level.getBlockState(a).isAir()) level.setBlockAndUpdate(a, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    static boolean coolingDown(Entity e) {
        final Long last = COOLDOWN.get(e.getUUID());
        return last != null && e.level().getServer().getTickCount() - last < COOLDOWN_TICKS;
    }

    /** Sends a player back to the gate the beacon names, in front of its controller, or to world spawn if it is gone. */
    public static void goBack(ServerPlayer player, @Nullable GlobalPos gate) {
        final MinecraftServer server = player.level().getServer();
        final ServerLevel level = gate == null ? null : server.getLevel(gate.dimension());
        if (level == null) {
            PortalWorlds.toSpawn(server, player);
            return;
        }
        final BlockPos g = gate.pos();
        level.getChunk(g.getX() >> 4, g.getZ() >> 4);
        Direction front = Direction.NORTH;
        final BlockState s = level.getBlockState(g);
        if (s.hasProperty(com.workshopcraft.simplemachines.machine.MachineBlock.FACING)) front = s.getValue(com.workshopcraft.simplemachines.machine.MachineBlock.FACING);
        BlockPos spot = g.relative(front);
        if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) spot = g.relative(front, 2);
        COOLDOWN.put(player.getUUID(), (long) server.getTickCount());
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(spot), Vec3.ZERO, front.toYRot(), 0,
                TeleportTransition.PLAY_PORTAL_SOUND));
    }

    /**
     * The objective core was used. Fires the objective event exactly once per world (the world record keeps the flag),
     * and uses up the story card at the source gate unless config or the card keeps it.
     */
    public static void collect(ServerPlayer player, ServerLevel level, BlockPos pos) {
        final String id = PortalWorlds.idOf(level.dimension());
        final PortalData data = PortalData.get(level.getServer());
        final PortalData.World w = id == null ? null : data.world(id);
        if (w == null) {
            player.sendSystemMessage(Component.translatable("message.simplemachines.portal.objective_inert"));
            return;
        }
        if (w.collected) {
            player.sendSystemMessage(Component.translatable("message.simplemachines.portal.objective_done"));
            return;
        }
        w.collected = true;
        data.setDirty();
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(ObjectiveCoreBlock.SPENT, true));
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        boolean usedUp = false;
        if (w.gate != null) {
            final ServerLevel gl = level.getServer().getLevel(w.gate.dimension());
            if (gl != null && gl.getBlockEntity(w.gate.pos()) instanceof GateControllerBlockEntity g) usedUp = g.storySuccess(w);
        }
        player.sendSystemMessage(Component.translatable(usedUp ? "message.simplemachines.portal.objective_card_used"
                : "message.simplemachines.portal.objective_collected"));
        PortalApi.fireObjective(player, w.id, w.card, w.gate, usedUp);
    }

    /**
     * Every few seconds. Worlds bound to a card are kept for good. Deleted once empty long enough, with no open gate or
     * rift pointing at them: planets of a star system the gate has left (portal_keep_one_off_worlds, false by default), story
     * attempts replaced by a newer attempt (portal_cleanup_failed_story_worlds, on by default), and worlds with no card,
     * which only commands make (portal_cleanup_unbound_worlds, off by default).
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        COOLDOWN.values().removeIf(t -> server.getTickCount() - t > COOLDOWN_TICKS * 4L);
        PortalRifts.sweep(server);
        final boolean unbound = PortalConfig.bool(server, PortalConfig.CLEANUP_UNBOUND);
        final boolean story = PortalConfig.bool(server, PortalConfig.CLEANUP_FAILED_STORY);
        final boolean oneOff = !PortalConfig.bool(server, PortalConfig.KEEP_ONE_OFF_WORLDS);
        if (!unbound && !story && !oneOff) return;
        final Set<String> open = new HashSet<>(PortalRifts.worldsWithRifts(server));
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, AABB>> e : ACTIVE.entrySet()) {
            final ServerLevel l = server.getLevel(e.getKey());
            if (l == null) continue;
            for (BlockPos p : e.getValue().keySet()) {
                if (l.getBlockEntity(p) instanceof GateControllerBlockEntity g && g.worldId() != null) open.add(g.worldId());
            }
        }
        final long now = server.getTickCount();
        final long wait = PortalConfig.integer(server, PortalConfig.EMPTY_WORLD_SECONDS) * 20L;
        for (PortalData.World w : PortalData.get(server).worlds()) {
            if (w.oneOff && !w.retired && w.system != null && orphaned(server, w)) {
                w.retired = true;
                PortalData.get(server).setDirty();
            }
            // a planet stays while its system is current; once the gate has left the system it may go
            final boolean mayGo = w.oneOff ? oneOff && (w.retired || w.system == null)
                    : w.card == null ? unbound : story && w.superseded && !w.collected;
            if (!mayGo) continue;
            if (open.contains(w.id)) {
                EMPTY_SINCE.remove(w.id);
                continue;
            }
            final ServerLevel l = PortalWorlds.level(server, w.id);
            final boolean empty = l == null || l.players().isEmpty();
            final boolean offlineInside = PortalData.get(server).hasOccupants(w.id);
            if (!empty || offlineInside) {
                EMPTY_SINCE.remove(w.id);
                continue;
            }
            final Long since = EMPTY_SINCE.get(w.id);
            if (since == null) {
                EMPTY_SINCE.put(w.id, now);
            } else if (now - since >= wait) {
                EMPTY_SINCE.remove(w.id);
                // a logged-out player still in it keeps it, retired or not, until they have left
                if (PortalWorlds.occupiedOffline(server, w.id)) continue;
                PortalWorlds.later(s -> PortalWorlds.unload(s, w.id, true, p -> goBack(p, w.gate)));
            }
        }
    }

    /** A planet whose gate is gone or no longer in its system (a controller replaced without its removal hook). */
    private static boolean orphaned(MinecraftServer server, PortalData.World w) {
        if (w.gate == null) return true;
        final ServerLevel l = server.getLevel(w.gate.dimension());
        if (l == null || !l.isLoaded(w.gate.pos())) return false;
        return !(l.getBlockEntity(w.gate.pos()) instanceof GateControllerBlockEntity g) || !w.system.equals(g.system());
    }

    /** Remembers players who log out inside a portal world, so the world is kept for them. */
    public static void loggedOut(ServerPlayer p) {
        final String id = PortalWorlds.idOf(p.level().dimension());
        if (id != null) PortalData.get(p.level().getServer()).addOccupant(id, p.getUUID());
    }

    /**
     * A player who logs back in on a planet whose system the ship has left (or whose world is otherwise due for cleanup)
     * is taken back to the gate it came from, or to spawn if that gate is gone, and told why. The world can then go.
     */
    public static void loggedIn(ServerPlayer p) {
        final MinecraftServer server = p.level().getServer();
        final PortalData data = PortalData.get(server);
        data.removeOccupant(p.getUUID());
        final String id = PortalWorlds.idOf(p.level().dimension());
        final PortalData.World w = id == null ? null : data.world(id);
        if (w == null || !w.oneOff || !w.retired) return;
        final java.util.UUID uuid = p.getUUID();
        PortalWorlds.later(s -> {
            final ServerPlayer now = s.getPlayerList().getPlayer(uuid);
            if (now == null || !now.level().dimension().equals(PortalWorlds.key(w.id))) return;
            goBack(now, w.gate);
            now.sendSystemMessage(Component.translatable("message.simplemachines.portal.system_left_offline"));
        });
    }

    public static void serverStopped() {
        ACTIVE.clear();
        COOLDOWN.clear();
        EMPTY_SINCE.clear();
    }

    static List<String> openGates(ServerLevel level) {
        final Map<BlockPos, AABB> m = ACTIVE.get(level.dimension());
        return m == null ? List.of() : m.keySet().stream().map(BlockPos::toShortString).toList();
    }
}
