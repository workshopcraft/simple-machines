package com.workshopcraft.simplemachines.machine;

import com.workshopcraft.simplemachines.assembly.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Input slots then one output slot, runs a job on FE. Speed and storage follow the chassis tier. */
public abstract class PoweredBlockEntity extends WorkBlockEntity {
    /** counts is how many to take from each input slot; bonus is rolled once per job with the given chance. */
    protected record Job(ItemStack result, int[] counts, int time, ItemStack bonus, float chance) {
        public Job(ItemStack result, int count, int time) {
            this(result, new int[]{count}, time, ItemStack.EMPTY, 0);
        }
    }

    private final int inputs;
    private final int perTick;
    private List<ItemStack> cachedFor = List.of();
    private @Nullable Job cached;

    protected PoweredBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int maxIn, int perTick) {
        this(type, pos, state, 1, capacity, maxIn, perTick);
    }

    protected PoweredBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inputs, int capacity, int maxIn, int perTick) {
        super(type, pos, state, inputs + 1, capacity, maxIn, 0);
        this.inputs = inputs;
        this.perTick = perTick;
    }

    protected @Nullable Job find(ServerLevel level, ItemStack input) {
        return null;
    }

    protected @Nullable Job find(ServerLevel level, List<ItemStack> inputs) {
        return find(level, inputs.get(0));
    }

    protected boolean accepts(ServerLevel level, ItemStack stack) {
        return false;
    }

    @Override
    protected boolean accepts(ServerLevel level, int slot, ItemStack stack) {
        return accepts(level, stack);
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == inputs;
    }

    @Override
    public boolean energyOut(@Nullable Direction side) {
        return false;
    }

    protected int perTick(Tier tier) {
        return perTick;
    }

    protected void forget() {
        cachedFor = List.of();
        cached = null;
        progress = 0;
    }

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state, Tier tier) {
        boolean changed = false;
        final List<ItemStack> in = new ArrayList<>(inputs);
        boolean empty = true;
        for (int i = 0; i < inputs; i++) {
            in.add(items.get(i));
            if (!items.get(i).isEmpty()) empty = false;
        }
        if (!same(in, cachedFor)) {
            final List<ItemStack> copy = new ArrayList<>(inputs);
            for (ItemStack s : in) copy.add(s.copy());
            cachedFor = copy;
            cached = empty ? null : find(level, in);
        }
        Job job = cached;
        //? if portal {
        if (job != null && tier != Tier.STEEL && com.workshopcraft.simplemachines.portal.Titanium.needsSteel(in)) job = null;
        //?}
        boolean ready = job != null && fits(inputs, job.result());
        if (ready) for (int i = 0; i < job.counts().length; i++) if (items.get(i).getCount() < job.counts()[i]) ready = false;
        boolean working = false;
        final int cost = perTick(tier);
        if (ready) {
            total = tier.time(job.time());
            if (energy.energy >= cost) {
                energy.energy -= cost;
                working = true;
                changed = true;
                if (++progress >= total) {
                    progress = 0;
                    for (int i = 0; i < job.counts().length; i++) items.get(i).shrink(job.counts()[i]);
                    final ItemStack out = items.get(inputs);
                    ItemStack result = job.result().copy();
                    if (!job.bonus().isEmpty() && level.getRandom().nextFloat() < job.chance()
                            && result.getCount() + job.bonus().getCount() <= result.getMaxStackSize()
                            && ItemStack.isSameItemSameComponents(result, job.bonus())) {
                        result.grow(job.bonus().getCount());
                        if (!fits(inputs, result)) result.shrink(job.bonus().getCount());
                    }
                    if (out.isEmpty()) items.set(inputs, result);
                    else out.grow(result.getCount());
                }
            }
        } else if (progress != 0) {
            progress = 0;
            changed = true;
        }
        if (state.getValue(MachineBlock.LIT) != working) {
            state = state.setValue(MachineBlock.LIT, working);
            level.setBlock(pos, state, 3);
            changed = true;
        }
        if (changed) setChanged(level, pos, state);
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        final boolean same = !stack.isEmpty() && ItemStack.isSameItemSameComponents(items.get(slot), stack);
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        if (slot < inputs && !same) {
            progress = 0;
            setChanged();
        }
    }
}
