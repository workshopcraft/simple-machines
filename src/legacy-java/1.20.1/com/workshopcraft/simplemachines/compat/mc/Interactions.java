package com.workshopcraft.simplemachines.compat.mc;

import net.minecraft.world.InteractionResult;

/** 1.20.1 has no TRY_WITH_EMPTY_HAND; CONSUME_PARTIAL is otherwise unused by this mod. */
public final class Interactions {
    public static final InteractionResult TRY_WITH_EMPTY_HAND = InteractionResult.CONSUME_PARTIAL;
    public static final InteractionResult SUCCESS_SERVER = InteractionResult.SUCCESS;

    private Interactions() {
    }
}
