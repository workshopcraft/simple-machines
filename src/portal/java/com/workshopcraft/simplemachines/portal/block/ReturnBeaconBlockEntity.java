package com.workshopcraft.simplemachines.portal.block;

import com.workshopcraft.simplemachines.portal.Portal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Remembers which gate the beacon leads back to. */
public class ReturnBeaconBlockEntity extends BlockEntity {
    private @Nullable GlobalPos gate;

    public ReturnBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(Portal.BEACON_ENTITY, pos, state);
    }

    public @Nullable GlobalPos gate() {
        return gate;
    }

    public void setGate(GlobalPos gate) {
        this.gate = gate;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        gate = input.read("gate", GlobalPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (gate != null) output.store("gate", GlobalPos.CODEC, gate);
    }
}
