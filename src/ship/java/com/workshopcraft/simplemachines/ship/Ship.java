package com.workshopcraft.simplemachines.ship;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.compat.mc.Registration;
import com.workshopcraft.simplemachines.item.HintItem;
import com.workshopcraft.simplemachines.ship.block.CountdownBlock;
import com.workshopcraft.simplemachines.ship.block.CountdownBlockEntity;
import com.workshopcraft.simplemachines.ship.block.CryopodBedBlock;
import com.workshopcraft.simplemachines.ship.block.CryopodBlock;
import com.workshopcraft.simplemachines.ship.block.CryopodBlockEntity;
import com.workshopcraft.simplemachines.ship.block.ShipContainerBlock;
import com.workshopcraft.simplemachines.ship.block.ShipContainerBlockEntity;
import com.workshopcraft.simplemachines.ship.block.SlideBlock;
import com.workshopcraft.simplemachines.ship.block.SlidingDoorBlockEntity;
import com.workshopcraft.simplemachines.ship.block.TerminalBlock;
import com.workshopcraft.simplemachines.ship.block.TerminalBlockEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Simple Machines 2.3 ship and factory blocks: registration shared by both loaders. */
public final class Ship {
    public static final TagKey<Item> SHIP_TOOLS = TagKey.create(Registries.ITEM, SimpleMachines.id("ship_tools"));
    public static final TagKey<Item> REPAIR_KITS = TagKey.create(Registries.ITEM, SimpleMachines.id("repair_kits"));
    public static final DataComponentType<Identifier> MACHINE_FINISH = DataComponentType.<Identifier>builder()
            .persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build();
    /** Finishes that come with Simple Machines; packs add more with json and a kit given by command. */
    public static final List<String> BUILT_IN_FINISHES = List.of("ancient", "industrial", "clean");

    public static BlockEntityType<CryopodBlockEntity> CRYOPOD_ENTITY;
    public static BlockEntityType<SlidingDoorBlockEntity> SLIDING_DOOR_ENTITY;
    public static BlockEntityType<ShipContainerBlockEntity> CONTAINER_ENTITY;
    public static BlockEntityType<TerminalBlockEntity> TERMINAL_ENTITY;
    public static BlockEntityType<CountdownBlockEntity> COUNTDOWN_ENTITY;

    public static Item shipTool;
    public static Item repairKit;
    public static Item finishKit;
    static final List<Item> TOOL_ITEMS = new ArrayList<>();

    private Ship() {}

    public static void init() {
        ShipConfig.init();
    }

    public static void registerComponents(BiConsumer<Identifier, DataComponentType<?>> r) {
        r.accept(SimpleMachines.id("machine_finish"), MACHINE_FINISH);
    }

    public static void registerBlocks(BiConsumer<Identifier, Block> r) {
        ShipBlocks.registerBlocks(r);
    }

    private static Item.Properties props(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SimpleMachines.id(name)));
    }

    public static void registerItems(BiConsumer<Identifier, Item> r) {
        ShipBlocks.registerItems(r);
        shipTool = tool(r, "ship_tool", new HintItem(props("ship_tool").durability(512), "tooltip.simplemachines.ship_tool", "tooltip.simplemachines.ship_tool_sneak"));
        repairKit = tool(r, "repair_kit", new RepairKitItem(props("repair_kit").stacksTo(16)));
        finishKit = tool(r, "machine_finish_kit", new MachineFinishKitItem(props("machine_finish_kit").stacksTo(16)
                .component(MACHINE_FINISH, MachineFinishes.NONE)));
    }

    private static Item tool(BiConsumer<Identifier, Item> r, String name, Item item) {
        r.accept(SimpleMachines.id(name), item);
        TOOL_ITEMS.add(item);
        return item;
    }

    private static Set<Block> blocks(Predicate<Block> test) {
        final Set<Block> out = new LinkedHashSet<>();
        for (Block b : ShipBlocks.ALL) if (test.test(b)) out.add(b);
        return out;
    }

    public static void registerBlockEntities(BiConsumer<Identifier, BlockEntityType<?>> r) {
        CRYOPOD_ENTITY = Registration.blockEntity(CryopodBlockEntity::new, blocks(b -> b instanceof CryopodBlock));
        r.accept(SimpleMachines.id("cryopod"), CRYOPOD_ENTITY);
        SLIDING_DOOR_ENTITY = Registration.blockEntity(SlidingDoorBlockEntity::new, blocks(b -> b instanceof SlideBlock));
        r.accept(SimpleMachines.id("sliding_door"), SLIDING_DOOR_ENTITY);
        CONTAINER_ENTITY = Registration.blockEntity(ShipContainerBlockEntity::new, blocks(b -> b instanceof ShipContainerBlock));
        r.accept(SimpleMachines.id("ship_container"), CONTAINER_ENTITY);
        TERMINAL_ENTITY = Registration.blockEntity(TerminalBlockEntity::new, blocks(b -> b instanceof TerminalBlock));
        r.accept(SimpleMachines.id("story_log_terminal"), TERMINAL_ENTITY);
        COUNTDOWN_ENTITY = Registration.blockEntity(CountdownBlockEntity::new, blocks(b -> b instanceof CountdownBlock));
        r.accept(SimpleMachines.id("jump_countdown_display"), COUNTDOWN_ENTITY);
    }

    public static ItemStack finishKit(String finish) {
        final ItemStack s = new ItemStack(finishKit);
        s.set(MACHINE_FINISH, SimpleMachines.id(finish));
        return s;
    }

    public static void registerTabs(BiConsumer<Identifier, CreativeModeTab> r) {
        r.accept(SimpleMachines.id("ship"), CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1)
                .title(Component.translatable("itemGroup.simplemachines.ship"))
                .icon(() -> new ItemStack(ShipBlocks.ICON))
                .displayItems((params, out) -> {
                    for (Item i : ShipBlocks.TAB) {
                        out.accept(i);
                        if (i == ShipBlocks.CRYOPOD_BED.asItem()) {
                            final ItemStack start = new ItemStack(i);
                            start.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CryopodBedBlock.START, true));
                            start.set(DataComponents.ITEM_NAME, Component.translatable("item.simplemachines.start_cryopod"));
                            out.accept(start);
                        }
                    }
                    TOOL_ITEMS.forEach(out::accept);
                    for (String f : BUILT_IN_FINISHES) out.accept(finishKit(f));
                })
                .build());
    }
}
