package com.workshopcraft.simplemachines.portal.entity;

import com.workshopcraft.simplemachines.portal.EmergencyReturn;
import com.workshopcraft.simplemachines.portal.Portal;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
//? if >=26.1 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}
import net.minecraft.world.phys.Vec3;
//? if >=1.21.1
import net.minecraft.network.syncher.SynchedEntityData;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A short, no-clip launch vehicle. After {@link #FLIGHT_TICKS} (or if the rider leaves or
 * disconnects mid-flight) the rider is sent home through {@link EmergencyReturn#finish}.
 */
public class EmergencyRocketEntity extends Entity implements ItemSupplier {
    public static final int FLIGHT_TICKS = 40;
    private static final double ASCENT = 0.55;

    private int flight;
    private @Nullable UUID rider;
    private boolean finished;

    public EmergencyRocketEntity(EntityType<? extends EmergencyRocketEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setRider(UUID rider) {
        this.rider = rider;
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Portal.emergencyRocket);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(new Vec3(0, ASCENT, 0));
        setPos(getX(), getY() + ASCENT, getZ());
        if (level().isClientSide()) return;
        flight++;
        final boolean noRider = getPassengers().isEmpty();
        if (flight >= FLIGHT_TICKS || (noRider && flight > 1)) complete();
    }

    /** Called when the launch ends, the rider dismounts, or they log out mid-flight. */
    public void complete() {
        if (finished) return;
        finished = true;
        if (level() instanceof ServerLevel sl) {
            ServerPlayer player = null;
            if (getFirstPassenger() instanceof ServerPlayer sp) player = sp;
            if (player == null && rider != null) player = sl.getServer().getPlayerList().getPlayer(rider);
            ejectPassengers();
            if (player != null) {
                EmergencyReturn.finish(player);
            } else if (rider != null) {
                EmergencyReturn.markPending(sl.getServer(), rider);
            }
        }
        discard();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && (rider == null || passenger.getUUID().equals(rider));
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        move.accept(passenger, getX(), getY() + 0.85, getZ());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    //? if >=26.1 {
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }
    //?} else {
    /*@Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
    *///?}

    //? if >=1.21.1 {
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }
    //?} else {
    /*@Override
    protected void defineSynchedData() {
    }
    *///?}

    //? if >=26.1 {
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Flight", flight);
        if (rider != null) output.store("Rider", UUIDUtil.CODEC, rider);
        if (finished) output.putBoolean("Finished", true);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        flight = input.getIntOr("Flight", 0);
        rider = input.read("Rider", UUIDUtil.CODEC).orElse(null);
        finished = input.getBooleanOr("Finished", false);
    }
    //?} else {
    /*@Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Flight", flight);
        if (rider != null) tag.putUUID("Rider", rider);
        if (finished) tag.putBoolean("Finished", true);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        flight = tag.getInt("Flight");
        if (tag.hasUUID("Rider")) rider = tag.getUUID("Rider");
        finished = tag.getBoolean("Finished");
    }
    *///?}
}
