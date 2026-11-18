package com.workshopcraft.simplemachines.compat;

import com.mojang.logging.LogUtils;
import com.workshopcraft.simplebooks.api.SimpleBooksPlugin;
import org.slf4j.Logger;

/**
 * Optional Simple Books plugin: the portal_research quest reward. Loaded by Simple Books through a Fabric
 * entrypoint or a Forge/NeoForge IMC plugin; this class is never touched when Simple Books is absent.
 * Reward types exist only in Simple Books 1.3.0+, so registration is deferred to SimpleBooksPortalReward
 * and guarded: an older Simple Books must not crash the game.
 */
public final class SimpleBooksCompat implements SimpleBooksPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void register() {
        try {
            SimpleBooksPortalReward.register();
        } catch (LinkageError e) {
            // NoSuchMethodError and NoClassDefFoundError are LinkageErrors; older Simple Books lacks the reward API
            LOGGER.warn("Simple Machines did not register the portal research quest reward because Simple Books is older than 1.3.0. The manual still works.");
        }
    }
}
