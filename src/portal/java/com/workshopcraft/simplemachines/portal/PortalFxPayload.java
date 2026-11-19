package com.workshopcraft.simplemachines.portal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A short client-side effect: portal state change, emergency teleport, or a normal pad jump. */
public record PortalFxPayload(int kind, int color, float x, float y, float z, int entityId) implements CustomPacketPayload {
    public static final int GATE_OPEN = 0;
    public static final int GATE_COLLAPSE = 1;
    public static final int GATE_LOW = 2;
    public static final int RIFT_OPEN = 3;
    public static final int RIFT_COLLAPSE = 4;
    public static final int EMERGENCY_WINDUP = 5;
    public static final int EMERGENCY_POP = 6;
    public static final int EMERGENCY_LAUNCH = 7;
    public static final int EMERGENCY_ARRIVE = 8;
    public static final int PAD_JUMP = 9;
    public static final int PAD_ARRIVE = 10;

    public static final CustomPacketPayload.Type<PortalFxPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("simplemachines", "portal_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PortalFxPayload::kind,
            ByteBufCodecs.VAR_INT, PortalFxPayload::color,
            ByteBufCodecs.FLOAT, PortalFxPayload::x,
            ByteBufCodecs.FLOAT, PortalFxPayload::y,
            ByteBufCodecs.FLOAT, PortalFxPayload::z,
            ByteBufCodecs.VAR_INT, PortalFxPayload::entityId,
            PortalFxPayload::new);

    public static PortalFxPayload at(int kind, net.minecraft.world.phys.Vec3 pos, int color, int entityId) {
        return new PortalFxPayload(kind, color, (float) pos.x, (float) pos.y, (float) pos.z, entityId);
    }

    @Override
    public CustomPacketPayload.Type<PortalFxPayload> type() {
        return TYPE;
    }
}
