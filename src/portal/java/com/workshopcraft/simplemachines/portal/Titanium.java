package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.SimpleMachines;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Titanium is processed only by machines on a steel chassis. Every titanium input carries the
 * simplemachines:needs_steel_machine item tag; a machine on an iron chassis finds no job for it.
 * Titanium has no furnace recipes, so raw titanium and dust go through the Crusher and the Electric Furnace.
 */
public final class Titanium {
    public static final TagKey<Item> NEEDS_STEEL = TagKey.create(Registries.ITEM, SimpleMachines.id("needs_steel_machine"));

    private Titanium() {}

    public static boolean needsSteel(List<ItemStack> inputs) {
        for (ItemStack s : inputs) if (!s.isEmpty() && s.is(NEEDS_STEEL)) return true;
        return false;
    }

    /** Electric Furnace: titanium dust to ingot, steel chassis only. Vanilla furnaces have no recipe for it. */
    public static ItemStack smelt(ItemStack input) {
        return input.is(Portal.titaniumDust) ? new ItemStack(Portal.titaniumIngot) : ItemStack.EMPTY;
    }
}
