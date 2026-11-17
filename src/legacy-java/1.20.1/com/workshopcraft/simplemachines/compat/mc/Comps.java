package com.workshopcraft.simplemachines.compat.mc;

import com.workshopcraft.simplemachines.compat.mc.item.BlockItemStateProperties;
import com.workshopcraft.simplemachines.compat.mc.component.DataComponentMap;
import com.workshopcraft.simplemachines.compat.mc.component.DataComponentType;
import com.workshopcraft.simplemachines.compat.mc.component.DataComponents;
import com.workshopcraft.simplemachines.compat.mc.item.ItemLore;
import com.workshopcraft.simplemachines.compat.mc.item.WritableBookContent;
import com.workshopcraft.simplemachines.compat.mc.item.WrittenBookContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Item component access for 1.20.1, stored under the stack's SimpleMachines compound. */
public final class Comps {
    public static final String KEY = "SimpleMachines";
    private static final Map<Item, Map<DataComponentType<?>, Object>> DEFAULTS = new IdentityHashMap<>();

    private Comps() {
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T get(ItemStack stack, DataComponentType<T> type) {
        if (type == DataComponents.DYE) return stack.getItem() instanceof DyeItem d ? (T) d.getDyeColor() : null;
        final CompoundTag tag = stack.getTag();
        if (type == DataComponents.BLOCK_STATE) {
            return tag != null && tag.contains("BlockStateTag", Tag.TAG_COMPOUND) ? (T) new BlockItemStateProperties(tag.getCompound("BlockStateTag")) : null;
        }
        if (type == DataComponents.ITEM_NAME) return stack.hasCustomHoverName() ? (T) stack.getHoverName() : null;
        if (type == DataComponents.LORE) return (T) lore(tag);
        if (type == DataComponents.WRITABLE_BOOK_CONTENT) return stack.is(Items.WRITABLE_BOOK) ? (T) new WritableBookContent(strings(tag, "pages")) : null;
        if (type == DataComponents.WRITTEN_BOOK_CONTENT) {
            if (!stack.is(Items.WRITTEN_BOOK) || tag == null) return null;
            final List<Component> pages = new ArrayList<>();
            for (String s : strings(tag, "pages")) pages.add(json(s));
            return (T) new WrittenBookContent(new WrittenBookContent.Title(tag.getString("title")), pages);
        }
        final T v = read(tag == null ? null : tag.getCompound(KEY), type);
        if (v != null) return v;
        final Map<DataComponentType<?>, Object> d = DEFAULTS.get(stack.getItem());
        return d == null ? null : (T) d.get(type);
    }

    /** Stands in for Item.Properties.component(...) defaults, which 1.20.1 items don't have. */
    public static <T> void setDefault(Item item, DataComponentType<T> type, T value) {
        DEFAULTS.computeIfAbsent(item, i -> new IdentityHashMap<>()).put(type, value);
    }

    private static List<String> strings(@Nullable CompoundTag tag, String key) {
        final List<String> out = new ArrayList<>();
        if (tag == null) return out;
        final ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    private static Component json(String s) {
        try {
            final Component c = Component.Serializer.fromJson(s);
            if (c != null) return c;
        } catch (RuntimeException ignored) {
        }
        return Component.literal(s);
    }

    @Nullable
    private static ItemLore lore(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("display", Tag.TAG_COMPOUND)) return null;
        final CompoundTag display = tag.getCompound("display");
        if (!display.contains("Lore", Tag.TAG_LIST)) return null;
        final List<Component> lines = new ArrayList<>();
        for (String s : strings(display, "Lore")) lines.add(json(s));
        return new ItemLore(lines);
    }

    @Nullable
    public static <T> T read(@Nullable CompoundTag data, DataComponentType<T> type) {
        if (data == null || type.codec() == null || !data.contains(type.name())) return null;
        return type.codec().parse(NbtOps.INSTANCE, data.get(type.name())).result().orElse(null);
    }

    public static <T> T getOrDefault(ItemStack stack, DataComponentType<T> type, T def) {
        final T v = get(stack, type);
        return v != null ? v : def;
    }

    public static boolean has(ItemStack stack, DataComponentType<?> type) {
        return get(stack, type) != null;
    }

    @Nullable
    public static <T> T set(ItemStack stack, DataComponentType<T> type, @Nullable T value) {
        final T old = get(stack, type);
        if (value == null) {
            remove(stack, type);
            return old;
        }
        if (type == DataComponents.BLOCK_STATE) {
            stack.getOrCreateTag().put("BlockStateTag", ((BlockItemStateProperties) value).tag().copy());
            return old;
        }
        if (type == DataComponents.ITEM_NAME) {
            stack.setHoverName(((Component) value).copy().withStyle(s -> s.withItalic(false)));
            return old;
        }
        if (type == DataComponents.LORE) {
            final ListTag list = new ListTag();
            for (Component line : ((ItemLore) value).lines()) list.add(StringTag.valueOf(Component.Serializer.toJson(line)));
            stack.getOrCreateTagElement("display").put("Lore", list);
            return old;
        }
        if (type.codec() == null) return old;
        final Map<DataComponentType<?>, Object> d = DEFAULTS.get(stack.getItem());
        if (d != null && value.equals(d.get(type))) {
            // like 1.20.5+, a value equal to the item default leaves no data behind, so the stack still stacks with a plain one
            remove(stack, type);
            return old;
        }
        final CompoundTag data = stack.getOrCreateTagElement(KEY);
        type.codec().encodeStart(NbtOps.INSTANCE, value).result().ifPresent(t -> data.put(type.name(), t));
        return old;
    }

    @Nullable
    public static <T> T remove(ItemStack stack, DataComponentType<T> type) {
        final T old = get(stack, type);
        if (type == DataComponents.BLOCK_STATE) {
            stack.removeTagKey("BlockStateTag");
            return old;
        }
        if (type == DataComponents.ITEM_NAME) {
            stack.resetHoverName();
            return old;
        }
        if (type == DataComponents.LORE) {
            final CompoundTag display = stack.getTagElement("display");
            if (display != null) display.remove("Lore");
            return old;
        }
        final CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(KEY, Tag.TAG_COMPOUND)) {
            final CompoundTag data = tag.getCompound(KEY);
            data.remove(type.name());
            if (data.isEmpty()) stack.removeTagKey(KEY);
        }
        return old;
    }

    public static void write(CompoundTag data, DataComponentMap.Builder components) {
        for (Map.Entry<DataComponentType<?>, Object> e : components.values().entrySet()) put(data, e.getKey(), e.getValue());
    }

    @SuppressWarnings("unchecked")
    private static <T> void put(CompoundTag data, DataComponentType<T> type, Object value) {
        if (type.codec() != null && type.name() != null) {
            type.codec().encodeStart(NbtOps.INSTANCE, (T) value).result().ifPresent(t -> data.put(type.name(), t));
        }
    }

    public static DataComponentGetter getter(ItemStack stack) {
        return new DataComponentGetter() {
            public <T> T get(DataComponentType<T> type) {
                return Comps.get(stack, type);
            }
        };
    }

    public static DataComponentGetter getter(CompoundTag data) {
        return new DataComponentGetter() {
            public <T> T get(DataComponentType<T> type) {
                return Comps.read(data, type);
            }
        };
    }
}
