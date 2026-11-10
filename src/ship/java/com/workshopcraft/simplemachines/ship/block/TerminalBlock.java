package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.ship.ShipConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A story log terminal. Use it to read the log in chat. Use a Book and Quill or Written Book on it to load the book's
 * pages as the log (unless the terminal is locked, or ship_story_logs_editable is off). /simplemachines log sets it too.
 */
public class TerminalBlock extends FacingEntityBlock {
    private MapCodec<TerminalBlock> codec;

    public TerminalBlock(Properties properties, double[][] north) {
        super(properties, north);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        if (codec == null) codec = simpleCodec(p -> this);
        return codec;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        final WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        final WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writable == null && written == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TerminalBlockEntity t)) return InteractionResult.PASS;
        if (!player.isCreative() && (t.locked() || !ShipConfig.bool(sl.getServer(), ShipConfig.LOGS_EDITABLE))) {
            player.sendOverlayMessage(Component.translatable("message.simplemachines.log_locked"));
            return InteractionResult.SUCCESS_SERVER;
        }
        final List<String> pages = new ArrayList<>();
        String title = "";
        if (written != null) {
            title = written.title().raw();
            for (Component c : written.getPages(false)) pages.add(c.getString());
        } else {
            writable.getPages(false).forEach(pages::add);
        }
        t.setLog(title, String.join("\n", pages));
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 1.4F);
        player.sendOverlayMessage(Component.translatable("message.simplemachines.log_written"));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof TerminalBlockEntity t) {
            if (t.text().isEmpty()) player.sendSystemMessage(Component.translatable("message.simplemachines.log_empty"));
            else {
                if (!t.title().isEmpty()) player.sendSystemMessage(Component.literal("[" + t.title() + "]").withStyle(net.minecraft.ChatFormatting.GREEN));
                for (String line : t.text().split("\n")) player.sendSystemMessage(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.8F);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
