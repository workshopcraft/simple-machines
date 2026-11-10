package com.workshopcraft.simplemachines.ship.block;

import com.workshopcraft.simplemachines.ship.Ship;
import com.workshopcraft.simplemachines.ship.ShipOccupants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Who sleeps in a decoration cryopod: a player name or UUID (skin looked up like a player head), "skin:<name>" for a
 * bundled skin in assets/simplemachines/textures/entity/cryopod/, or "" for an empty pod. With auto on and no occupant,
 * the first server tick takes the next name from the cryopod occupant list, so a placed ship fills its pods.
 */
public class CryopodBlockEntity extends BlockEntity {
    private String occupant = "";
    private boolean auto = true;

    public CryopodBlockEntity(BlockPos pos, BlockState state) {
        super(Ship.CRYOPOD_ENTITY, pos, state);
    }

    public static @Nullable CryopodBlockEntity at(Level level, BlockPos pos) {
        final BlockState s = level.getBlockState(pos);
        return level.getBlockEntity(TallBlock.base(s, pos)) instanceof CryopodBlockEntity c ? c : null;
    }

    public String occupant() {
        return occupant;
    }

    public boolean auto() {
        return auto;
    }

    void placedByHand() {
        auto = false;
        setChanged();
    }

    public void setOccupant(String who) {
        occupant = who == null ? "" : who.trim();
        auto = false;
        changed();
    }

    public void setAuto(boolean on) {
        auto = on;
        if (on) occupant = "";
        changed();
    }

    void serverTick() {
        if (!auto || !occupant.isEmpty() || !(level instanceof ServerLevel sl)) return;
        auto = false;
        occupant = ShipOccupants.next(sl.getServer());
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        occupant = input.getStringOr("occupant", "");
        auto = input.getBooleanOr("auto", true);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("occupant", occupant);
        output.putBoolean("auto", auto);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("occupant", occupant);
        tag.putBoolean("auto", auto);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
