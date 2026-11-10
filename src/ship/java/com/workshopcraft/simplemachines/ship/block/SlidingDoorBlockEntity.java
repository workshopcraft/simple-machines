package com.workshopcraft.simplemachines.ship.block;

import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.ShipApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Slide animation on the client, plus the lock of a locked door: it opens while area key has state unlockState. */
public class SlidingDoorBlockEntity extends BlockEntity {
    public static final float STEP = 0.1F;
    private float progress = -1, last;
    private String lockKey = "";
    private String unlockState = "repaired";

    public SlidingDoorBlockEntity(BlockPos pos, BlockState state) {
        super(Ship.SLIDING_DOOR_ENTITY, pos, state);
    }

    void clientTick() {
        final boolean open = getBlockState().hasProperty(SlideBlock.OPEN) && getBlockState().getValue(SlideBlock.OPEN);
        if (progress < 0) progress = last = open ? 1 : 0;
        last = progress;
        progress = open ? Math.min(1, progress + STEP) : Math.max(0, progress - STEP);
    }

    /** 0 closed, 1 open, smoothed between ticks. */
    public float progress(float partialTick) {
        if (progress < 0) return getBlockState().getValue(SlideBlock.OPEN) ? 1 : 0;
        return last + (progress - last) * partialTick;
    }

    public String lockKey() {
        return lockKey;
    }

    public String unlockState() {
        return unlockState;
    }

    public void setLock(String key, String state) {
        lockKey = key == null ? "" : key;
        unlockState = state == null || state.isEmpty() ? "repaired" : state;
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    boolean unlocked(ServerLevel level) {
        return lockKey.isEmpty() || ShipApi.getAreaState(level, lockKey).equals(unlockState);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lockKey = input.getStringOr("lock_key", "");
        unlockState = input.getStringOr("unlock_state", "repaired");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!lockKey.isEmpty()) {
            output.putString("lock_key", lockKey);
            output.putString("unlock_state", unlockState);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("lock_key", lockKey);
        tag.putString("unlock_state", unlockState);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
