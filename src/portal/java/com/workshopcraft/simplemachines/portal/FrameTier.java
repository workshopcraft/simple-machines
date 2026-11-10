package com.workshopcraft.simplemachines.portal;

import java.util.Locale;

/** Gate frame material. Higher tiers reach harder worlds and are needed by their presets. Wood and other blocks never form a gate. */
public enum FrameTier {
    IRON(1), STEEL(2), TITANIUM(3), NETHERITE(4);

    public final int level;

    FrameTier(int level) {
        this.level = level;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean reaches(FrameTier needed) {
        return level >= needed.level;
    }

    public static FrameTier byName(String s) {
        for (FrameTier t : values()) if (t.id().equalsIgnoreCase(s)) return t;
        return IRON;
    }
}
