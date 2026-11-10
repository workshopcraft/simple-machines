package com.workshopcraft.simplemachines.portal.block;

import com.workshopcraft.simplemachines.assembly.Tier;
import com.workshopcraft.simplemachines.cable.Voltage;
import com.workshopcraft.simplemachines.machine.EnergyMenu;
import com.workshopcraft.simplemachines.machine.MachineBlock;
import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.PortalData;
import com.workshopcraft.simplemachines.portal.PortalResources;
import com.workshopcraft.simplemachines.portal.Research;
import com.workshopcraft.simplemachines.portal.ResearchMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.Set;

/**
 * Research Station. Pick a research entry with the arrows. Resource entries eat their item from the input slot,
 * one at a time for energy_per_item FE, until count are gone; mob entries fill up from Mob Scanner scans.
 * Research is per player: the station belongs to whoever placed it (public or private by screwdriver, like an
 * entangled code) and everything fed into it counts for its owner. When an entry completes its unlocks go to the owner. Imprint programs the jump card in the card slot with the entry's
 * unlocked ores and mobs (up to three per card) or world type, and the world that card makes reflects it.
 * A card that is already bound to a world cannot be programmed.
 * Slot 2 only shows the chosen entry; it cannot be taken.
 */
public class ResearchStationBlockEntity extends WorkBlockEntity implements com.workshopcraft.simplemachines.machine.Owned {
    public static final int CAPACITY = 400_000;
    public static final int PER_TICK = 2_000;
    public static final int SLOT_INPUT = 0, SLOT_CARD = 1, SLOT_SHOW = 2;
    /** 11..18 the owner in 8 shorts, 19 flags: 1 has an owner, 2 private. */
    public static final int DATA_COUNT = 20;
    public static final int MAX_FORCED = 3;
    private static final int[] FACES = {SLOT_INPUT};

    private int selected;
    private int spent;
    private int count, need, total;
    private boolean unlocked, mob;
    private @Nullable Identifier shown;
    private @Nullable UUID owner;
    private boolean priv;

