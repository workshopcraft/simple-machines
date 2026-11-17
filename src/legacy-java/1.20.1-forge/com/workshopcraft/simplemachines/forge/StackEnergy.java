package com.workshopcraft.simplemachines.forge;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.compat.mc.Comps;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/** Item energy kept in the stack's energy component, like NeoForge's ComponentEnergyStorage. */
public final class StackEnergy implements IEnergyStorage {
    private final ItemStack stack;
    private final int capacity;
    private final int rate;

    public StackEnergy(ItemStack stack, int capacity, int rate) {
        this.stack = stack;
        this.capacity = capacity;
        this.rate = rate;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        final int stored = getEnergyStored();
        final int n = Math.max(0, Math.min(capacity - stored, Math.min(amount, rate)));
        if (!simulate && n > 0) Comps.set(stack, SimpleMachines.ENERGY, stored + n);
        return n;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        final int stored = getEnergyStored();
        final int n = Math.max(0, Math.min(stored, Math.min(amount, rate)));
        if (!simulate && n > 0) Comps.set(stack, SimpleMachines.ENERGY, stored - n);
        return n;
    }

    @Override
    public int getEnergyStored() {
        final Integer e = Comps.get(stack, SimpleMachines.ENERGY);
        return e == null ? 0 : Math.min(e, capacity);
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
