package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.TransparentBlock;

public class HullGlassBlock extends TransparentBlock implements Hull {
    public static final MapCodec<HullGlassBlock> CODEC = simpleCodec(HullGlassBlock::new);

    public HullGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }
}
