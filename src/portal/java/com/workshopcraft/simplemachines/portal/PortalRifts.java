package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import com.workshopcraft.simplemachines.portal.block.ReturnRiftBlock;
import com.workshopcraft.simplemachines.portal.world.PortalWorlds;
import com.workshopcraft.simplemachines.portal.world.WorldPreset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Return rifts (brief A8). No frame is built on the far side: the gate holds a frameless rift open beside the landing spot,
 * and walking into it takes a player back to the gate. Holding it open costs the gate FE every second and one Entangled
 * Pellet every portal_rift_seconds_per_pellet, from its own buffer and pellet slots, so the gate's fuel is the timer.
 * With portal_rift_warning_seconds of fuel left the rift flickers; when either runs out it collapses, and anyone still on
 * the far side is stranded. A rift also closes quietly once everyone who came through it has gone back
 * (portal_rift_empty_close_seconds), unless the API holds it. While a rift is open the gate keeps its own chunk loaded.
 * Rifts are saved in PortalData, so they survive a restart; the chunk tickets are put back when the server starts.
 * Everyone in the rift's world and near the gate gets a countdown on screen (RiftHudPayload), worked out from the fuel.
 */
public final class PortalRifts {
    /** Same kind of ticket as the 2.2 Chunk Loader: loads and ticks, never times out, rebuilt from saved data on start. */
    public static final TicketType TICKET = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    public enum Reason { EMPTY, OUT_OF_FUEL, DROPPED, DEPARTED, GATE_GONE, WORLD_GONE }

    /** Players this close to the gate (and everyone in the rift's world) see the countdown. */
    private static final double HUD_RANGE = 16;

    private static final Set<GlobalPos> TICKETED = new HashSet<>();

    private PortalRifts() {}

    /** The world tier a rift's drain scales with: a preset's tier, 1 for the Nether, 3 for the End. */
    public static int tierOf(MinecraftServer server, ResourceKey<Level> level, @Nullable String world) {
        if (level == Level.END) return 3;
        if (world == null) return 1;
        final PortalData.World w = PortalData.get(server).world(world);
        final WorldPreset p = w == null ? null : PortalResources.presets(server).get(w.preset);
        return p == null ? 1 : Math.max(1, p.tier());
    }

    public static int energyPerSecond(MinecraftServer server, int tier) {
        return Math.max(0, PortalConfig.integer(server, PortalConfig.RIFT_ENERGY)) * Math.max(1, tier);
    }

    private static int pelletSeconds(MinecraftServer server) {
        return Math.max(1, PortalConfig.integer(server, PortalConfig.RIFT_PELLET_SECONDS));
    }

    public static PortalData.@Nullable Rift of(ServerLevel gateLevel, BlockPos gate) {
        return PortalData.get(gateLevel.getServer()).rift(GlobalPos.of(gateLevel.dimension(), gate));
    }

    /** A player came out of the gate's field into target. Opens the gate's rift there if it has none, and counts the player as away. */
    static void arrived(ServerLevel gateLevel, BlockPos gate, ServerLevel target, BlockPos landing, @Nullable String world, ServerPlayer player) {
        final MinecraftServer server = gateLevel.getServer();
        final PortalData data = PortalData.get(server);
        final GlobalPos gp = GlobalPos.of(gateLevel.dimension(), gate);
        PortalData.Rift r = data.rift(gp);
        if (r != null && !r.level.equals(target.dimension())) {
            collapse(server, r, Reason.DROPPED);
            r = null;
        }
        if (r == null) r = open(server, gp, target, landing, world, false);
        r.away.add(player.getUUID());
        r.emptyTicks = 0;
        data.setDirty();
    }

