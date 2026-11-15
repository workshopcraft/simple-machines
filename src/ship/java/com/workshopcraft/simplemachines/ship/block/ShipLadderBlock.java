package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.LadderBlock;

/** A steel rung ladder. Behaves like a vanilla ladder. */
public class ShipLadderBlock extends LadderBlock {
    @SuppressWarnings({"unchecked", "rawtypes"})
    //? if <26.3 {
    private static final MapCodec<LadderBlock> CODEC = (MapCodec) simpleCodec(ShipLadderBlock::new);
    //?}

    public ShipLadderBlock(Properties properties) {
        super(properties);
    }

    //? if <26.3 {
    @Override
    public MapCodec<LadderBlock> codec() {
        return CODEC;
    }
    //?}
}
