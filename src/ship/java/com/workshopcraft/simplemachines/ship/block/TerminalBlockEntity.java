package com.workshopcraft.simplemachines.ship.block;

import com.workshopcraft.simplemachines.ship.Ship;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TerminalBlockEntity extends BlockEntity {
    public static final int MAX = 8192;
    private String title = "";
    private String text = "";
    private boolean locked;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(Ship.TERMINAL_ENTITY, pos, state);
    }

    public String title() {
        return title;
    }

    public String text() {
        return text;
    }

    public boolean locked() {
        return locked;
    }

    public void setLog(String title, String text) {
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text.length() > MAX ? text.substring(0, MAX) : text;
        changed();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        title = input.getStringOr("title", "");
        text = input.getStringOr("text", "");
        locked = input.getBooleanOr("locked", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("title", title);
        output.putString("text", text);
        output.putBoolean("locked", locked);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("title", title);
        tag.putString("text", text);
        tag.putBoolean("locked", locked);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