    /** Opens a rift for the gate beside landing in target. Nothing is paid up front: the jump already was. */
    public static PortalData.Rift open(MinecraftServer server, GlobalPos gate, ServerLevel target, BlockPos landing, @Nullable String world, boolean held) {
        final PortalData data = PortalData.get(server);
        final PortalData.Rift old = data.rift(gate);
        if (old != null) {
            if (old.level.equals(target.dimension())) {
                old.held |= held;
                data.setDirty();
                return old;
            }
            collapse(server, old, Reason.DROPPED);
        }
        final BlockPos at = landing.offset(2, 0, 0);
        final PortalData.Rift r = new PortalData.Rift(gate, target.dimension(), at, world, tierOf(server, target.dimension(), world));
        r.pelletTicks = pelletSeconds(server) * 20;
        r.held = held;
        r.token = server.overworld().getRandom().nextLong() | 1L;
        final ServerLevel gl = server.getLevel(gate.dimension());
        if (gl != null && gl.getBlockEntity(gate.pos()) instanceof GateControllerBlockEntity g) g.setRiftToken(r.token);
        data.putRift(r);
        place(target, at);
        hold(server, r);
        target.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        target.playSound(null, at, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.3F, 1.8F);
        target.sendParticles(ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 60, 0.2, 0.8, 0.2, 0.1);
        PortalApi.fireRift(r, true, null);
        return r;
    }

