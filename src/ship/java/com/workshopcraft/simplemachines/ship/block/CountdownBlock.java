package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.ship.Ship;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * A jump countdown display. A redstone pulse starts it from 30 seconds; /simplemachines countdown or ShipApi sets
 * any time. At zero it shows JUMP and gives a redstone signal for one second.
 */
public class CountdownBlock extends FacingEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private MapCodec<CountdownBlock> codec;

    public CountdownBlock(Properties properties, double[][] north) {
        super(properties, north);
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(TRIGGERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        if (codec == null) codec = simpleCodec(p -> this);
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, TRIGGERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CountdownBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != Ship.COUNTDOWN_ENTITY ? null : (l, p, s, be) -> ((CountdownBlockEntity) be).serverTick();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (level.isClientSide()) return;
        final boolean on = level.hasNeighborSignal(pos);
        if (on == state.getValue(TRIGGERED)) return;
        level.setBlock(pos, state.setValue(TRIGGERED, on), 2);
        if (on && level.getBlockEntity(pos) instanceof CountdownBlockEntity c) c.start(CountdownBlockEntity.DEFAULT_SECONDS);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
