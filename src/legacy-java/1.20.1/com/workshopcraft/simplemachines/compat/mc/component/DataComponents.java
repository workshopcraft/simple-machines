package com.workshopcraft.simplemachines.compat.mc.component;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import com.workshopcraft.simplemachines.compat.mc.item.ItemLore;
import com.workshopcraft.simplemachines.compat.mc.item.WritableBookContent;
import com.workshopcraft.simplemachines.compat.mc.item.WrittenBookContent;
import com.workshopcraft.simplemachines.compat.mc.item.BlockItemStateProperties;

/** Vanilla components the mod reads, mapped onto 1.20.1 item data by Comps. */
public final class DataComponents {
    public static final DataComponentType<BlockItemStateProperties> BLOCK_STATE = new DataComponentType<>(null, "BlockStateTag");
    public static final DataComponentType<DyeColor> DYE = new DataComponentType<>(null, "$dye");
    public static final DataComponentType<Component> ITEM_NAME = new DataComponentType<>(null, "$item_name");
    public static final DataComponentType<ItemLore> LORE = new DataComponentType<>(null, "$lore");
    public static final DataComponentType<WritableBookContent> WRITABLE_BOOK_CONTENT = new DataComponentType<>(null, "$writable_book");
    public static final DataComponentType<WrittenBookContent> WRITTEN_BOOK_CONTENT = new DataComponentType<>(null, "$written_book");

    private DataComponents() {
    }
}
