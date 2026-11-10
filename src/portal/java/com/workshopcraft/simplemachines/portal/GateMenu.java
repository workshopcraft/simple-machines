package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.machine.EnergyMenu;
import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Gate controller screen: core, card and pellet slots, status, cost and the Open and Close buttons. */
public class GateMenu extends AbstractContainerMenu {
    public static final int BUTTON_OPEN = 0, BUTTON_CLOSE = 1;
    private final Container container;
    private final ContainerData data;
    private final @Nullable GateControllerBlockEntity gate;

    public static GateMenu client(int id, Inventory inventory) {
        return new GateMenu(id, inventory, new SimpleContainer(GateControllerBlockEntity.SLOTS),
                new SimpleContainerData(GateControllerBlockEntity.DATA_COUNT), null);
    }

    public GateMenu(int id, Inventory inventory, Container container, ContainerData data, @Nullable GateControllerBlockEntity gate) {
        super(Portal.GATE_MENU, id);
        checkContainerSize(container, GateControllerBlockEntity.SLOTS);
        checkContainerDataCount(data, GateControllerBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.gate = gate;
        addSlot(new Only(container, GateControllerBlockEntity.SLOT_CORE, 8, 18));
        addSlot(new Only(container, GateControllerBlockEntity.SLOT_CARD, 8, 40));
        for (int i = 0; i < 3; i++) addSlot(new Only(container, GateControllerBlockEntity.FIRST_PELLET + i, 8 + i * 18, 62));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    private static final class Only extends Slot {
        Only(Container c, int slot, int x, int y) {
            super(c, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return GateControllerBlockEntity.valid(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return getContainerSlot() < GateControllerBlockEntity.FIRST_PELLET ? 1 : 64;
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (gate == null || !(player instanceof ServerPlayer sp) || !(player.level() instanceof ServerLevel level)) return false;
        if (id == BUTTON_OPEN) {
            final String problem = gate.tryOpen(sp);
            if (problem != null) sp.sendSystemMessage(Component.translatable(problem), true);
            return true;
        }
        if (id == BUTTON_CLOSE) {
            gate.close(level);
            return true;
        }
        return false;
    }

    public int energy() {
        return (data.get(2) & 0xFFFF) | (data.get(3) & 0xFFFF) << 16;
    }

    public int capacity() {
        return Math.max(1, data.get(4) & 0xFFFF) * 1000;
    }

    public float energyFraction() {
        return Mth.clamp((float) energy() / capacity(), 0, 1);
    }

    public int voltage() {
        return EnergyMenu.voltage(data.get(6));
    }

    public int status() {
        return Mth.clamp(data.get(5), 0, GateControllerBlockEntity.STATUS.length - 1);
    }

    public boolean open() {
        return status() == GateControllerBlockEntity.OPEN || status() == GateControllerBlockEntity.STARTING;
    }

    public float openFraction() {
        final int a = data.get(0), b = data.get(1);
        return b <= 0 ? 0 : Mth.clamp((float) a / b, 0, 1);
    }

    public int needPellets() {
        return data.get(7);
    }

    public int needEnergy() {
        return (data.get(8) & 0xFFFF) * 1000;
    }

    /** 0 for no frame, otherwise FrameTier.level. */
    public int frameTier() {
        return data.get(9);
    }

    public int frameWidth() {
        return data.get(10) >> 8 & 0xFF;
    }

    public int frameHeight() {
        return data.get(10) & 0xFF;
    }

    public boolean enabled() {
        return (data.get(11) & 1) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        final int size = GateControllerBlockEntity.SLOTS;
        final ItemStack stack = slot.getItem();
        final ItemStack before = stack.copy();
        if (index < size) {
            if (!moveItemStackTo(stack, size, size + 36, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < size && !moved; i++) {
                if (GateControllerBlockEntity.valid(i, stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return before;
    }
}
