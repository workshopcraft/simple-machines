package com.workshopcraft.simplemachines.portal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client, once a second: how long the rift the player depends on has left. seconds < 0 hides the counter. */
public record RiftHudPayload(int seconds, boolean low) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RiftHudPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("simplemachines", "rift_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiftHudPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RiftHudPayload::seconds, ByteBufCodecs.BOOL, RiftHudPayload::low, RiftHudPayload::new);
    public static final RiftHudPayload HIDE = new RiftHudPayload(-1, false);

    @Override
    public CustomPacketPayload.Type<RiftHudPayload> type() {
        return TYPE;
    }
}
