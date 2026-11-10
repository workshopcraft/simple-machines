package com.workshopcraft.simplemachines.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.cable.PowerInfo;
import com.workshopcraft.simplemachines.cable.Voltage;
import com.workshopcraft.simplemachines.compat.mc.Registration;
import com.workshopcraft.simplemachines.portal.block.GateControllerBlock;
import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import com.workshopcraft.simplemachines.portal.block.ObjectiveCoreBlock;
import com.workshopcraft.simplemachines.portal.block.PortalFieldBlock;
import com.workshopcraft.simplemachines.portal.block.ResearchStationBlock;
import com.workshopcraft.simplemachines.portal.block.ResearchStationBlockEntity;
import com.workshopcraft.simplemachines.portal.block.ReturnBeaconBlock;
import com.workshopcraft.simplemachines.portal.block.ReturnBeaconBlockEntity;
import com.workshopcraft.simplemachines.portal.item.MobScannerItem;
import com.workshopcraft.simplemachines.portal.item.PortalCardItem;
import com.workshopcraft.simplemachines.portal.world.ConfigFilter;
import com.workshopcraft.simplemachines.portal.world.PortalChunkGenerator;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Simple Machines 2.3 portal core (M2): registration shared by both loaders. */
public final class Portal {
    public static final DataComponentType<Identifier> CARD = DataComponentType.<Identifier>builder()
            .persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build();
    public static final DataComponentType<List<String>> FORCED = DataComponentType.<List<String>>builder()
            .persistent(Codec.STRING.listOf()).networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8))).build();

    /** The runtime world a card is bound to after its first jump. A bound card always links back to that world. */
    public static final DataComponentType<String> BOUND = DataComponentType.<String>builder()
            .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build();

    public static final TagKey<Block> FRAME_IRON = blockTag("gate_frames/iron");
    public static final TagKey<Block> FRAME_STEEL = blockTag("gate_frames/steel");
    public static final TagKey<Block> FRAME_TITANIUM = blockTag("gate_frames/titanium");
    public static final TagKey<Block> FRAME_NETHERITE = blockTag("gate_frames/netherite");

    public static final MenuType<GateMenu> GATE_MENU = new MenuType<>(GateMenu::client, FeatureFlags.VANILLA_SET);
    public static final MenuType<ResearchMenu> RESEARCH_MENU = new MenuType<>(ResearchMenu::client, FeatureFlags.VANILLA_SET);
    public static final PlacementModifierType<ConfigFilter> CONFIG_FILTER = () -> ConfigFilter.CODEC;

    public static Block titaniumOre, deepslateTitaniumOre, titaniumBlock, steelBlock;
    public static Block gateController, portalField, objectiveCore, returnBeacon, researchStation;
    public static Item rawTitanium, titaniumDust, titaniumIngot, titaniumSheet, titaniumRod;
    public static Item entangledPellet, entangledCore, jumpCard, storyCard, netherCard, endCard, mobScanner;

    public static BlockEntityType<GateControllerBlockEntity> GATE_ENTITY;
    public static BlockEntityType<ResearchStationBlockEntity> RESEARCH_ENTITY;
    public static BlockEntityType<ReturnBeaconBlockEntity> BEACON_ENTITY;

    static final List<Item> TAB = new ArrayList<>();

    private Portal() {}

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, SimpleMachines.id(path));
    }

    public static void init() {
        PortalConfig.init();
    }

    public static void registerComponents(BiConsumer<Identifier, DataComponentType<?>> r) {
        r.accept(SimpleMachines.id("portal_card"), CARD);
        r.accept(SimpleMachines.id("portal_forced"), FORCED);
        r.accept(SimpleMachines.id("portal_world"), BOUND);
    }

    public static void registerChunkGenerators(BiConsumer<Identifier, MapCodec<? extends ChunkGenerator>> r) {
        r.accept(SimpleMachines.id("portal"), PortalChunkGenerator.CODEC);
    }

    public static void registerPlacementModifiers(BiConsumer<Identifier, PlacementModifierType<?>> r) {
        r.accept(SimpleMachines.id("config_enabled"), CONFIG_FILTER);
    }

    private static BlockBehaviour.Properties props(String name) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, SimpleMachines.id(name)));
    }

    private static Item.Properties itemProps(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SimpleMachines.id(name)));
    }

    private static Block block(BiConsumer<Identifier, Block> r, String name, Block b) {
        r.accept(SimpleMachines.id(name), b);
        return b;
    }

    public static void registerBlocks(BiConsumer<Identifier, Block> r) {
        titaniumOre = block(r, "titanium_ore", new DropExperienceBlock(UniformInt.of(1, 3), props("titanium_ore")
                .mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(4.5F, 6.0F)));
        deepslateTitaniumOre = block(r, "deepslate_titanium_ore", new DropExperienceBlock(UniformInt.of(1, 3), props("deepslate_titanium_ore")
                .mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(6.0F, 6.0F)
                .sound(SoundType.DEEPSLATE)));
        titaniumBlock = block(r, "titanium_block", new Block(props("titanium_block").mapColor(MapColor.COLOR_LIGHT_GRAY)
                .requiresCorrectToolForDrops().strength(6.0F, 8.0F).sound(SoundType.METAL)));
        steelBlock = block(r, "steel_block", new Block(props("steel_block").mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops().strength(5.5F, 7.0F).sound(SoundType.METAL)));
        gateController = block(r, "portal_gate_controller", new GateControllerBlock(props("portal_gate_controller").mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops().strength(5.0F, 8.0F).sound(SoundType.METAL)
                .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 10 : 0)));
        portalField = block(r, "portal_field", new PortalFieldBlock(props("portal_field").noCollision().strength(-1.0F, 3600000.0F)
                .lightLevel(s -> 12).noLootTable().pushReaction(PushReaction.BLOCK).sound(SoundType.GLASS).forceSolidOn()
                .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
        objectiveCore = block(r, "objective_core", new ObjectiveCoreBlock(props("objective_core").mapColor(MapColor.COLOR_CYAN)
                .strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> s.getValue(ObjectiveCoreBlock.SPENT) ? 3 : 14).sound(SoundType.AMETHYST)));
        returnBeacon = block(r, "return_beacon", new ReturnBeaconBlock(props("return_beacon").mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> 15).sound(SoundType.METAL).noOcclusion()));
        researchStation = block(r, "research_station", new ResearchStationBlock(props("research_station").mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops().strength(3.5F).sound(SoundType.METAL)
                .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 9 : 0)));
    }

    private static Item item(BiConsumer<Identifier, Item> r, String name, Item item) {
        r.accept(SimpleMachines.id(name), item);
        TAB.add(item);
        return item;
    }

    private static Item blockItem(BiConsumer<Identifier, Item> r, String name, Block b) {
        return item(r, name, new BlockItem(b, itemProps(name).useBlockDescriptionPrefix()));
    }

    public static void registerItems(BiConsumer<Identifier, Item> r) {
        blockItem(r, "titanium_ore", titaniumOre);
        blockItem(r, "deepslate_titanium_ore", deepslateTitaniumOre);
        rawTitanium = item(r, "raw_titanium", new Item(itemProps("raw_titanium")));
        titaniumDust = item(r, "titanium_dust", new Item(itemProps("titanium_dust")));
        titaniumIngot = item(r, "titanium_ingot", new Item(itemProps("titanium_ingot")));
        titaniumSheet = item(r, "titanium_sheet", new Item(itemProps("titanium_sheet")));
        titaniumRod = item(r, "titanium_rod", new Item(itemProps("titanium_rod")));
        blockItem(r, "titanium_block", titaniumBlock);
        blockItem(r, "steel_block", steelBlock);
        entangledPellet = item(r, "entangled_pellet", new Item(itemProps("entangled_pellet")));
        entangledCore = item(r, "entangled_core", new Item(itemProps("entangled_core").stacksTo(1)));
        blockItem(r, "portal_gate_controller", gateController);
        blockItem(r, "research_station", researchStation);
        jumpCard = item(r, "jump_card", new PortalCardItem(itemProps("jump_card").stacksTo(1)
                .component(CARD, SimpleMachines.id("jump")), PortalCard.Kind.JUMP));
        storyCard = item(r, "story_card", new PortalCardItem(itemProps("story_card").stacksTo(1), PortalCard.Kind.STORY));
        netherCard = item(r, "nether_card", new PortalCardItem(itemProps("nether_card").stacksTo(1)
                .component(CARD, SimpleMachines.id("nether")), PortalCard.Kind.NETHER));
        endCard = item(r, "end_card", new PortalCardItem(itemProps("end_card").stacksTo(1)
                .component(CARD, SimpleMachines.id("end")), PortalCard.Kind.END));
        mobScanner = item(r, "mob_scanner", new MobScannerItem(itemProps("mob_scanner").stacksTo(1)));
        // placed by worldgen and gates only; items exist so creative players and commands can use them
        r.accept(SimpleMachines.id("objective_core"), new BlockItem(objectiveCore, itemProps("objective_core").useBlockDescriptionPrefix()));
        r.accept(SimpleMachines.id("return_beacon"), new BlockItem(returnBeacon, itemProps("return_beacon").useBlockDescriptionPrefix()));
    }

    public static void registerBlockEntities(BiConsumer<Identifier, BlockEntityType<?>> r) {
        GATE_ENTITY = Registration.blockEntity(GateControllerBlockEntity::new, Set.of(gateController));
        r.accept(SimpleMachines.id("portal_gate_controller"), GATE_ENTITY);
        RESEARCH_ENTITY = Registration.blockEntity(ResearchStationBlockEntity::new, Set.of(researchStation));
        r.accept(SimpleMachines.id("research_station"), RESEARCH_ENTITY);
        BEACON_ENTITY = Registration.blockEntity(ReturnBeaconBlockEntity::new, Set.of(returnBeacon));
        r.accept(SimpleMachines.id("return_beacon"), BEACON_ENTITY);
    }

    public static void registerMenus(BiConsumer<Identifier, MenuType<?>> r) {
        r.accept(SimpleMachines.id("portal_gate_controller"), GATE_MENU);
        r.accept(SimpleMachines.id("research_station"), RESEARCH_MENU);
    }

    /** The gate controller is an HV machine; the Research Station runs on MV. */
    public static void powerInfo(Map<Block, PowerInfo> m) {
        m.put(gateController, new PowerInfo(Voltage.HV, -1, 0, GateControllerBlockEntity.UPKEEP, GateControllerBlockEntity.UPKEEP, 0,
                PowerInfo.Unit.TELEPORT, GateControllerBlockEntity.CAPACITY, true));
        m.put(researchStation, new PowerInfo(Voltage.MV, -1, 0, ResearchStationBlockEntity.PER_TICK, ResearchStationBlockEntity.PER_TICK, 0,
                PowerInfo.Unit.RUNNING, ResearchStationBlockEntity.CAPACITY, true));
    }

    /** A card item for a card definition id, the item picked by the card's kind. */
    public static ItemStack cardStack(PortalCard card) {
        final Item item = switch (card.kind()) {
            case STORY -> storyCard;
            case NETHER -> netherCard;
            case END -> endCard;
            default -> jumpCard;
        };
        final ItemStack s = new ItemStack(item);
        s.set(CARD, card.id());
        return s;
    }

    public static void registerTabs(BiConsumer<Identifier, CreativeModeTab> r) {
        r.accept(SimpleMachines.id("portals"), CreativeModeTab.builder(CreativeModeTab.Row.TOP, 2)
                .title(Component.translatable("itemGroup.simplemachines.portals"))
                .icon(() -> new ItemStack(gateController))
                .displayItems((params, out) -> {
                    for (Item i : TAB) {
                        if (i instanceof PortalCardItem) continue;
                        out.accept(i == mobScanner ? MobScannerItem.charged(i) : new ItemStack(i));
                    }
                    out.accept(jumpCard);
                    out.accept(netherCard);
                    out.accept(endCard);
                    // story cards and any other cards come from data and are listed by PortalCardItem's creative helper
                    for (ItemStack s : PortalCardItem.creativeCards()) out.accept(s);
                    out.accept(objectiveCore);
                    out.accept(returnBeacon);
                })
                .build());
    }
}
