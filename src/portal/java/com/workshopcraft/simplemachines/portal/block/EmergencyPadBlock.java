package com.workshopcraft.simplemachines.portal.block;

import com.workshopcraft.simplemachines.portal.EmergencyReturn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
//? if >=26.1
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cobbled, one-shot cousin of the Entangled Pad. No code and no power: jump on it or
 * right-click it to go back to the gate you arrived from (or spawn if that gate is gone).
 * After it fires it is left cracked and dead.
 */
public class EmergencyPadBlock extends Block {
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);
    private static final double SURFACE = 4.0 / 16.0;
    /** Last time each player was standing on a given pad, so a jump (not just walking on) fires it. */
    private static final Map<GlobalPos, Map<UUID, Long>> STANDING = new ConcurrentHashMap<>();

    public EmergencyPadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SPENT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPENT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (state.getValue(SPENT)) {
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.simplemachines.portal.emergency_pad_spent"));
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) tryUse(sl, pos, state, sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    //? if >=26.1 {
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
    //?} else {
    /*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    *///?}
        if (state.getValue(SPENT) || level.isClientSide()) return;
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SPENT)) {
            STANDING.remove(key(level, pos));
            return;
        }
        if (scanJump(level, pos, state)) level.scheduleTick(pos, this, 1);
    }

    private static GlobalPos key(ServerLevel level, BlockPos pos) {
        return GlobalPos.of(level.dimension(), pos);
    }

    /** @return true if someone is still on or just above the pad, so we should keep ticking */
    private static boolean scanJump(ServerLevel level, BlockPos pos, BlockState state) {
        final long now = level.getGameTime();
        final GlobalPos key = key(level, pos);
        final Map<UUID, Long> standing = STANDING.computeIfAbsent(key, k -> new HashMap<>());
        final AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        boolean busy = false;
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            if (!(p instanceof ServerPlayer sp) || p.isPassenger() || p.isSpectator()) continue;
            if (Math.floor(p.getX()) != pos.getX() || Math.floor(p.getZ()) != pos.getZ()) continue;
            final double feet = p.getY() - (pos.getY() + SURFACE);
            if (p.onGround() && Math.abs(feet) < 0.02) {
                standing.put(p.getUUID(), now);
                busy = true;
            } else if (!p.onGround() && feet > 0.02 && now - standing.getOrDefault(p.getUUID(), -100L) <= 4) {
                standing.remove(p.getUUID());
                tryUse(level, pos, state, sp);
                return false;
            } else if (Math.abs(feet) < 1.5) {
                busy = true;
            }
        }
        if (!standing.isEmpty()) {
            for (Iterator<Map.Entry<UUID, Long>> it = standing.entrySet().iterator(); it.hasNext(); ) {
                if (now - it.next().getValue() > 40) it.remove();
            }
            if (!standing.isEmpty()) busy = true;
        }
        if (standing.isEmpty()) STANDING.remove(key);
        return busy;
    }

    private static void tryUse(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (state.getValue(SPENT) || !level.getBlockState(pos).is(state.getBlock())) return;
        if (!EmergencyReturn.padEnabled(level)) {
            player.sendOverlayMessage(Component.translatable("message.simplemachines.portal.emergency_pad_disabled"));
            return;
        }
        level.setBlock(pos, state.setValue(SPENT, true), 3);
        STANDING.remove(key(level, pos));
        EmergencyReturn.fxPadSpent(level, pos);
        EmergencyReturn.finish(player);
    }
}
