package com.workshopcraft.simplebooks.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Compile-only stub of the Simple Books 1.3.0 API. The real classes are used at runtime. */
public record RewardContext(MinecraftServer server, ServerPlayer player, String teamId, String teamName) {}
