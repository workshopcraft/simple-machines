package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.LadderBlock;

/** A steel rung ladder. Behaves like a vanilla ladder. */
public class ShipLadderBlock extends LadderBlock {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final MapCodec<LadderBlock> CODEC = (MapCodec) simpleCodec(ShipLadderBlock::new);

    public ShipLadderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<LadderBlock> codec() {
        return CODEC;
    }
}
