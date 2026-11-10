package com.workshopcraft.simplemachines.portal.client;

import com.workshopcraft.simplemachines.portal.RiftHudPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The rift countdown at the top of the screen. The server sends the time left once a second; between updates it counts
 * down here, and it hides itself when updates stop. config/simplemachines-client.properties: rift_hud, rift_hud_y.
 */
public final class RiftHud {
    private static int seconds = -1;
    private static boolean low;
    private static long at;
    private static boolean enabled = true;
    private static int top = 6;
    private static long checked = Long.MIN_VALUE;
    private static long modified = Long.MIN_VALUE;

    private RiftHud() {}

    public static void receive(RiftHudPayload p) {
        seconds = p.seconds();
        low = p.low();
        at = Util.getMillis();
    }

    public static void render(GuiGraphicsExtractor g) {
        if (seconds < 0) return;
        final long now = Util.getMillis();
        final long age = now - at;
        if (age > 3000) {
            seconds = -1;
            return;
        }
        readConfig(now);
        final Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.options.hideGui || mc.player == null) return;
        final int left = (int) Math.max(0, seconds - age / 1000);
        final Font font = mc.font;
        final String text = Component.translatable(low ? "hud.simplemachines.rift_low" : "hud.simplemachines.rift",
                GateScreen.clock(left)).getString();
        final int w = font.width(text) + 10;
        final int x = (g.guiWidth() - w) / 2;
        // below any boss bars: vanilla stacks them 19 pixels apart from the top
        final int bosses = bossBars(mc);
        final int top = bosses > 0 ? Math.max(RiftHud.top, Math.min(bosses * 19 + 3, g.guiHeight() / 3 + 3)) : RiftHud.top;
        final boolean flash = low && (now / 400) % 2 == 0;
        final int edge = low ? (flash ? 0xFFFF3030 : 0xFF7A1010) : 0xFF6A3FA8;
        g.fill(x - 1, top - 1, x + w + 1, top + 13, edge);
        g.fill(x, top, x + w, top + 12, low && flash ? 0xD0400000 : 0xC0101018);
        g.text(font, text, x + 5, top + 2, low ? (flash ? 0xFFFFFFFF : 0xFFFF4040) : 0xFFE0D0FF, true);
    }

    private static java.lang.reflect.@org.jspecify.annotations.Nullable Field bossEvents;
    private static boolean bossFailed;

    private static int bossBars(Minecraft mc) {
        if (bossFailed) return 0;
        try {
            if (bossEvents == null) {
                bossEvents = net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredField("events");
                bossEvents.setAccessible(true);
            }
            return ((java.util.Map<?, ?>) bossEvents.get(mc.gui.getBossOverlay())).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            bossFailed = true;
            return 0;
        }
    }

    private static void readConfig(long now) {
        if (now - checked < 5000) return;
        checked = now;
        final Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("simplemachines-client.properties");
        try {
            final long stamp = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
            if (stamp == modified) return;
            final Properties p = new Properties();
            if (stamp >= 0) {
                try (Reader r = Files.newBufferedReader(file)) {
                    p.load(r);
                }
            }
            if (!p.containsKey("rift_hud") || !p.containsKey("rift_hud_y")) {
                p.putIfAbsent("rift_hud", "true");
                p.putIfAbsent("rift_hud_y", "6");
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file)) {
                    p.store(w, "Simple Machines client settings");
                }
            }
            modified = Files.getLastModifiedTime(file).toMillis();
            enabled = !"false".equalsIgnoreCase(p.getProperty("rift_hud", "true").trim());
            try {
                top = Math.max(0, Integer.parseInt(p.getProperty("rift_hud_y", "6").trim()));
            } catch (NumberFormatException e) {
                top = 6;
            }
        } catch (IOException e) {
            // keep the last settings
        }
    }
}
