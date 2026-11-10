package com.workshopcraft.simplemachines.ship;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/** Puts the finish named in its simplemachines:machine_finish component on a built machine. The plain kit takes it off. */
public class MachineFinishKitItem extends Item {
    public MachineFinishKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        final Identifier finish = ctx.getItemInHand().getOrDefault(Ship.MACHINE_FINISH, MachineFinishes.NONE);
        if (!MachineFinishes.accepts(ctx.getLevel().getBlockEntity(ctx.getClickedPos()))) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (!MachineFinishes.apply(level.getBlockEntity(ctx.getClickedPos()), finish)) return InteractionResult.FAIL;
        level.playSound(null, ctx.getClickedPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        if (ctx.getPlayer() != null && !ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public Component getName(ItemStack stack) {
        final Identifier f = stack.getOrDefault(Ship.MACHINE_FINISH, MachineFinishes.NONE);
        return Component.translatable("item.simplemachines.machine_finish_kit.named", MachineFinishes.name(f));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.simplemachines.machine_finish_kit").withStyle(ChatFormatting.GRAY));
        if (flag.isAdvanced()) out.accept(Component.literal(stack.getOrDefault(Ship.MACHINE_FINISH, MachineFinishes.NONE).toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
