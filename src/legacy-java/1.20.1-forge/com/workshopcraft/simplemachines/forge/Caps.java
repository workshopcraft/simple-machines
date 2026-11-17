package com.workshopcraft.simplemachines.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** Block capability lookups and attachable providers that ask the block entity each time, so exposed() changes apply at once. */
public final class Caps {
    private Caps() {
    }

    @Nullable
    public static <T> T find(Level level, BlockPos pos, @Nullable Direction side, Capability<T> cap) {
        final BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(cap, side).resolve().orElse(null);
    }

    public static <H> ICapabilityProvider provider(Capability<H> cap, Function<Direction, H> make) {
        return new ICapabilityProvider() {
            @Override
            public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> asked, @Nullable Direction side) {
                if (asked != cap) return LazyOptional.empty();
                final H h = make.apply(side);
                return h == null ? LazyOptional.empty() : LazyOptional.of(() -> h).cast();
            }
        };
    }
}
