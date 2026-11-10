package com.workshopcraft.simplemachines.portal.item;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.cable.Voltage;
import com.workshopcraft.simplemachines.item.PowerTooltip;
import com.workshopcraft.simplemachines.portal.PortalConfig;
import com.workshopcraft.simplemachines.portal.PortalData;
import com.workshopcraft.simplemachines.portal.Research;
import com.workshopcraft.simplemachines.portal.block.ResearchStationBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Mob Scanner: a powered handheld tool, charged in a Charger. Use it on a mob to scan it for mob research.
 * Each mob can only be scanned once (the scan is tagged on the mob). With scanner_kills_mob the scan kills it.
 */
public class MobScannerItem extends Item {
    public static final int CAPACITY = 40_000;
    public static final int RATE = 1_000;
    public static final int PER_SCAN = 4_000;
    public static final String SCANNED = "simplemachines.scanned";

    public MobScannerItem(Properties properties) {
        super(properties);
    }

    public static ItemStack charged(Item item) {
        final ItemStack s = new ItemStack(item);
        s.set(SimpleMachines.ENERGY, CAPACITY);
        return s;
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(SimpleMachines.ENERGY, 0);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || !(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        final ItemStack held = player.getItemInHand(hand);
        if (target.entityTags().contains(SCANNED)) {
            sp.sendSystemMessage(Component.translatable("message.simplemachines.scanner.already"), true);
            return InteractionResult.FAIL;
        }
        if (!player.isCreative() && energy(held) < PER_SCAN) {
            sp.sendSystemMessage(Component.translatable("message.simplemachines.scanner.no_power"), true);
            return InteractionResult.FAIL;
        }
        Research match = null;
        final var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        for (Research r : ResearchStationBlockEntity.list(level.getServer())) if (r.mob() && typeId.equals(r.entity())) match = r;
        if (match == null) {
            sp.sendSystemMessage(Component.translatable("message.simplemachines.scanner.nothing", target.getType().getDescription()), true);
            return InteractionResult.FAIL;
        }
        if (!player.isCreative()) held.set(SimpleMachines.ENERGY, energy(held) - PER_SCAN);
        target.addTag(SCANNED);
        final PortalData data = PortalData.get(level.getServer());
        final int n = data.addScan(ResearchStationBlockEntity.key(match));
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        sp.sendSystemMessage(Component.translatable("message.simplemachines.scanner.scanned", target.getType().getDescription(),
                Math.min(n, match.scans()), match.scans()), true);
        if (n == match.scans()) ResearchStationBlockEntity.complete(level.getServer(), match, sp);
        if (PortalConfig.bool(level.getServer(), PortalConfig.SCANNER_KILLS_MOB)) target.kill(level);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * Mth.clamp(energy(stack) / (float) CAPACITY, 0, 1));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x4FC3F7;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.simplemachines.mob_scanner").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.simplemachines.energy", PowerTooltip.num(energy(stack)), PowerTooltip.num(CAPACITY))
                .withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.simplemachines.charges_at", PowerTooltip.tier(Voltage.LV), PowerTooltip.num(RATE))
                .withStyle(ChatFormatting.GRAY));
    }
}
