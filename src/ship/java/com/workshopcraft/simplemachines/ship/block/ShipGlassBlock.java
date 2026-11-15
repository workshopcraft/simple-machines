package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.TransparentBlock;

/** Windows and grates: see-through, joins up with the same block next to it. */
public class ShipGlassBlock extends TransparentBlock {
    @SuppressWarnings({"unchecked", "rawtypes"})
    //? if <26.3 {
    private static final MapCodec<TransparentBlock> CODEC = (MapCodec) simpleCodec(ShipGlassBlock::new);
    //?}

    public ShipGlassBlock(Properties properties) {
        super(properties);
    }

    //? if <26.3 {
    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }
    //?}
}
