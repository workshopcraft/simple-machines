package com.workshopcraft.simplemachines.portal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A research entry, read from data/&lt;namespace&gt;/simplemachines/portal_research/&lt;name&gt;.json.
 * kind "resource": the Research Station eats count of item (an item id or a #tag) and energy per item.
 * kind "mob": scans of entity with the Mob Scanner, one per mob.
 * unlocks: "ore:&lt;block&gt;" or "mob:&lt;entity&gt;" (can be imprinted on a jump card), or "preset:&lt;preset&gt;" (a new world type).
 */
public record Research(Identifier id, boolean mob, String name, @Nullable String item, int count, int energyPerItem,
                       @Nullable Identifier entity, int scans, List<String> unlocks) {

    public static Research parse(Identifier id, JsonObject o) {
        final List<String> unlocks = new ArrayList<>();
        if (o.has("unlocks")) for (JsonElement e : o.getAsJsonArray("unlocks")) unlocks.add(e.getAsString());
        final boolean mob = GsonHelper.getAsString(o, "kind", "resource").equalsIgnoreCase("mob");
        return new Research(id, mob, GsonHelper.getAsString(o, "name", id.getPath()), o.has("item") ? GsonHelper.getAsString(o, "item") : null,
                Math.max(1, GsonHelper.getAsInt(o, "count", 64)), Math.max(0, GsonHelper.getAsInt(o, "energy_per_item", 2000)),
                o.has("entity") ? Identifier.tryParse(GsonHelper.getAsString(o, "entity")) : null, Math.max(1, GsonHelper.getAsInt(o, "scans", 1)),
                List.copyOf(unlocks));
    }

    public boolean takes(ItemStack stack) {
        if (mob || item == null || stack.isEmpty()) return false;
        if (item.startsWith("#")) {
            final Identifier tag = Identifier.tryParse(item.substring(1));
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }
        final Identifier iid = Identifier.tryParse(item);
        return iid != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(iid);
    }

    /** A stack to show for this entry: the item, or the first item in the tag. */
    public ItemStack icon() {
        if (item == null) return ItemStack.EMPTY;
        if (item.startsWith("#")) {
            final Identifier tag = Identifier.tryParse(item.substring(1));
            if (tag == null) return ItemStack.EMPTY;
            for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag))) return new ItemStack(h);
            return ItemStack.EMPTY;
        }
        final Identifier iid = Identifier.tryParse(item);
        final Item it = iid == null ? null : BuiltInRegistries.ITEM.getOptional(iid).orElse(null);
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }
}
