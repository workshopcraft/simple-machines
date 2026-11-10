package com.workshopcraft.simplemachines.machine;

import com.workshopcraft.simplemachines.SimpleMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
//? if >=26.1
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Vanilla smelting on FE, a quarter faster than a furnace on an iron chassis. */
public class ElectricFurnaceBlockEntity extends PoweredBlockEntity {
    public static final int CAPACITY = 10_000;
    public static final int PER_TICK = 20;
    public static final int MAX_IN = 200;

    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> check = RecipeManager.createCheck(RecipeType.SMELTING);

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleMachines.electricFurnaceEntity, pos, state, CAPACITY, MAX_IN, PER_TICK);
    }

    @Override
    protected @Nullable Job find(ServerLevel level, ItemStack input) {
        //? if portal {
        final ItemStack titanium = com.workshopcraft.simplemachines.portal.Titanium.smelt(input);
        if (!titanium.isEmpty()) return new Job(titanium, 1, 300);
        //?}
        final SingleRecipeInput in = new SingleRecipeInput(input);
        return check.getRecipeFor(in, level)
                .map(r -> new Job(r.value().assemble(in), 1, Math.max(1, r.value().cookingTime() * 3 / 4)))
                .orElse(null);
    }

    @Override
    protected boolean accepts(ServerLevel level, ItemStack stack) {
        //? if portal {
        if (!com.workshopcraft.simplemachines.portal.Titanium.smelt(stack).isEmpty()) return true;
        //?}
        //? if >=26.1 {
        return level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);
        //?} else {
        /*return check.getRecipeFor(new SingleRecipeInput(stack), level).isPresent();
        *///?}
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.simplemachines.electric_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new EnergyMenu(SimpleMachines.ELECTRIC_FURNACE_MENU, id, inventory, this, data);
    }
}
