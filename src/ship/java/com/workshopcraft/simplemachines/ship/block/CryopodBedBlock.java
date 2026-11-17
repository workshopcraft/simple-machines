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
//? if >=26.1 {
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
//?}
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
    //? if >=1.20.5 <26.3 {
    public static final MapCodec<CryopodBedBlock> CODEC = simpleCodec(CryopodBedBlock::new);
    //?}
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public CryopodBedBlock(Properties properties) {
        super(DyeColor.LIGHT_GRAY, properties);
        registerDefaultState(defaultBlockState().setValue(START, false));
    }

    //? if >=1.20.5 <26.3 {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<BedBlock> codec() {
        return (MapCodec) CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(START);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // 26.2: BedBlock no longer implements EntityBlock, so there is nothing to override
    //? if <26.2 {
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
    //?}

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
    *///?}
        if (level.isClientSide()) return InteractionResult.SUCCESS_SERVER;
        final BlockPos head = state.getValue(PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(FACING));
        final BlockState headState = level.getBlockState(head);
        if (!headState.is(this)) return InteractionResult.CONSUME;
        if (refusesSleep(level, head)) {
            // no explosion in a pod: link spawn and say so, the player stays awake
            if (player instanceof ServerPlayer sp && linkSpawn(sp, (ServerLevel) level, head, headState)) {
                sp.sendOverlayMessage(Component.translatable("message.simplemachines.cryopod_linked"));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        //? if >=1.20.5 {
        return super.useWithoutItem(state, level, pos, player, hit);
        //?} else {
        /*return super.use(state, level, pos, player, hand, hit);
        *///?}
    }

    /** Whether sleeping here is refused and would hurt the sleeper, so the pod links a spawn instead. */
    private static boolean refusesSleep(Level level, BlockPos head) {
        //? if >=26.3 {
        /*final BedRule rule = bedRule(level, head);
        return rule.destroyOnUse() || rule.destroyOnLeave() || !rule.canSetSpawn(level);
        *///?} else if >=26.1 {
        final BedRule rule = bedRule(level, head);
        return rule.explodes() || !rule.canSetSpawn(level);
        //?} else {
        /*return !level.dimensionType().bedWorks();
        *///?}
    }

    //? if >=26.1 {
    private static BedRule bedRule(Level level, BlockPos head) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, head);
    }
    //?}

    /** Sets a forced spawn next to the pod, for dimensions where beds cannot set spawn. */
    public static boolean linkSpawn(ServerPlayer player, ServerLevel level, BlockPos head, BlockState headState) {
        final Optional<Vec3> stand = standUp(level, head, headState, player.getYRot());
        if (stand.isEmpty()) return false;
        final BlockPos at = BlockPos.containing(stand.get());
        //? if >=26.1 {
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), at, player.getYRot(), 0F), true), false);
        //?} else {
        /*player.setRespawnPosition(level.dimension(), at, player.getYRot(), true, false);
        *///?}
        return true;
    }

    public static Optional<Vec3> standUp(ServerLevel level, BlockPos head, BlockState headState, float yaw) {
        //? if >=26.2 {
        /*return BedBlock.findStandUpPosition(net.minecraft.world.entity.EntityTypes.PLAYER, level, head, headState.getValue(FACING), yaw);
        *///?} else {
        return BedBlock.findStandUpPosition(EntityType.PLAYER, level, head, headState.getValue(FACING), yaw);
        //?}
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