    private final ContainerData researchData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case 0 -> spent;
                case 1 -> currentCost();
                case 2 -> energy.energy & 0xFFFF;
                case 3 -> energy.energy >>> 16;
                case 4 -> energy.capacity / 1000;
                case 5 -> count;
                case 6 -> EnergyMenu.packTier(getBlockState().getBlock());
                case 7 -> need;
                case 8 -> (unlocked ? 1 : 0) | (mob ? 2 : 0);
                case 9 -> selected;
                case 10 -> total;
                case 19 -> (owner != null ? 1 : 0) | (priv ? 2 : 0);
                default -> {
                    if (id < 11 || id > 18 || owner == null) yield 0;
                    final int i = id - 11;
                    final long half = i < 4 ? owner.getMostSignificantBits() : owner.getLeastSignificantBits();
                    yield (int) (half >>> (48 - 16 * (i % 4))) & 0xFFFF;
                }
            };
        }

        @Override
        public void set(int id, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        super(Portal.RESEARCH_ENTITY, pos, state, 3, CAPACITY, Voltage.RATE[Voltage.MV], 0);
    }

    private int currentCost() {
        final Research r = current();
        return r == null ? 0 : r.energyPerItem();
    }

    private @Nullable Research current() {
        if (!(level instanceof ServerLevel s)) return null;
        final List<Research> all = list(s.getServer());
        if (all.isEmpty()) return null;
        selected = Math.floorMod(selected, all.size());
        return all.get(selected);
    }

    public static List<Research> list(MinecraftServer server) {
        final List<Research> all = new ArrayList<>(PortalResources.research(server).values());
        all.sort((a, b) -> a.id().compareTo(b.id()));
        return all;
    }

    @Override
    protected boolean isOutput(int slot) {
        return false;
    }

    @Override
    protected boolean accepts(ServerLevel level, int slot, ItemStack stack) {
        if (slot == SLOT_CARD) return stack.is(Portal.jumpCard);
        if (slot == SLOT_SHOW) return false;
        final Research r = current();
        return r != null && r.takes(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return FACES;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean energyOut(@Nullable Direction side) {
        return false;
    }

    @Override
    public Tier tier() {
        return Tier.IRON;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        energy.capacity = CAPACITY;
        energy.maxIn = Voltage.RATE[Voltage.MV];
        energy.maxOut = 0;
        if (energy.energy > energy.capacity) energy.energy = energy.capacity;
        work(level, pos, state, Tier.IRON);
    }

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state, Tier tier) {
        final Research r = current();
        boolean working = false;
        total = list(level.getServer()).size();
        if (r == null) {
            count = need = 0;
            unlocked = mob = false;
            show(null);
        } else {
            final PortalData data = PortalData.get(level.getServer());
            mob = r.mob();
            need = mob ? r.scans() : r.count();
            count = data.scans(owner, key(r));
            // done by feeding it, or unlocked by a command, a quest reward or the API
            unlocked = count >= need || data.unlocked(owner, "research:" + r.id());
            show(r);
            final ItemStack in = items.get(SLOT_INPUT);
            // an unowned station (from a command, not placed by a player) has nobody to research for
            if (owner != null && !mob && !unlocked && r.takes(in)) {
                final int step = Math.min(PER_TICK, Math.min(energy.energy, r.energyPerItem() - spent));
                if (step > 0 || r.energyPerItem() == 0) {
                    energy.energy -= step;
                    spent += step;
                    working = true;
                    if (spent >= r.energyPerItem()) {
                        spent = 0;
                        in.shrink(1);
                        count = data.addScan(owner, key(r));
                        if (count >= need) complete(level.getServer(), r, owner);
                    }
                    setChanged();
                }
            } else if (spent != 0) {
                spent = 0;
            }
        }
        if (state.getValue(MachineBlock.LIT) != working) level.setBlock(pos, state.setValue(MachineBlock.LIT, working), 3);
    }

    /** Progress is kept per player under the entry id: every station they own and every scan they make adds to it. */
    public static String key(Research r) {
        return r.id().toString();
    }

    /** The entry is done for player: its unlocks are theirs, and they are told if they are online. */
    public static void complete(MinecraftServer server, Research r, UUID player) {
        final PortalData data = PortalData.get(server);
        boolean any = false;
        for (String u : r.unlocks()) any |= data.unlock(player, u);
        data.unlock(player, "research:" + r.id());
        final ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (any && p != null) {
            p.sendSystemMessage(Component.translatable("message.simplemachines.research.done", r.name()).withStyle(ChatFormatting.AQUA));
        }
    }

    // --- ownership, as entangled devices: public by default, the owner's screwdriver makes it private ---

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID who) {
        owner = who;
        setChanged();
    }

    @Override
    public boolean isPrivate() {
        return priv;
    }

    @Override
    public void setPrivate(boolean on) {
        priv = on;
        setChanged();
    }

    @Override
    public boolean canOpen(net.minecraft.world.entity.player.Player player) {
        if (!com.workshopcraft.simplemachines.portal.PortalApi.mayControl(this, player)) {
            player.sendOverlayMessage(Component.translatable("message.simplemachines.owned_not_owner").withStyle(ChatFormatting.RED));
            return false;
        }
        return super.canOpen(player);
    }

    private void show(@Nullable Research r) {
        final Identifier id = r == null ? null : r.id();
        if (java.util.Objects.equals(id, shown) && !items.get(SLOT_SHOW).isEmpty() == (r != null)) return;
        shown = id;
        if (r == null) {
            items.set(SLOT_SHOW, ItemStack.EMPTY);
            return;
        }
        ItemStack icon = r.icon();
        if (icon.isEmpty() && r.entity() != null) {
            final var type = BuiltInRegistries.ENTITY_TYPE.getOptional(r.entity()).orElse(null);
            if (type != null) icon = SpawnEggItem.byId(type).map(h -> new ItemStack(h)).orElse(ItemStack.EMPTY);
        }
        if (icon.isEmpty()) icon = new ItemStack(Items.PAPER);
        icon.set(DataComponents.ITEM_NAME, Component.literal(r.name()));
        final List<Component> lore = new ArrayList<>();
        for (String u : r.unlocks()) lore.add(describe(u).copy().withStyle(ChatFormatting.GRAY));
        icon.set(DataComponents.LORE, new ItemLore(lore));
        items.set(SLOT_SHOW, icon);
    }

    /** Plain words for an unlock key, for tooltips and messages. */
    public static Component describe(String key) {
        final int c = key.indexOf(':');
        if (c < 0) return Component.literal(key);
        final String kind = key.substring(0, c);
        final Identifier what = Identifier.tryParse(key.substring(c + 1));
        if (what == null) return Component.literal(key);
        return switch (kind) {
            case "ore" -> Component.translatable("tooltip.simplemachines.unlock.ore",
                    BuiltInRegistries.BLOCK.getOptional(what).map(b -> b.getName()).orElse(Component.literal(what.toString())));
            case "mob" -> Component.translatable("tooltip.simplemachines.unlock.mob",
                    BuiltInRegistries.ENTITY_TYPE.getOptional(what).map(t -> t.getDescription()).orElse(Component.literal(what.toString())));
            case "preset" -> Component.translatable("tooltip.simplemachines.unlock.preset",
                    Component.translatableWithFallback("portal_preset." + what.getNamespace() + "." + what.getPath(), what.getPath()));
            default -> Component.literal(key);
        };
    }

    public void step(int by) {
        selected += by;
        spent = 0;
        shown = null;
        setChanged();
    }

    /** Writes the chosen entry's ore and mob unlocks onto the jump card. Creative players may imprint before it is done. */
    public boolean imprint(ServerPlayer player) {
        final Research r = current();
        final ItemStack card = items.get(SLOT_CARD);
        if (r == null || !card.is(Portal.jumpCard)) return false;
        if (!com.workshopcraft.simplemachines.portal.PortalApi.playerProgramming(player.level().getServer())) {
            // closed mode server: research still unlocks worlds, but players do not program cards
            player.sendSystemMessage(Component.translatable("message.simplemachines.research.programming_off"));
            return false;
        }
        final PortalData data = PortalData.get(player.level().getServer());
        // the station's owner must have done the research; anyone may use a public station to program with it
        if (!player.isCreative() && data.scans(owner, key(r)) < (r.mob() ? r.scans() : r.count()) && !data.unlocked(owner, "research:" + r.id())) {
            player.sendSystemMessage(Component.translatable("message.simplemachines.research.not_done"));
            return false;
        }
        if (card.has(Portal.BOUND)) {
            player.sendSystemMessage(Component.translatable("message.simplemachines.research.card_bound", card.get(Portal.BOUND)));
            return false;
        }
        final Set<String> forced = new LinkedHashSet<>(card.getOrDefault(Portal.FORCED, List.of()));
        boolean any = false;
        for (String u : r.unlocks()) {
            if (u.startsWith("preset:")) {
                // one world type per card: a new one replaces the old
                forced.removeIf(f -> f.startsWith("preset:"));
                forced.add(u);
                any = true;
            } else if (u.startsWith("ore:") || u.startsWith("mob:")) {
                any |= forced.add(u);
            }
        }
        if (!any) {
            player.sendSystemMessage(Component.translatable("message.simplemachines.research.nothing_to_imprint"));
            return false;
        }
        if (forced.stream().filter(f -> !f.startsWith("preset:")).count() > MAX_FORCED) {
            player.sendSystemMessage(Component.translatable("message.simplemachines.research.card_full", MAX_FORCED));
            return false;
        }
        card.set(Portal.FORCED, List.copyOf(forced));
        setChanged();
        player.sendSystemMessage(Component.translatable("message.simplemachines.research.imprinted", r.name()));
        return true;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        items.set(SLOT_SHOW, ItemStack.EMPTY);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        selected = input.getIntOr("selected", 0);
        spent = input.getIntOr("spent", 0);
        owner = input.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        priv = input.getBooleanOr("private", false);
        items.set(SLOT_SHOW, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        final ItemStack keep = items.get(SLOT_SHOW);
        items.set(SLOT_SHOW, ItemStack.EMPTY);
        super.saveAdditional(output);
        items.set(SLOT_SHOW, keep);
        output.putInt("selected", selected);
        output.putInt("spent", spent);
        if (owner != null) output.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        if (priv) output.putBoolean("private", true);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ResearchMenu(id, inventory, this, researchData, this);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.simplemachines.research_station");
    }
}
