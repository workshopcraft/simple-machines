package com.workshopcraft.simplemachines.portal.item;

import com.workshopcraft.simplemachines.portal.EmergencyReturn;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <26.1
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A one-shot craftable rocket. Use it to ride a short launch and then return to the last gate (or spawn). */
public class EmergencyRocketItem extends Item {
    public EmergencyRocketItem(Properties properties) {
        super(properties);
    }

    //? if >=26.1 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!EmergencyReturn.tryLaunch(level, player, stack)) return InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!EmergencyReturn.tryLaunch(level, player, stack)) return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    *///?}
}
