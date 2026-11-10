package com.workshopcraft.simplemachines.ship;

import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Alternate finishes for built Simple Machines machines. A finish is an id stored on the machine's block entity as the
 * simplemachines:machine_finish component. What it looks like is data: assets/<ns>/simplemachines/machine_finishes/<path>.json
 * names a texture for each side (see MachineFinishRenderer), so a resource pack or mod can add finishes without code.
 */
public final class MachineFinishes {
    public static final Identifier NONE = Identifier.fromNamespaceAndPath("simplemachines", "none");

    private MachineFinishes() {}

    public static boolean accepts(@Nullable BlockEntity be) {
        return be instanceof WorkBlockEntity;
    }

    /** The finish on a machine, or null. */
    public static @Nullable Identifier of(BlockEntity be) {
        final Identifier id = be.components().get(Ship.MACHINE_FINISH);
        return id == null || id.equals(NONE) ? null : id;
    }

    public static boolean apply(@Nullable BlockEntity be, Identifier finish) {
        if (!accepts(be)) return false;
        final Identifier now = of(be);
        final boolean remove = finish.equals(NONE);
        if (remove ? now == null : finish.equals(now)) return false;
        final DataComponentMap.Builder b = DataComponentMap.builder();
        for (var c : be.components()) if (c.type() != Ship.MACHINE_FINISH) add(b, c);
        if (!remove) b.set(Ship.MACHINE_FINISH, finish);
        be.setComponents(b.build());
        be.setChanged();
        if (be.getLevel() != null) be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void add(DataComponentMap.Builder b, net.minecraft.core.component.TypedDataComponent c) {
        b.set(c.type(), c.value());
    }

    /** Puts the machine's components in its update tag so the client sees the finish. */
    public static void updateTag(BlockEntity be, CompoundTag tag, HolderLookup.Provider registries) {
        if (be.components().isEmpty()) return;
        DataComponentMap.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), be.components())
                .ifSuccess(t -> tag.put("components", t));
    }

    public static Component name(Identifier finish) {
        return Component.translatable("finish." + finish.getNamespace() + "." + finish.getPath());
    }
}
