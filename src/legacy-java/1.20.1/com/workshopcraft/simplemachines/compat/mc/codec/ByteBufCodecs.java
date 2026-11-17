package com.workshopcraft.simplemachines.compat.mc.codec;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Stand-in for the 1.20.5+ ByteBufCodecs. */
public final class ByteBufCodecs {
    public static final StreamCodec<Object, Integer> VAR_INT = StreamCodec.of(FriendlyByteBuf::readVarInt, FriendlyByteBuf::writeVarInt);
    public static final StreamCodec<Object, Long> VAR_LONG = StreamCodec.of(FriendlyByteBuf::readVarLong, FriendlyByteBuf::writeVarLong);
    public static final StreamCodec<Object, Float> FLOAT = StreamCodec.of(FriendlyByteBuf::readFloat, FriendlyByteBuf::writeFloat);
    public static final StreamCodec<Object, Boolean> BOOL = StreamCodec.of(FriendlyByteBuf::readBoolean, FriendlyByteBuf::writeBoolean);
    public static final StreamCodec<Object, String> STRING_UTF8 = StreamCodec.of(FriendlyByteBuf::readUtf, FriendlyByteBuf::writeUtf);
    public static final StreamCodec<Object, net.minecraft.resources.ResourceLocation> RESOURCE_LOCATION = StreamCodec.of(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::writeResourceLocation);
    public static final StreamCodec<Object, java.util.UUID> UUID = StreamCodec.of(FriendlyByteBuf::readUUID, FriendlyByteBuf::writeUUID);

    private ByteBufCodecs() {
    }

    public static <B, T> StreamCodec<B, T> fromCodecWithRegistries(Codec<T> codec) {
        return fromCodec(codec);
    }

    public static <B, T> StreamCodec<B, T> fromCodec(Codec<T> codec) {
        return StreamCodec.of(b -> codec.parse(NbtOps.INSTANCE, b.readNbt().get("v")).result().orElseThrow(), (b, v) -> {
            final CompoundTag t = new CompoundTag();
            codec.encodeStart(NbtOps.INSTANCE, v).result().ifPresent(x -> t.put("v", x));
            b.writeNbt(t);
        });
    }

    @SuppressWarnings("unchecked")
    public static <B, T> StreamCodec<B, T> registry(ResourceKey<? extends Registry<T>> key) {
        final Registry<T> reg = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
        return StreamCodec.of(b -> reg.byId(b.readVarInt()), (b, v) -> b.writeVarInt(reg.getId(v)));
    }

    public static <B, V> Function<StreamCodec<B, V>, StreamCodec<B, List<V>>> list() {
        return list(Integer.MAX_VALUE);
    }

    public static <B, V> Function<StreamCodec<B, V>, StreamCodec<B, List<V>>> list(int max) {
        return c -> StreamCodec.of(b -> {
            final int n = b.readVarInt();
            if (n > max) throw new io.netty.handler.codec.DecoderException(n + " elements exceeds max " + max);
            final List<V> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(c.decode(b));
            return out;
        }, (b, l) -> {
            b.writeVarInt(l.size());
            for (V v : l) c.encode(b, v);
        });
    }
}
