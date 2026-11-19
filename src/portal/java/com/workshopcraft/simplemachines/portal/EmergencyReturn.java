package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import com.workshopcraft.simplemachines.portal.world.PortalWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared emergency-return logic for the one-shot pad and the rocket.
 * Visual effects live in {@link PortalFx}; flight length, wind-up and spent drops are config keys.
 */
public final class EmergencyReturn {
    public static final String PAD = PortalConfig.EMERGENCY_PAD;
    public static final String ROCKET = PortalConfig.EMERGENCY_ROCKET;
    public static final int DEFAULT_FLIGHT_TICKS = 40;
    public static final int DEFAULT_WINDUP_TICKS = 20;

    private record Windup(UUID player, long endTick) {}
    private static final Map<GlobalPos, Windup> WINDING = new ConcurrentHashMap<>();

    private EmergencyReturn() {}

    public static boolean padEnabled(Level level) {
        return PortalConfig.bool(level.getServer(), PAD);
    }

    public static boolean rocketEnabled(Level level) {
        return PortalConfig.bool(level.getServer(), ROCKET);
    }

    public static int flightTicks(MinecraftServer server) {
        final int n = PortalConfig.integer(server, PortalConfig.EMERGENCY_ROCKET_FLIGHT_TICKS);
        return n < 5 ? DEFAULT_FLIGHT_TICKS : Math.min(200, n);
    }

    public static int windupTicks(MinecraftServer server) {
        final int n = PortalConfig.integer(server, PortalConfig.EMERGENCY_PAD_WINDUP_TICKS);
        return n < 1 ? DEFAULT_WINDUP_TICKS : Math.min(80, n);
    }

    public static String spentDrop(MinecraftServer server) {
        final String v = PortalConfig.string(server, PortalConfig.EMERGENCY_SPENT_DROP).trim().toLowerCase();
        if (v.equals("cobble") || v.equals("cobblestone")) return "cobble";
        if (v.equals("pad") || v.equals("item")) return "pad";
        return "none";
    }

    public static void dropSpent(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        final String drop = spentDrop(level.getServer());
        if (drop.equals("none")) return;
        final var stack = drop.equals("cobble") ? new ItemStack(Blocks.COBBLESTONE) : new ItemStack(Portal.emergencyPad);
        Block.popResource(level, pos, stack);
    }

    /**
     * Starts the pad wind-up (sparks and glitch) and schedules the fire. Returns false if this pad is already winding.
     */
    public static boolean beginPad(ServerLevel level, BlockPos pos, ServerPlayer player) {
        final GlobalPos key = GlobalPos.of(level.dimension(), pos);
        if (WINDING.containsKey(key)) return false;
        final int ticks = windupTicks(level.getServer());
        WINDING.put(key, new Windup(player.getUUID(), level.getGameTime() + ticks));
        PortalFx.emergencyWindup(level, pos, player);
        return true;
    }

    /** @return true if the pad is still winding and should keep a scheduled tick */
    public static boolean tickPad(ServerLevel level, BlockPos pos, BlockState state) {
        final GlobalPos key = GlobalPos.of(level.dimension(), pos);
        final Windup w = WINDING.get(key);
        if (w == null) return false;
        final long now = level.getGameTime();
        final int left = (int) Math.max(0, w.endTick() - now);
        final int total = Math.max(1, windupTicks(level.getServer()));
        PortalFx.emergencySparks(level, pos, Math.max(0, 3 - left * 3 / total));
        if (now < w.endTick()) return true;
        WINDING.remove(key);
        firePad(level, pos, state, w.player());
        return false;
    }

    public static boolean winding(ServerLevel level, BlockPos pos) {
        return WINDING.containsKey(GlobalPos.of(level.dimension(), pos));
    }

    /** If the player is mid wind-up, fire that pad now (logout or mid-flight). */
    public static void finishWindup(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        GlobalPos found = null;
        for (var e : WINDING.entrySet()) {
            if (e.getValue().player().equals(player.getUUID()) && e.getKey().dimension().equals(sl.dimension())) {
                found = e.getKey();
                break;
            }
        }
        if (found == null) return;
        WINDING.remove(found);
        sl.getChunk(found.pos());
        firePad(sl, found.pos(), sl.getBlockState(found.pos()), player.getUUID());
    }