    private static void place(ServerLevel level, BlockPos at) {
        final BlockPos floor = at.below();
        if (!level.getBlockState(floor).isSolid()) {
            final BlockState under = level.getBlockState(at.offset(-2, -1, 0));
            level.setBlockAndUpdate(floor, under.isSolid() && !under.hasBlockEntity() ? under : Blocks.STONE_BRICKS.defaultBlockState());
        }
        final BlockState lower = Portal.returnRift.defaultBlockState().setValue(ReturnRiftBlock.AXIS, Direction.Axis.Z);
        level.setBlock(at, lower.setValue(ReturnRiftBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(at.above(), lower.setValue(ReturnRiftBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** Takes the rift down. Players still on the far side are told they are stranded, unless it closed for being empty. */
    public static void collapse(MinecraftServer server, PortalData.Rift r, Reason reason) {
        final PortalData data = PortalData.get(server);
        if (data.removeRift(r.gate) == null) return;
        release(server, r);
        hideHud(server, r, Set.of());
        final ServerLevel level = server.getLevel(r.level);
        if (level != null) {
            if (level.isLoaded(r.pos)) {
                for (BlockPos p : new BlockPos[]{r.pos, r.pos.above()}) {
                    if (level.getBlockState(p).is(Portal.returnRift)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                level.playSound(null, r.pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.7F);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, r.pos.getX() + 0.5, r.pos.getY() + 1.0, r.pos.getZ() + 0.5, 20, 0.2, 0.7, 0.2, 0.02);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, r.pos.getX() + 0.5, r.pos.getY() + 1.0, r.pos.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.3);
            }
            if (reason != Reason.EMPTY && reason != Reason.WORLD_GONE) {
                for (UUID u : r.away) {
                    final ServerPlayer p = server.getPlayerList().getPlayer(u);
                    if (p != null && p.level().dimension().equals(r.level)) {
                        p.sendSystemMessage(Component.translatable(reason == Reason.DEPARTED
                                ? "message.simplemachines.portal.rift_departed" : "message.simplemachines.portal.rift_collapsed"));
                    }
                }
            }
        }
        final ServerLevel gl = server.getLevel(r.gate.dimension());
        if (gl != null && gl.isLoaded(r.gate.pos())) gl.playSound(null, r.gate.pos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 0.9F);
        PortalApi.fireRift(r, false, reason);
    }

    /** Called by the gate every tick; works once a second. Drains the fuel, follows who is still away, flickers and collapses. */
    public static void tickGate(ServerLevel level, GateControllerBlockEntity g) {
        if (level.getGameTime() % 20 != 0) return;
        final MinecraftServer server = level.getServer();
        final PortalData data = PortalData.get(server);
        final PortalData.Rift r = data.rift(GlobalPos.of(level.dimension(), g.getBlockPos()));
        if (r == null) return;
        if (r.token != g.riftToken()) {
            // a different controller now stands where the rift's gate was (replaced without its removal hook, e.g. /setblock)
            collapse(server, r, Reason.GATE_GONE);
            return;
        }
        final ServerLevel far = server.getLevel(r.level);
        if (far == null) {
            collapse(server, r, Reason.WORLD_GONE);
            return;
        }
        if (r.away.removeIf(u -> {
            final ServerPlayer p = server.getPlayerList().getPlayer(u);
            return p != null && !p.level().dimension().equals(r.level);
        })) data.setDirty();
        final int closeAfter = PortalConfig.integer(server, PortalConfig.RIFT_EMPTY_CLOSE);
        if (r.away.isEmpty() && !r.held && closeAfter > 0) {
            r.emptyTicks += 20;
            if (r.emptyTicks >= closeAfter * 20) {
                collapse(server, r, Reason.EMPTY);
                return;
            }
        } else {
            r.emptyTicks = 0;
        }
        final int perSecond = energyPerSecond(server, r.tier);
        if (!g.drain(perSecond)) {
            collapse(server, r, Reason.OUT_OF_FUEL);
            return;
        }
        final int interval = pelletSeconds(server);
        r.pelletTicks -= 20;
        if (r.pelletTicks <= 0) {
            if (!g.takePellet()) {
                collapse(server, r, Reason.OUT_OF_FUEL);
                return;
            }
            r.pelletTicks += interval * 20;
        }
        data.setDirty();
        final long byEnergy = perSecond <= 0 ? Integer.MAX_VALUE : g.energy().energy / perSecond;
        final long byPellets = (long) g.pellets() * interval + r.pelletTicks / 20;
        r.secondsLeft = (int) Math.min(Integer.MAX_VALUE, Math.min(byEnergy, byPellets));
        final boolean low = r.secondsLeft <= Math.max(0, PortalConfig.integer(server, PortalConfig.RIFT_WARNING));
        if (low != r.low) {
            r.low = low;
            syncBlock(far, r);
            if (low) {
                for (UUID u : r.away) {
                    final ServerPlayer p = server.getPlayerList().getPlayer(u);
                    if (p != null && p.level().dimension().equals(r.level)) {
                        p.sendSystemMessage(Component.translatable("message.simplemachines.portal.rift_flicker"), true);
                    }
                }
                if (far.isLoaded(r.pos)) far.playSound(null, r.pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0F, 1.3F);
                level.playSound(null, g.getBlockPos(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.8F, 1.3F);
            }
            PortalApi.fireRiftLow(r, low);
        }
        showHud(server, level, g.getBlockPos(), far, r);
    }

    /** Players who came through the rift and have not gone back, offline ones included. */
    public static int away(MinecraftServer server, PortalData.Rift r) {
        return r.away.size();
    }

    private static void showHud(MinecraftServer server, ServerLevel gateLevel, BlockPos gate, ServerLevel far, PortalData.Rift r) {
        final Set<UUID> now = new HashSet<>();
        final RiftHudPayload msg = new RiftHudPayload(Math.max(0, r.secondsLeft), r.low);
        for (ServerPlayer p : far.players()) {
            now.add(p.getUUID());
            PortalPlatform.sendHud.accept(p, msg);
        }
        for (ServerPlayer p : gateLevel.players()) {
            if (!now.contains(p.getUUID()) && p.distanceToSqr(gate.getX() + 0.5, gate.getY() + 0.5, gate.getZ() + 0.5) <= HUD_RANGE * HUD_RANGE) {
                now.add(p.getUUID());
                PortalPlatform.sendHud.accept(p, msg);
            }
        }
        hideHud(server, r, now);
        r.hud.addAll(now);
    }

    /** Tells everyone shown the countdown but not in keep to hide it. */
    private static void hideHud(MinecraftServer server, PortalData.Rift r, Set<UUID> keep) {
        r.hud.removeIf(u -> {
            if (keep.contains(u)) return false;
            final ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) PortalPlatform.sendHud.accept(p, RiftHudPayload.HIDE);
            return true;
        });
    }

    /** Puts the flicker state on the rift blocks, when their chunk is loaded. The rift's own tick catches up otherwise. */
    static void syncBlock(ServerLevel far, PortalData.Rift r) {
        if (!far.isLoaded(r.pos)) return;
        for (BlockPos p : new BlockPos[]{r.pos, r.pos.above()}) {
            final BlockState s = far.getBlockState(p);
            if (s.is(Portal.returnRift) && s.getValue(ReturnRiftBlock.LOW) != r.low) far.setBlock(p, s.setValue(ReturnRiftBlock.LOW, r.low), Block.UPDATE_CLIENTS);
        }
    }

    /** A player walked into a rift. Sends them to the gate that holds it. */
    public static void entered(ServerLevel level, BlockPos lower, ServerPlayer player) {
        final MinecraftServer server = level.getServer();
        final PortalData data = PortalData.get(server);
        final PortalData.Rift r = data.riftAt(level.dimension(), lower);
        if (r == null || PortalTravel.coolingDown(player)) return;
        if (!PortalApi.fireBefore(player, r.gate, r.gate.dimension())) return;
        r.away.remove(player.getUUID());
        data.setDirty();
        PortalTravel.goBack(player, r.gate);
        PortalApi.fireAfter(player, r.gate, r.gate.dimension());
    }

    // --- chunk tickets ---

    private static void hold(MinecraftServer server, PortalData.Rift r) {
        if (!PortalConfig.bool(server, PortalConfig.RIFT_CHUNK_LOADING) || !TICKETED.add(r.gate)) return;
        final ServerLevel level = server.getLevel(r.gate.dimension());
        if (level != null) level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.containing(r.gate.pos()), 2);
    }

    private static void release(MinecraftServer server, PortalData.Rift r) {
        if (!TICKETED.remove(r.gate)) return;
        final ChunkPos c = ChunkPos.containing(r.gate.pos());
        // two gates in one chunk share a ticket, so only the last one out takes it
        for (GlobalPos o : TICKETED) if (o.dimension().equals(r.gate.dimension()) && ChunkPos.containing(o.pos()).equals(c)) return;
        final ServerLevel level = server.getLevel(r.gate.dimension());
        if (level != null) level.getChunkSource().removeTicketWithRadius(TICKET, c, 2);
    }

    /** Server start: drops rifts whose gate or world is gone, and puts the chunk tickets back for the rest. */
    public static void restore(MinecraftServer server) {
        TICKETED.clear();
        final PortalData data = PortalData.get(server);
        for (PortalData.Rift r : data.rifts()) {
            final boolean worldGone = r.world != null && (data.world(r.world) == null || PortalWorlds.level(server, r.world) == null);
            if (server.getLevel(r.gate.dimension()) == null || server.getLevel(r.level) == null || worldGone) {
                data.removeRift(r.gate);
                continue;
            }
            hold(server, r);
        }
    }

    /** A level is about to be unloaded or deleted: its rifts and the rifts of gates inside it go. */
    public static void levelGone(MinecraftServer server, ResourceKey<Level> key) {
        for (PortalData.Rift r : PortalData.get(server).rifts()) {
            if (r.level.equals(key) || r.gate.dimension().equals(key)) collapse(server, r, Reason.WORLD_GONE);
        }
    }

    /** Every few seconds: a rift whose controller is gone (removed without its hook, e.g. by /setblock) collapses. */
    static void sweep(MinecraftServer server) {
        for (PortalData.Rift r : PortalData.get(server).rifts()) {
            final ServerLevel gl = server.getLevel(r.gate.dimension());
            if (gl != null && gl.isLoaded(r.gate.pos()) && !(gl.getBlockEntity(r.gate.pos()) instanceof GateControllerBlockEntity)) {
                collapse(server, r, Reason.GATE_GONE);
            }
        }
    }

    /** Worlds with an open rift, so cleanup leaves them alone. */
    static Set<String> worldsWithRifts(MinecraftServer server) {
        final Set<String> s = new HashSet<>();
        for (PortalData.Rift r : PortalData.get(server).rifts()) if (r.world != null) s.add(r.world);
        return s;
    }

    public static void serverStopped() {
        TICKETED.clear();
    }
}
