package com.workshopcraft.simplemachines.compat.mc.component;

import com.mojang.serialization.Codec;
import com.workshopcraft.simplemachines.compat.mc.codec.StreamCodec;

import org.jetbrains.annotations.Nullable;

/** Stand-in for 1.20.5+ item components; values live in the stack's SimpleMachines NBT compound. */
public final class DataComponentType<T> {
    @Nullable final Codec<T> codec;
    String name;

    DataComponentType(@Nullable Codec<T> codec, String name) {
        this.codec = codec;
        this.name = name;
    }

    public void bind(String name) {
        if (this.name == null) this.name = name;
    }

    /** Stands in for Item.Properties.component(...): the value an item reports when its stack has none. */
    public void setDefault(net.minecraft.world.item.Item item, T value) {
        com.workshopcraft.simplemachines.compat.mc.Comps.setDefault(item, this, value);
    }

    public String name() {
        return name;
    }

    @Nullable
    public Codec<T> codec() {
        return codec;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private Codec<T> codec;

        public Builder<T> persistent(Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        public Builder<T> networkSynchronized(StreamCodec<?, T> codec) {
            return this;
        }

        public Builder<T> cacheEncoding() {
            return this;
        }

        public DataComponentType<T> build() {
            return new DataComponentType<>(codec, null);
        }
    }
}
