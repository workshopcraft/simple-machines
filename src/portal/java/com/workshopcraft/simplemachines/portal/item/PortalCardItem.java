package com.workshopcraft.simplemachines.portal.item;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.PortalCard;
import com.workshopcraft.simplemachines.portal.block.ResearchStationBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A card item. It carries a card definition id (simplemachines:portal_card), for jump cards the programming done at a
 * Research Station (simplemachines:portal_forced: ores, mobs, a world type) and, after its first jump, the world it is
 * bound to for good (simplemachines:portal_world). The definition itself is data.
 */
public class PortalCardItem extends Item {
    /** Story cards that ship with the mod, listed in the creative tab. Pack cards are given with /simplemachines portal givecard. */
    public static final List<String> BUILT_IN_STORY = List.of("story/lost_signal", "story/ember_vault", "story/silent_spire");

    private final PortalCard.Kind kind;

    public PortalCardItem(Properties properties, PortalCard.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public PortalCard.Kind kind() {
        return kind;
    }

    public static List<ItemStack> creativeCards() {
        final List<ItemStack> out = new ArrayList<>();
        for (String id : BUILT_IN_STORY) {
            final ItemStack s = new ItemStack(Portal.storyCard);
            s.set(Portal.CARD, SimpleMachines.id(id));
            out.add(s);
        }
        return out;
    }

    public static Component cardName(Identifier id) {
        return Component.translatableWithFallback("portal_card." + id.getNamespace() + "." + id.getPath().replace('/', '.'), id.getPath());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        final Identifier id = stack.get(Portal.CARD);
        if (id == null) {
            out.accept(Component.translatable("tooltip.simplemachines.card_blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(cardName(id).copy().withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("tooltip.simplemachines.card_" + kind.id()).withStyle(ChatFormatting.GRAY));
        final String bound = stack.get(Portal.BOUND);
        if (bound != null) out.accept(Component.translatable("tooltip.simplemachines.card_bound", bound).withStyle(ChatFormatting.LIGHT_PURPLE));
        else if (kind == PortalCard.Kind.JUMP) out.accept(Component.translatable("tooltip.simplemachines.card_unbound").withStyle(ChatFormatting.DARK_GRAY));
        final List<String> forced = stack.get(Portal.FORCED);
        if (forced != null) for (String f : forced) out.accept(ResearchStationBlockEntity.describe(f).copy().withStyle(ChatFormatting.DARK_AQUA));
    }
}
