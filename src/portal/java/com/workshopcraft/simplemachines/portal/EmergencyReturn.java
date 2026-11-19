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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Shared emergency-return logic for the one-shot pad and the rocket.
 * Visual effects are left as no-op hooks for M5.
 */
public final class EmergencyReturn {
    public static final String PAD = PortalConfig.EMERGENCY_PAD;
    public static final String ROCKET = PortalConfig.EMERGENCY_ROCKET;

    private EmergencyReturn() {}

    public static boolean padEnabled(Level level) {
        return PortalConfig.bool(level.getServer(), PAD);
    }

    public static boolean rocketEnabled(Level level) {
        return PortalConfig.bool(level.getServer(), ROCKET);
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

    /** M5 hook: launch effects at the rocket. No-op in M4. */
    public static void fxLaunch(ServerLevel level, Vec3 pos) {
    }

    /** M5 hook: arrival effects after the player is sent home. No-op in M4. */
    public static void fxArrive(ServerLevel level, ServerPlayer player) {
    }

    /** M5 hook: the pad has just spent itself. No-op in M4. */
    public static void fxPadSpent(ServerLevel level, BlockPos pos) {
    }
}
