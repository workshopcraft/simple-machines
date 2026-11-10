package com.workshopcraft.simplemachines.ship.block;

import com.workshopcraft.simplemachines.ship.ShipConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Small damage from broken ship parts, switched off with ship_hazard_damage=false. */
final class Hazards {
    private Hazards() {}

    static boolean on(Level level) {
        return level instanceof ServerLevel sl && ShipConfig.bool(sl.getServer(), ShipConfig.HAZARDS);
    }

    static void burn(Level level, Entity entity) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof LivingEntity living) || living.isSteppingCarefully() || !on(level)) return;
        if (living.tickCount % 10 == 0) living.hurtServer(sl, level.damageSources().hotFloor(), 1F);
    }
}
