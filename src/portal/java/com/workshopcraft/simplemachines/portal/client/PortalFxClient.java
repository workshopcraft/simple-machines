package com.workshopcraft.simplemachines.portal.client;

import com.workshopcraft.simplemachines.portal.PortalFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client effects for portal states and teleports. Screen glitch and vignette honour
 * config/simplemachines-client.properties {@code screen_effects=full|reduced|off}.
 * Particles and sounds always play. Afterimages are particle columns, not a second player mesh,
 * so the look is the same on every supported version.
 */
public final class PortalFxClient {
    /** full, reduced, or off. */
    private static String screen = "full";
    private static long checked = Long.MIN_VALUE;
    private static long modified = Long.MIN_VALUE;

    private static int glitchTicks;
    private static boolean messy;
    private static int vignetteTicks;
    private static int ringTicks;
    private static int entityId;
    private static int color = 0xC060E0;
    private static int openingTicks;
    private static double openX, openY, openZ;

    static {
        com.workshopcraft.simplemachines.portal.PortalFx.openingAt = v -> opening(v.x, v.y, v.z);
    }

    private PortalFxClient() {}

    public static void receive(PortalFxPayload p) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        color = p.color() == 0 ? 0xC060E0 : p.color();
        entityId = p.entityId();
        final Vec3 at = new Vec3(p.x(), p.y(), p.z());
        switch (p.kind()) {
            case PortalFxPayload.GATE_OPEN, PortalFxPayload.RIFT_OPEN -> {
                openingTicks = 25;
                openX = p.x();
                openY = p.y();
                openZ = p.z();
            }
            case PortalFxPayload.GATE_COLLAPSE, PortalFxPayload.RIFT_COLLAPSE -> {
                // particles already spawned by the server; a short local crackle is enough
            }
            case PortalFxPayload.GATE_LOW -> {
                if (mc.level != null) {
                    mc.level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.4F, 1.5F, false);
                }
            }
            case PortalFxPayload.EMERGENCY_WINDUP, PortalFxPayload.EMERGENCY_LAUNCH -> {
                glitchTicks = Math.max(glitchTicks, 24);
                messy = true;
            }
            case PortalFxPayload.EMERGENCY_POP -> {
                glitchTicks = Math.max(glitchTicks, 8);
                messy = true;
            }
            case PortalFxPayload.EMERGENCY_ARRIVE -> {
                glitchTicks = 16;
                messy = true;
                vignetteTicks = 40;
                ringTicks = 35;
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.BEACON_DEACTIVATE, 0.55F, 0.5F);
                }
            }
            case PortalFxPayload.PAD_JUMP, PortalFxPayload.PAD_ARRIVE -> {
                glitchTicks = 10;
                messy = false;
            }
            default -> {}
        }
        afterimage(mc.level, at, p.kind() == PortalFxPayload.PAD_JUMP || p.kind() == PortalFxPayload.PAD_ARRIVE ? 1 : 3);
    }

    /** True for the first second after a gate or rift opened, so animateTick can thicken the start-up look. */
    public static boolean opening(double x, double y, double z) {
        if (openingTicks <= 0) return false;
        final double dx = x - openX, dy = y - openY, dz = z - openZ;
        return dx * dx + dy * dy + dz * dz < 16;
    }

    public static void render(GuiGraphicsExtractor g) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        tickWorld(mc);
        readConfig();
        if ("off".equals(screen)) return;
        final int w = g.guiWidth();
        final int h = g.guiHeight();
        final boolean reduced = "reduced".equals(screen);
        if (vignetteTicks > 0 && messy) {
            final float t = Math.min(1.0F, vignetteTicks / 40.0F);
            final int alpha = (int) ((reduced ? 0x55 : 0x99) * t) << 24;
            final int edge = (int) ((reduced ? 40 : 80) * t);
            final int col = alpha | 0x12010A;
            g.fill(0, 0, w, edge, col);
            g.fill(0, h - edge, w, h, col);
            g.fill(0, edge, edge, h - edge, col);
            g.fill(w - edge, edge, w, h - edge, col);
            // a reddish wash in the middle, short and rough
            if (!reduced) g.fill(0, 0, w, h, ((int) (0x28 * t) << 24) | 0x2A0008);
        }
        if (glitchTicks > 0 && !reduced) {
            final RandomSource r = mc.level.getRandom();
            final int slices = messy ? 5 : 2;
            for (int i = 0; i < slices; i++) {
                final int y = r.nextInt(Math.max(1, h));
                final int tall = messy ? 2 + r.nextInt(8) : 2 + r.nextInt(4);
                final int shift = (messy ? 8 : 4) * (r.nextBoolean() ? 1 : -1);
                final int a = messy ? 0x55 : 0x33;
                final int c1 = (a << 24) | 0xFF3030;
                final int c2 = (a << 24) | 0x30E0FF;
                final int x0 = Math.max(0, shift);
                final int x1 = Math.min(w, w + shift);
                if (shift > 0) {
                    g.fill(0, y, shift, Math.min(h, y + tall), c1);
                    g.fill(shift, y, w, Math.min(h, y + tall), c2 & 0x22FFFFFF);
                } else {
                    g.fill(w + shift, y, w, Math.min(h, y + tall), c2);
                    g.fill(0, y, x1, Math.min(h, y + tall), c1 & 0x22FFFFFF);
                }
            }
        }
    }

    private static void tickWorld(Minecraft mc) {
        if (openingTicks > 0) openingTicks--;
        if (glitchTicks > 0) {
            glitchTicks--;
            if (mc.level != null && !"off".equals(screen) && !"reduced".equals(screen)) {
                Entity e = entityId != 0 ? mc.level.getEntity(entityId) : mc.player;
                if (e == null) e = mc.player;
                if (e != null) afterimage(mc.level, e.position().add(0, e.getBbHeight() * 0.5, 0), messy ? 2 : 1);
            }
        }
        if (vignetteTicks > 0) vignetteTicks--;
        if (ringTicks > 0) {
            ringTicks--;
            final LocalPlayer p = mc.player;
            if (p != null && ringTicks % 8 == 0 && !"off".equals(screen)) {
                p.playSound(SoundEvents.BEACON_AMBIENT, 0.18F, 1.8F);
            }
        }
    }

    private static void afterimage(Level level, Vec3 at, int copies) {
        if (level == null || "off".equals(screen) || "reduced".equals(screen)) return;
        final RandomSource r = level.getRandom();
        for (int i = 0; i < copies; i++) {
            final double ox = (r.nextDouble() - 0.5) * (messy ? 1.4 : 0.6);
            final double oz = (r.nextDouble() - 0.5) * (messy ? 1.4 : 0.6);
            for (int y = 0; y < 6; y++) {
                final double yy = at.y - 0.2 + y * 0.3;
                level.addParticle(messy ? ParticleTypes.WITCH : ParticleTypes.REVERSE_PORTAL,
                        at.x + ox, yy, at.z + oz, 0, 0.01, 0);
            }
            if (messy) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + ox, at.y + 0.8, at.z + oz,
                        (r.nextDouble() - 0.5) * 0.2, 0.05, (r.nextDouble() - 0.5) * 0.2);
            }
        }
    }

    private static void readConfig() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        final long now = System.currentTimeMillis();
        if (now - checked < 5000) return;
        checked = now;
        final Path file = mc.gameDirectory.toPath().resolve("config").resolve("simplemachines-client.properties");
        try {
            final long stamp = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
            if (stamp == modified && stamp >= 0) return;
            final Properties p = new Properties();
            if (stamp >= 0) {
                try (Reader r = Files.newBufferedReader(file)) {
                    p.load(r);
                }
            }
            if (!p.containsKey("screen_effects")) {
                p.putIfAbsent("screen_effects", "full");
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file)) {
                    p.store(w, "Simple Machines client settings");
                }
            }
            modified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : stamp;
            final String v = p.getProperty("screen_effects", "full").trim().toLowerCase();
            screen = switch (v) {
                case "off", "false", "none" -> "off";
                case "reduced", "low", "minimal" -> "reduced";
                default -> "full";
            };
        } catch (IOException e) {
            screen = "full";
        }
    }
}
