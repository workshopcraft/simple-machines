package com.workshopcraft.simplemachines.compat.mc;

import net.minecraft.world.level.material.PushReaction;

/** Push reactions that were renamed. */
public final class Pushes {
    private Pushes() {}

    /** A block pistons cannot move or destroy. 26.3 renamed BLOCK to IMMOVEABLE. */
    public static PushReaction immoveable() {
        //? if >=26.3 {
        /*return PushReaction.IMMOVEABLE;
        *///?} else {
        return PushReaction.BLOCK;
        //?}
    }
}
