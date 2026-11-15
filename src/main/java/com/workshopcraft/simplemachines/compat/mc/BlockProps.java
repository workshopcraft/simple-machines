package com.workshopcraft.simplemachines.compat.mc;

import net.minecraft.world.level.block.state.BlockBehaviour;

/** Block property predicates whose lambda shape changed between versions. */
public final class BlockProps {
    private BlockProps() {}

    /** A block you can see and walk through: no occlusion, not view blocking, not suffocating. */
    public static BlockBehaviour.Properties seeThrough(BlockBehaviour.Properties p) {
        // 26.3 passes the tested box to isViewBlocking
        //? if >=26.3 {
        /*return p.isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false);
        *///?} else {
        return p.isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false);
        //?}
    }

    /** The same, translucent and with nothing spawning on it. */
    public static BlockBehaviour.Properties open(BlockBehaviour.Properties p) {
        return seeThrough(p).noOcclusion().isValidSpawn((s, l, pos, e) -> false);
    }
}
