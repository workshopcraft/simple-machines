package com.workshopcraft.simplemachines.compat.mc;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;

/** Maps 26.x style block item-use results onto 1.21.1's ItemInteractionResult. */
public final class Interactions {
    /** 26.x TRY_WITH_EMPTY_HAND; SUCCESS_NO_ITEM_USED is otherwise unused by this mod. */
    public static final InteractionResult TRY_WITH_EMPTY_HAND = InteractionResult.SUCCESS_NO_ITEM_USED;
    /** 26.x SUCCESS_SERVER (the server swings the arm); plain SUCCESS does the same on the server here. */
    public static final InteractionResult SUCCESS_SERVER = InteractionResult.SUCCESS;

    private Interactions() {
    }

    public static ItemInteractionResult item(InteractionResult r) {
        return switch (r) {
            case SUCCESS_NO_ITEM_USED -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            case SUCCESS -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
        };
    }
}
