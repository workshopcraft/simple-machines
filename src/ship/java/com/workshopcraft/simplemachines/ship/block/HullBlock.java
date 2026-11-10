package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

public class HullBlock extends Block implements Hull {
    public static final MapCodec<HullBlock> CODEC = simpleCodec(HullBlock::new);

    public HullBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
