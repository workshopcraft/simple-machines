package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A grow tray: plant wheat seeds, carrots, potatoes or beetroot seeds, it grows without light or water, pick when ripe. */
public class HydroponicsTrayBlock extends Block {
    public enum Crop implements StringRepresentable {
        NONE("none", Items.AIR, Items.AIR, 0),
        WHEAT("wheat", Items.WHEAT_SEEDS, Items.WHEAT, 1),
        CARROT("carrot", Items.CARROT, Items.CARROT, 3),
        POTATO("potato", Items.POTATO, Items.POTATO, 3),
        BEETROOT("beetroot", Items.BEETROOT_SEEDS, Items.BEETROOT, 1);

        final String name;
        final Item seed, crop;
        final int count;

        Crop(String name, Item seed, Item crop, int count) {
            this.name = name;
            this.seed = seed;
            this.crop = crop;
            this.count = count;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Crop> CROP = EnumProperty.create("crop", Crop.class);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    public static final MapCodec<HydroponicsTrayBlock> CODEC = simpleCodec(HydroponicsTrayBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public HydroponicsTrayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CROP, Crop.NONE).setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CROP, AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(CROP) != Crop.NONE && state.getValue(AGE) < 3;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(CROP) != Crop.NONE) return InteractionResult.TRY_WITH_EMPTY_HAND;
        for (Crop c : Crop.values()) {
            if (c == Crop.NONE || !stack.is(c.seed)) continue;
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(CROP, c).setValue(AGE, 0), 3);
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1F, 1F);
                if (!player.isCreative()) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        final Crop c = state.getValue(CROP);
        if (c == Crop.NONE || state.getValue(AGE) < 3) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            final RandomSource r = level.getRandom();
            popResource(level, pos.above(), new ItemStack(c.crop, c.count + r.nextInt(2)));
            if (c.seed != c.crop) popResource(level, pos.above(), new ItemStack(c.seed, 1 + r.nextInt(2)));
            level.setBlock(pos, state.setValue(AGE, 0), 3);
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1F, 1F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        final java.util.List<ItemStack> drops = new java.util.ArrayList<>(super.getDrops(state, params));
        final Crop c = state.getValue(CROP);
        if (c != Crop.NONE) drops.add(new ItemStack(state.getValue(AGE) == 3 ? c.crop : c.seed));
        return drops;
    }
}