    private static void firePad(ServerLevel level, BlockPos pos, BlockState state, UUID player) {
        if (state.getBlock() instanceof com.workshopcraft.simplemachines.portal.block.EmergencyPadBlock
                && !state.getValue(com.workshopcraft.simplemachines.portal.block.EmergencyPadBlock.SPENT)) {
            level.setBlock(pos, state.setValue(com.workshopcraft.simplemachines.portal.block.EmergencyPadBlock.SPENT, true), 3);
            fxPadSpent(level, pos);
        }
        final ServerPlayer sp = level.getServer().getPlayerList().getPlayer(player);
        if (sp != null) finish(sp);
        else markPending(level.getServer(), player);
    }

    /** Whether a recorded gate still has its controller. */
    public static boolean gateExists(MinecraftServer server, @Nullable GlobalPos gate) {
        if (gate == null) return false;
        final ServerLevel level = server.getLevel(gate.dimension());
        if (level == null) return false;
        final BlockPos pos = gate.pos();
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return level.getBlockEntity(pos) instanceof GateControllerBlockEntity;
    }

    /**
     * Sends the player back to the last gate they walked through, or that portal world's gate,
     * or world spawn if the controller is gone. Per-player; does not touch world cleanup.
     */
    public static void finish(ServerPlayer player) {
        final MinecraftServer server = player.level().getServer();
        if (server == null) return;
        final PortalData data = PortalData.get(server);
        final UUID id = player.getUUID();
        GlobalPos gate = data.returnGate(id);
        if (!gateExists(server, gate)) {
            final String worldId = PortalWorlds.idOf(player.level().dimension());
            final PortalData.World w = worldId == null ? null : data.world(worldId);
            gate = w == null ? null : w.gate;
        }
        if (!gateExists(server, gate)) gate = null;
        data.clearReturnGate(id);
        if (gate == null) {
            player.sendSystemMessage(Component.translatable("message.simplemachines.portal.emergency_spawn"));
        }
        PortalTravel.goBack(player, gate);
        if (player.level() instanceof ServerLevel sl) fxArrive(sl, player);
    }

    /**
     * Spawns the rocket under the player and starts the short launch. Returns false if the
     * server has the rocket turned off or the player is already riding something.
     * Client callers should only request a swing; spawning happens on the server.
     */
    public static boolean tryLaunch(Level level, Player player, ItemStack stack) {
        if (!rocketEnabled(level)) {
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.simplemachines.portal.emergency_rocket_disabled"));
            }
            return false;
        }
        if (player.isPassenger() || player.isSpectator()) return false;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return true;
        final var rocket = new com.workshopcraft.simplemachines.portal.entity.EmergencyRocketEntity(Portal.emergencyRocketEntity, sl);
        rocket.setPos(sp.getX(), sp.getY(), sp.getZ());
        rocket.setYRot(sp.getYRot());
        rocket.setRider(sp.getUUID());
        sl.addFreshEntity(rocket);
        sp.startRiding(rocket);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        fxLaunch(sl, rocket.position());
        return true;
    }

    /** Remember a mid-flight logout so the player is sent home when they next join. */
    public static void markPending(MinecraftServer server, UUID player) {
        PortalData.get(server).markPendingReturn(player);
    }

    /** Launch effects at the rocket. */
    public static void fxLaunch(ServerLevel level, Vec3 pos) {
        PortalFx.emergencyLaunch(level, pos);
    }

    /** Arrival effects after the player is sent home. */
    public static void fxArrive(ServerLevel level, ServerPlayer player) {
        PortalFx.emergencyArrive(level, player);
    }

    /** The pad has just spent itself: pop and smoke. */
    public static void fxPadSpent(ServerLevel level, BlockPos pos) {
        PortalFx.emergencyPop(level, pos);
    }
}
