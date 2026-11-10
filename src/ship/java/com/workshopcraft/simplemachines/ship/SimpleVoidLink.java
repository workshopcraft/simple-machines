package com.workshopcraft.simplemachines.ship;

import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

/** Reads Simple Void area states without a compile-time dependency. */
final class SimpleVoidLink {
    private static Method getState;
    private static boolean looked;

    private SimpleVoidLink() {}

    static String state(ServerLevel level, String area) {
        if (!looked) {
            looked = true;
            try {
                getState = Class.forName("com.workshopcraft.simplevoid.api.VoidAreas").getMethod("getState", ServerLevel.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                getState = null;
            }
        }
        if (getState == null) return "";
        try {
            final Object s = getState.invoke(null, level, area);
            return s == null ? "" : s.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }
}
