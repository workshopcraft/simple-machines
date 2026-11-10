package com.workshopcraft.simplemachines.ship;

import com.workshopcraft.simplemachines.item.HintItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** Fixes one broken ship block (see data/<ns>/simplemachines/repairs), or takes down a bulkhead wall. Used up. */
public class RepairKitItem extends HintItem {
    public RepairKitItem(Properties properties) {
        super(properties, "tooltip.simplemachines.repair_kit", "tooltip.simplemachines.repair_kit_bulkhead");
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (!ShipApi.repair(level, ctx.getClickedPos())) return InteractionResult.PASS;
        level.playSound(null, ctx.getClickedPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().sendOverlayMessage(Component.translatable("message.simplemachines.repaired"));
            if (!ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
