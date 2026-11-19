package com.workshopcraft.simplemachines.portal;

import com.workshopcraft.simplemachines.entangled.EntangledPadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Server-side portal and teleport effects. Particles and sounds play here (vanilla, cheap);
 * screen glitch, vignette and afterimages are sent to clients with {@link PortalFxPayload}.
 */
public final class PortalFx {
    /** Default "entangled" magenta, used when a pad has no code to derive a colour from. */
    public static final int ENTANGLED = 0xC060E0;
    private static final double RANGE = 64.0;
    /** Set on the client: whether this position is still in the opening burst. False on a dedicated server. */
    public static java.util.function.Predicate<Vec3> openingAt = p -> false;

    private PortalFx() {}

    public static void send(ServerLevel level, PortalFxPayload payload) {
        final Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        final double r2 = RANGE * RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) <= r2) PortalPlatform.sendFx.accept(p, payload);
        }
    }

    public static void sendTo(ServerPlayer player, PortalFxPayload payload) {
        PortalPlatform.sendFx.accept(player, payload);
    }

    public static int colorOf(String code) {
        if (code == null || code.isEmpty()) return ENTANGLED;
        // fixed saturation and value so every code is readable; hue comes from the code
        final int h = code.hashCode();
        final float hue = (h & 0xFFFF) / 65535.0F;
        return hsv(hue, 0.62F, 0.92F);
    }

    private static int hsv(float h, float s, float v) {
        final float c = v * s;
        final float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        final float m = v - c;
        final float r, g, b;
        final int i = (int) Math.floor(h * 6);
        switch (i) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return ((int) ((r + m) * 255) << 16) | ((int) ((g + m) * 255) << 8) | (int) ((b + m) * 255);
    }

    private static void dust(ServerLevel level, Vec3 pos, int color, int count, double spread, double speed) {
        //? if >=26.1 {
        level.sendParticles(new DustParticleOptions(color, 1.2F), pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
        //?} else {
        /*level.sendParticles(new DustParticleOptions(new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F), 1.2F), pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
        *///?}
    }

    public static void gateOpen(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.15F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.45F, 1.35F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 80, 0.6, 0.8, 0.6, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 16, 0.4, 0.6, 0.4, 0.02);
        send(level, PortalFxPayload.at(PortalFxPayload.GATE_OPEN, pos, ENTANGLED, 0));
    }

    public static void gateCollapse(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.1F, 0.7F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.8F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 24, 0.4, 0.6, 0.4, 0.03);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 28, 0.5, 0.7, 0.5, 0.2);
        send(level, PortalFxPayload.at(PortalFxPayload.GATE_COLLAPSE, pos, ENTANGLED, 0));
    }

    public static void gateLow(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5F, 1.4F);
        send(level, PortalFxPayload.at(PortalFxPayload.GATE_LOW, pos, ENTANGLED, 0));
    }

    public static void riftOpen(ServerLevel level, BlockPos at) {
        final Vec3 pos = Vec3.atCenterOf(at).add(0, 0.5, 0);
        // PortalRifts.open already plays the start-up burst; this just tells nearby clients
        send(level, PortalFxPayload.at(PortalFxPayload.RIFT_OPEN, pos, ENTANGLED, 0));
    }

    public static void riftCollapse(ServerLevel level, BlockPos at) {
        final Vec3 pos = Vec3.atCenterOf(at).add(0, 0.5, 0);
        send(level, PortalFxPayload.at(PortalFxPayload.RIFT_COLLAPSE, pos, ENTANGLED, 0));
    }

    public static void emergencyWindup(ServerLevel level, BlockPos pos, ServerPlayer player) {
        final Vec3 at = Vec3.atCenterOf(pos).add(0, 0.3, 0);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.9F, 0.7F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.4, 0.2, 0.4, 0.2);
        send(level, PortalFxPayload.at(PortalFxPayload.EMERGENCY_WINDUP, at, ENTANGLED, player.getId()));
        sendTo(player, PortalFxPayload.at(PortalFxPayload.EMERGENCY_WINDUP, at, ENTANGLED, player.getId()));
    }

    public static void emergencySparks(ServerLevel level, BlockPos pos, int stage) {
        final Vec3 at = Vec3.atCenterOf(pos).add(0, 0.3, 0);
        final int n = 8 + stage * 6;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, n, 0.45, 0.25, 0.45, 0.25);
        if (stage > 1) level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.3, 0.2, 0.3, 0.1);
        if (stage % 2 == 0) {
            level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 0.15F, 1.6F + stage * 0.1F);
        }
    }

    public static void emergencyPop(ServerLevel level, BlockPos pos) {
        final Vec3 at = Vec3.atCenterOf(pos).add(0, 0.4, 0);
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 0.45F, 1.4F);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 18, 0.3, 0.2, 0.3, 0.04);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 20, 0.25, 0.15, 0.25, 0.03);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 8, 0.2, 0.1, 0.2, 0.01);
        send(level, PortalFxPayload.at(PortalFxPayload.EMERGENCY_POP, at, ENTANGLED, 0));
    }

    public static void emergencyLaunch(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9F, 0.85F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 0.6F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 24, 0.4, 0.4, 0.4, 0.25);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.2, pos.z, 10, 0.25, 0.15, 0.25, 0.02);
        send(level, PortalFxPayload.at(PortalFxPayload.EMERGENCY_LAUNCH, pos, ENTANGLED, 0));
    }

    public static void emergencyArrive(ServerLevel level, ServerPlayer player) {
        final Vec3 pos = player.position();
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.35F, 1.5F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 0.55F);
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.8, pos.z, 16, 0.3, 0.5, 0.3, 0.04);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.4, pos.z, 14, 0.25, 0.3, 0.25, 0.02);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.8, pos.z, 20, 0.4, 0.6, 0.4, 0.2);
        final PortalFxPayload p = PortalFxPayload.at(PortalFxPayload.EMERGENCY_ARRIVE, pos, ENTANGLED, player.getId());
        send(level, p);
        sendTo(player, p);
    }

    public static void padJump(EntangledPadBlockEntity.Jump jump) {
        final int color = colorOf(jump.code());
        final Vec3 from = Vec3.atBottomCenterOf(jump.fromPos().above());
        final Vec3 to = Vec3.atBottomCenterOf(jump.toPos().above());
        burst(jump.from(), from, color);
        send(jump.from(), PortalFxPayload.at(PortalFxPayload.PAD_JUMP, from, color, jump.player().getId()));
        sendTo(jump.player(), PortalFxPayload.at(PortalFxPayload.PAD_JUMP, from, color, jump.player().getId()));
        burst(jump.to(), to, color);
        send(jump.to(), PortalFxPayload.at(PortalFxPayload.PAD_ARRIVE, to, color, jump.player().getId()));
        sendTo(jump.player(), PortalFxPayload.at(PortalFxPayload.PAD_ARRIVE, to, color, jump.player().getId()));
    }

    private static void burst(ServerLevel level, Vec3 pos, int color) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.7F, 1.4F);
        dust(level, pos.add(0, 0.4, 0), color, 18, 0.35, 0.01);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 0.4, pos.z, 10, 0.2, 0.3, 0.2, 0.04);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 6, 0.15, 0.2, 0.15, 0.01);
    }

    public static void rocketTrail(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.12, 0.12, 0.12, 0.01);
        if (level.getRandom().nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 2, 0.2, 0.2, 0.2, 0.05);
        }
    }
}
