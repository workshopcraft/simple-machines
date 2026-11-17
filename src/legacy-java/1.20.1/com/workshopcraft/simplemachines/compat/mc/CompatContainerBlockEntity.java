package com.workshopcraft.simplemachines.compat.mc;

import com.workshopcraft.simplemachines.compat.mc.component.DataComponentMap;
import com.workshopcraft.simplemachines.compat.mc.component.DataComponentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 1.20.1 BaseContainerBlockEntity with the 1.20.5+ item list helpers and 26.x style save/load. */
public abstract class CompatContainerBlockEntity extends BaseContainerBlockEntity implements Removal, ComponentHolder {
    protected CompatContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract NonNullList<ItemStack> getItems();

    protected abstract void setItems(NonNullList<ItemStack> items);

    // components set on the block entity itself (26.x setComponents), kept beside the implicit ones under SimpleMachines
    private CompoundTag explicit = new CompoundTag();

    @Override
    public final void load(CompoundTag tag) {
        super.load(tag);
        loadAdditional(new ValueInput(tag, null));
        explicit = tag.getCompound(Comps.KEY).copy();
        final DataComponentMap.Builder b = new DataComponentMap.Builder();
        collectImplicitComponents(b);
        for (DataComponentType<?> t : b.values().keySet()) if (t.name() != null) explicit.remove(t.name());
    }

    @Override
    protected final void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveAdditional(new ValueOutput(tag, null));
        final DataComponentMap.Builder b = new DataComponentMap.Builder();
        collectImplicitComponents(b);
        final CompoundTag data = explicit.copy();
        Comps.write(data, b);
        if (!data.isEmpty()) tag.put(Comps.KEY, data);
    }

    public CompoundTag explicitComponents() {
        return explicit;
    }

    @Nullable
    public <T> T getComponent(DataComponentType<T> type) {
        return Comps.read(explicit, type);
    }

    public <T> void setComponent(DataComponentType<T> type, @Nullable T value) {
        explicit.remove(type.name());
        if (value != null) Comps.write(explicit, new DataComponentMap.Builder().set(type, value));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return getUpdateTag((HolderLookup.Provider) null);
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return super.getUpdateTag();
    }

    public CompoundTag saveWithoutMetadata(HolderLookup.Provider registries) {
        return saveWithoutMetadata();
    }

    public CompoundTag saveCustomOnly(HolderLookup.Provider registries) {
        return saveWithoutMetadata();
    }

    public void applyComponentsFrom(DataComponentGetter components) {
        applyImplicitComponents(components);
        setChanged();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : getItems()) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return getItems().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        final ItemStack s = ContainerHelper.removeItem(getItems(), slot, count);
        if (!s.isEmpty()) setChanged();
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(getItems(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        getItems().set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        getItems().clear();
    }

    protected void loadAdditional(ValueInput input) {
    }

    protected void saveAdditional(ValueOutput output) {
    }

    protected void applyImplicitComponents(DataComponentGetter components) {
    }

    protected void collectImplicitComponents(DataComponentMap.Builder components) {
    }

    public void removeComponentsFromTag(ValueOutput output) {
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, this);
    }

    public int getMaxStackSize(ItemStack stack) {
        return Math.min(getMaxStackSize(), stack.getMaxStackSize());
    }
}
