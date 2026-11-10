package com.workshopcraft.simplemachines.ship.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.ship.ShipData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A two block cryopod the player sleeps in. Sleeping sets spawn here, even where beds would explode.
 * With start=true (placed by a start structure, or the Start Cryopod item) a new player begins the world in it.
 */
public class CryopodBedBlock extends BedBlock {
    public static final BooleanProperty START = BooleanProperty.create("start");
    public static final MapCodec<CryopodBedBlock> CODEC = simpleCodec(CryopodBedBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public CryopodBedBlock(Properties properties) {
        super(DyeColor.LIGHT_GRAY, properties);
        registerDefaultState(defaultBlockState().setValue(START, false));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<BedBlock> codec() {
        return (MapCodec) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(START);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS_SERVER;
        final BlockPos head = state.getValue(PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(FACING));
        final BlockState headState = level.getBlockState(head);
        if (!headState.is(this)) return InteractionResult.CONSUME;
        final BedRule rule = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, head);
        if (rule.explodes() || !rule.canSetSpawn(level)) {
            // no explosion in a pod: link spawn and say so, the player stays awake
            if (player instanceof ServerPlayer sp && linkSpawn(sp, (ServerLevel) level, head, headState)) {
                sp.sendOverlayMessage(Component.translatable("message.simplemachines.cryopod_linked"));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** Sets a forced spawn next to the pod, for dimensions where beds cannot set spawn. */
    public static boolean linkSpawn(ServerPlayer player, ServerLevel level, BlockPos head, BlockState headState) {
        final Optional<Vec3> stand = standUp(level, head, headState, player.getYRot());
        if (stand.isEmpty()) return false;
        final BlockPos at = BlockPos.containing(stand.get());
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), at, player.getYRot(), 0F), true), false);
        return true;
    }

    public static Optional<Vec3> standUp(ServerLevel level, BlockPos head, BlockState headState, float yaw) {
        return BedBlock.findStandUpPosition(EntityType.PLAYER, level, head, headState.getValue(FACING), yaw);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (state.getValue(START) && state.getValue(PART) == BedPart.HEAD && level instanceof ServerLevel sl) {
            ShipData.get(sl.getServer()).addStartPod(GlobalPos.of(sl.dimension(), pos.immutable()));
        }
    }

    /** The head block of the cryopod at pos, or null. */
    public static @Nullable BlockPos head(Level level, BlockPos pos) {
        final BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CryopodBedBlock)) return null;
        return s.getValue(PART) == BedPart.HEAD ? pos.immutable() : pos.relative(s.getValue(FACING)).immutable();
    }

    public static Direction facing(BlockState s) {
        return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }
}
