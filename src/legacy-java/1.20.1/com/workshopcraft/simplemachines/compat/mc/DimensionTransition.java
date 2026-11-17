package com.workshopcraft.simplemachines.compat.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/** 1.20.1 stand-in for the 1.21 transition record: a destination plus what to do once the entity is there. */
public record DimensionTransition(ServerLevel newLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, PostDimensionTransition postDimensionTransition) {
    public static final PostDimensionTransition DO_NOTHING = e -> {};
    public static final PostDimensionTransition PLAY_PORTAL_SOUND = e -> {
        if (e instanceof ServerPlayer p) p.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
    };
    public static final PostDimensionTransition PLACE_PORTAL_TICKET = e -> {
        if (e.level() instanceof ServerLevel l) l.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(e.blockPosition()), 3, e.blockPosition());
    };

    public interface PostDimensionTransition {
        void onTransition(Entity entity);

        default PostDimensionTransition then(PostDimensionTransition next) {
            return e -> {
                onTransition(e);
                next.onTransition(e);
            };
        }
    }

    /** Moves the entity like Entity.changeDimension does on 1.21: players keep their object, other entities may be copied. */
    @Nullable
    public static Entity go(Entity entity, DimensionTransition t) {
        final UUID id = entity.getUUID();
        if (!entity.teleportTo(t.newLevel, t.pos.x, t.pos.y, t.pos.z, Set.of(), t.yRot, t.xRot)) return null;
        final Entity moved = entity instanceof ServerPlayer ? entity : t.newLevel.getEntity(id);
        if (moved == null) return null;
        moved.setDeltaMovement(t.speed);
        t.postDimensionTransition.onTransition(moved);
        return moved;
    }
}
