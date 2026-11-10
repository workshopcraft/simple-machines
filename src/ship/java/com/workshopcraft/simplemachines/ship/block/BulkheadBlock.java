package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.ShipConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inner walls between rooms. A ship tool or repair kit takes down the whole connected wall so two rooms become one;
 * sneaking takes down one block. Pickaxes break them too unless ship_bulkheads_need_tool is on.
 */
public class BulkheadBlock extends Block {
    public static final MapCodec<BulkheadBlock> CODEC = simpleCodec(BulkheadBlock::new);

    public BulkheadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.isCreative() && player.level() instanceof ServerLevel sl && ShipConfig.bool(sl.getServer(), ShipConfig.BULKHEADS_NEED_TOOL)) return 0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Ship.SHIP_TOOLS)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        final int max = player.isShiftKeyDown() ? 1 : Math.max(1, ShipConfig.integer(server.getServer(), ShipConfig.BULKHEAD_MAX_REMOVAL));
        final List<BlockPos> wall = connected(level, pos, max);
        for (BlockPos p : wall) level.destroyBlock(p, !player.isCreative(), player);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1F, 0.6F);
        if (!player.isCreative()) {
            if (stack.is(Ship.REPAIR_KITS)) stack.shrink(1);
            else if (stack.isDamageableItem()) stack.hurtAndBreak(wall.size(), player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        player.sendOverlayMessage(Component.translatable("message.simplemachines.bulkhead_removed", wall.size()));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Bulkhead blocks joined face to face with pos, nearest first, at most max. */
    public static List<BlockPos> connected(Level level, BlockPos pos, int max) {
        final List<BlockPos> out = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty() && out.size() < max) {
            final BlockPos p = queue.poll();
            if (!(level.getBlockState(p).getBlock() instanceof BulkheadBlock)) continue;
            out.add(p);
            for (Direction d : Direction.values()) {
                final BlockPos n = p.relative(d);
                if (seen.add(n)) queue.add(n);
            }
        }
        return out;
    }
}
