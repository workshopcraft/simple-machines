package com.workshopcraft.simplemachines.ship;

import com.workshopcraft.simplemachines.ship.block.CryopodBedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Loader independent event handlers. */
public final class ShipEvents {
    /** Player tag set once a player has been given their start cryopod (or had none to get). */
    public static final String STARTED = "simplemachines.ship_started";
    private static final Map<UUID, Integer> WAITING = new HashMap<>();

    private ShipEvents() {}

    public static void onJoin(ServerPlayer player) {
        if (player.entityTags().contains(STARTED)) return;
        if (!ShipConfig.bool(player.level().getServer(), ShipConfig.START_IN_CRYOPOD)) return;
        if (!tryStart(player)) WAITING.put(player.getUUID(), 200);
    }

    public static void onServerTick(MinecraftServer server) {
        if (WAITING.isEmpty() || server.getTickCount() % 20 != 0) return;
        for (Iterator<Map.Entry<UUID, Integer>> it = WAITING.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<UUID, Integer> e = it.next();
            final ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            final int left = e.getValue() - 20;
            if (p == null || tryStart(p)) it.remove();
            else if (left <= 0) {
                // no start pod in this world: never look again for this player
                p.addTag(STARTED);
                it.remove();
            } else e.setValue(left);
        }
    }

    /** Puts a new player in the first start cryopod that still exists, and links their spawn to it. */
    private static boolean tryStart(ServerPlayer player) {
        final MinecraftServer server = player.level().getServer();
        final ShipData data = ShipData.get(server);
        for (GlobalPos gp : data.startPods()) {
            final ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            final BlockState s = level.getBlockState(gp.pos());
            if (!(s.getBlock() instanceof CryopodBedBlock)) {
                data.removeStartPod(gp);
                continue;
            }
            final BlockPos head = gp.pos();
            final Optional<Vec3> stand = CryopodBedBlock.standUp(level, head, s, player.getYRot());
            final Vec3 at = stand.orElse(Vec3.atBottomCenterOf(head.above()));
            player.teleportTo(level, at.x, at.y, at.z, Set.of(), s.getValue(CryopodBedBlock.FACING).getOpposite().toYRot(), 0F, true);
            final boolean bedsWork = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE, head).canSetSpawn(level);
            if (bedsWork) {
                player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), head, player.getYRot(), 0F), false), false);
            } else CryopodBedBlock.linkSpawn(player, level, head, s);
            player.addTag(STARTED);
            player.sendSystemMessage(Component.translatable("message.simplemachines.cryopod_wake"));
            return true;
        }
        return false;
    }
}
