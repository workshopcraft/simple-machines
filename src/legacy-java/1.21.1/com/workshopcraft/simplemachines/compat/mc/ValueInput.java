package com.workshopcraft.simplemachines.compat.mc;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/** Stand-in for the 26.x ValueInput, reading from a CompoundTag. */
public record ValueInput(CompoundTag tag, HolderLookup.Provider registries) {
    public int getIntOr(String key, int def) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : def;
    }

    public long getLongOr(String key, long def) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : def;
    }

    public float getFloatOr(String key, float def) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : def;
    }

    public boolean getBooleanOr(String key, boolean def) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : def;
    }

    public String getStringOr(String key, String def) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : def;
    }

    public Optional<String> getString(String key) {
        return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
    }

    public Optional<Long> getLong(String key) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getLong(key)) : Optional.empty();
    }

    public <T> Optional<T> read(String key, Codec<T> codec) {
        final Tag t = tag.get(key);
        if (t == null) return Optional.empty();
        return codec.parse(registries == null ? NbtOps.INSTANCE : registries.createSerializationContext(NbtOps.INSTANCE), t).result();
    }
}
