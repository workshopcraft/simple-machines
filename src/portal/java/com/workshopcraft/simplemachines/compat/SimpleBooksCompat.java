package com.workshopcraft.simplemachines.compat;

import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.api.Display;
import com.workshopcraft.simplebooks.api.RewardContext;
import com.workshopcraft.simplebooks.api.RewardProblems;
import com.workshopcraft.simplebooks.api.RewardSpec;
import com.workshopcraft.simplebooks.api.RewardType;
import com.workshopcraft.simplebooks.api.SimpleBooksApi;
import com.workshopcraft.simplebooks.api.SimpleBooksPlugin;
import com.workshopcraft.simplemachines.portal.PortalApi;
import com.workshopcraft.simplemachines.portal.PortalResources;

/**
 * Optional Simple Books plugin: the portal_research quest reward. Loaded by Simple Books through a Fabric
 * entrypoint or a Forge/NeoForge IMC plugin; this class is never touched when Simple Books is absent.
 */
public final class SimpleBooksCompat implements SimpleBooksPlugin {
    public static final String TYPE = "simplemachines:portal_research";
    private static final String DEFAULT_ICON = "simplemachines:jump_card";

    @Override
    public void register() {
        SimpleBooksApi.registerRewardType(TYPE, new RewardType() {
            @Override
            public RewardSpec parse(JsonObject json, RewardProblems problems) {
                final RewardSpec spec = RewardSpec.of(json);
                final String key = spec.string("key", "").trim();
                if (key.isEmpty()) {
                    problems.error("key", "missing");
                    return spec;
                }
                final var known = PortalResources.knownKeys();
                if (!known.isEmpty() && !known.contains(key)) problems.warn("key", "unknown research key");
                return spec;
            }

            @Override
            public Display display(RewardSpec spec) {
                String icon = spec.string("icon", "");
                if (icon.isBlank()) icon = DEFAULT_ICON;
                String label = spec.string("label", "");
                if (label.isBlank()) {
                    final String key = spec.string("key", "").trim();
                    label = "Portal research: " + (key.isEmpty() ? "unknown" : PortalResources.keyName(key));
                }
                return new Display(icon, label);
            }

            @Override
            public void grant(RewardContext ctx, RewardSpec spec) {
                final String key = spec.string("key", "").trim();
                if (!key.isEmpty()) PortalApi.unlock(ctx.server(), ctx.player().getUUID(), key);
            }
        });
    }
}
