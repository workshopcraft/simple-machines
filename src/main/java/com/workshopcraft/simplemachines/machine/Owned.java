package com.workshopcraft.simplemachines.machine;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A block owned by the player who placed it, public or private like an entangled code. A screwdriver click by the owner
 * switches it; a private one only opens for its owner.
 */
public interface Owned {
    @Nullable UUID owner();

    boolean isPrivate();

    void setPrivate(boolean on);
}
