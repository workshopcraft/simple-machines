package com.workshopcraft.simplemachines.ship.block;

import com.workshopcraft.simplemachines.ship.Ship;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CountdownBlockEntity extends BlockEntity {
    public static final int DEFAULT_SECONDS = 30;
    /** Seconds left; -1 idle, -2 shows JUMP. */
    private int seconds = -1;
    private int ticks;

    public CountdownBlockEntity(BlockPos pos, BlockState state) {
        super(Ship.COUNTDOWN_ENTITY, pos, state);
    }

    public int seconds() {
        return seconds;
    }

    public void start(int s) {
        seconds = s <= 0 ? -1 : Math.min(s, 99 * 60 + 59);
        ticks = 0;
        changed();
    }

    void serverTick() {
        if (level == null) return;
        final BlockState state = getBlockState();
        if (seconds == -2) {
            // JUMP: one second of redstone signal
            if (state.getValue(CountdownBlock.POWERED) && ++ticks >= 20) {
                ticks = 0;
                level.setBlock(worldPosition, state.setValue(CountdownBlock.POWERED, false), 3);
            }
            return;
        }
        if (seconds <= 0) return;
        if (++ticks < 20) return;
        ticks = 0;
        seconds--;
        if (seconds <= 10 && seconds > 0) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.6F, 1.5F);
        if (seconds == 0) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1F, 0.8F);
            level.setBlock(worldPosition, state.setValue(CountdownBlock.POWERED, true), 3);
            seconds = -2;
        }
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        seconds = input.getIntOr("seconds", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("seconds", seconds);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("seconds", seconds);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
