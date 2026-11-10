package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.machine.EnergyMenu;
import com.workshopcraft.simplemachines.portal.block.ResearchStationBlockEntity;
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

/** Research Station screen: input, card and the shown entry, progress and the arrow and Imprint buttons. */
public class ResearchMenu extends AbstractContainerMenu {
    public static final int BUTTON_PREV = 0, BUTTON_NEXT = 1, BUTTON_IMPRINT = 2;
    private final Container container;
    private final ContainerData data;
    private final @Nullable ResearchStationBlockEntity station;

    public static ResearchMenu client(int id, Inventory inventory) {
        return new ResearchMenu(id, inventory, new SimpleContainer(3), new SimpleContainerData(ResearchStationBlockEntity.DATA_COUNT), null);
    }

    public ResearchMenu(int id, Inventory inventory, Container container, ContainerData data, @Nullable ResearchStationBlockEntity station) {
        super(Portal.RESEARCH_MENU, id);
        checkContainerSize(container, 3);
        checkContainerDataCount(data, ResearchStationBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.station = station;
        addSlot(new Slot(container, ResearchStationBlockEntity.SLOT_INPUT, 26, 53));
        addSlot(new Slot(container, ResearchStationBlockEntity.SLOT_CARD, 116, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Portal.jumpCard);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, ResearchStationBlockEntity.SLOT_SHOW, 26, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (station == null || !(player instanceof ServerPlayer sp)) return false;
        switch (id) {
            case BUTTON_PREV -> station.step(-1);
            case BUTTON_NEXT -> station.step(1);
            case BUTTON_IMPRINT -> station.imprint(sp);
            default -> {
                return false;
            }
        }
        return true;
    }

    public ItemStack shown() {
        return container.getItem(ResearchStationBlockEntity.SLOT_SHOW);
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

    public float itemProgress() {
        final int a = data.get(0), b = data.get(1);
        return b <= 0 ? 0 : Mth.clamp((float) a / b, 0, 1);
    }

    public int count() {
        return data.get(5) & 0xFFFF;
    }

    public int need() {
        return data.get(7) & 0xFFFF;
    }

    public boolean unlocked() {
        return (data.get(8) & 1) != 0;
    }

    public boolean mob() {
        return (data.get(8) & 2) != 0;
    }

    public int index() {
        return data.get(9);
    }

    public int total() {
        return data.get(10);
    }

    public boolean hasOwner() {
        return (data.get(19) & 1) != 0;
    }

    public boolean isPrivate() {
        return (data.get(19) & 2) != 0;
    }

    public java.util.UUID owner() {
        long hi = 0, lo = 0;
        for (int i = 0; i < 4; i++) {
            hi = hi << 16 | (data.get(11 + i) & 0xFFFF);
            lo = lo << 16 | (data.get(15 + i) & 0xFFFF);
        }
        return new java.util.UUID(hi, lo);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        final Slot slot = slots.get(index);
        if (!slot.hasItem() || index == 2) return ItemStack.EMPTY;
        final ItemStack stack = slot.getItem();
        final ItemStack before = stack.copy();
        if (index < 3) {
            if (!moveItemStackTo(stack, 3, 39, true)) return ItemStack.EMPTY;
        } else if (stack.is(Portal.jumpCard)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return before;
    }
}
