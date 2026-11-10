package com.workshopcraft.simplemachines.portal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.workshopcraft.simplemachines.portal.block.GateControllerBlockEntity;
import com.workshopcraft.simplemachines.portal.world.PortalWorlds;
import com.workshopcraft.simplemachines.portal.world.WorldPreset;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

/**
 * /simplemachines portal ..., for pack makers, Simple Books command rewards and testing. Needs permission level 2.
 * Gates are named by position or by "nearest" (the closest controller within 64 blocks of the command source).
 */
public final class PortalCommands {
    private PortalCommands() {}

    private static final SuggestionProvider<CommandSourceStack> CARDS = (c, b) ->
            SharedSuggestionProvider.suggestResource(PortalResources.cards(c.getSource().getServer()).keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> PRESETS = (c, b) ->
            SharedSuggestionProvider.suggestResource(PortalResources.presets(c.getSource().getServer()).keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> WORLDS = (c, b) ->
            SharedSuggestionProvider.suggest(PortalData.get(c.getSource().getServer()).worlds().stream().map(w -> w.id), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        final LiteralArgumentBuilder<CommandSourceStack> portal = Commands.literal("portal");
        portal.then(Commands.literal("world")
                .then(Commands.literal("list").executes(PortalCommands::worldList))
                .then(Commands.literal("create").then(Commands.argument("preset", IdentifierArgument.id()).suggests(PRESETS)
                        .executes(PortalCommands::worldCreate)))
                .then(Commands.literal("tp").then(Commands.argument("id", StringArgumentType.word()).suggests(WORLDS)
                        .executes(PortalCommands::worldTp)))
                .then(Commands.literal("unload").then(Commands.argument("id", StringArgumentType.word()).suggests(WORLDS)
                        .executes(c -> worldUnload(c, false))))
                .then(Commands.literal("delete").then(Commands.argument("id", StringArgumentType.word()).suggests(WORLDS)
                        .executes(c -> worldUnload(c, true))))
                .then(Commands.literal("load").then(Commands.argument("id", StringArgumentType.word()).suggests(WORLDS)
                        .executes(PortalCommands::worldLoad))));
        portal.then(gate("applycard", (g, b) -> b
                .then(Commands.literal("clear").executes(c -> applyCard(c, g, null)))
                .then(Commands.argument("card", IdentifierArgument.id()).suggests(CARDS)
                        .executes(c -> applyCard(c, g, IdentifierArgument.getId(c, "card"))))));
        portal.then(gate("system", (g, b) -> b
                .then(Commands.literal("info").executes(c -> systemInfo(c, g)))
                .then(Commands.literal("depart").executes(c -> systemDepart(c, g, false))
                        .then(Commands.literal("force").executes(c -> systemDepart(c, g, true))))
                .then(Commands.literal("arrive").then(Commands.argument("id", StringArgumentType.word())
                        .executes(c -> systemArrive(c, g, StringArgumentType.getString(c, "id")))))));
        portal.then(gate("rift", (g, b) -> b
                .then(Commands.literal("hold").executes(c -> riftHold(c, g)))
                .then(Commands.literal("drop").executes(c -> riftDrop(c, g)))
                .then(Commands.literal("info").executes(c -> riftInfo(c, g)))));
        portal.then(gate("open", (g, b) -> b.executes(c -> open(c, g))));
        portal.then(gate("close", (g, b) -> b.executes(c -> close(c, g))));
        portal.then(gate("enable", (g, b) -> b.then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> enable(c, g)))));
        portal.then(gate("closedmode", (g, b) -> b.then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> closedMode(c, g)))));
        portal.then(gate("info", (g, b) -> b.executes(c -> info(c, g))));
        // research is per player: a player target (default @s), or everyone for the shared pool that counts for all
        portal.then(Commands.literal("unlock")
                .then(Commands.literal("all").executes(c -> unlockAll(c, self(c))))
                .then(Commands.literal("everyone")
                        .then(Commands.literal("all").executes(c -> unlockAll(c, null)))
                        .then(Commands.argument("key", StringArgumentType.greedyString()).executes(c -> unlock(c, null, true))))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("all").executes(c -> unlockAll(c, EntityArgument.getPlayers(c, "targets"))))
                        .then(Commands.argument("key", StringArgumentType.greedyString()).executes(c -> unlock(c, EntityArgument.getPlayers(c, "targets"), true))))
                .then(Commands.argument("key", StringArgumentType.greedyString()).executes(c -> unlock(c, self(c), true))));
        portal.then(Commands.literal("lock")
                .then(Commands.literal("everyone").then(Commands.argument("key", StringArgumentType.greedyString()).executes(c -> unlock(c, null, false))))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("key", StringArgumentType.greedyString()).executes(c -> unlock(c, EntityArgument.getPlayers(c, "targets"), false))))
                .then(Commands.argument("key", StringArgumentType.greedyString()).executes(c -> unlock(c, self(c), false))));
        portal.then(Commands.literal("unlocks").executes(c -> unlocks(c, self(c)))
                .then(Commands.literal("everyone").executes(c -> unlocks(c, null)))
                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> unlocks(c, EntityArgument.getPlayers(c, "targets")))));
        portal.then(Commands.literal("givecard").then(Commands.argument("card", IdentifierArgument.id()).suggests(CARDS)
                .executes(c -> giveCard(c, null))
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> giveCard(c, EntityArgument.getPlayer(c, "player"))))));
        portal.then(Commands.literal("cost").then(Commands.argument("card", IdentifierArgument.id()).suggests(CARDS).executes(PortalCommands::cost)));
        portal.then(Commands.literal("presets").executes(PortalCommands::presets));
        portal.then(Commands.literal("cards").executes(PortalCommands::cards));
        d.register(Commands.literal("simplemachines").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(portal));
    }

    /** Names a gate: "&lt;sub&gt; nearest ..." or "&lt;sub&gt; &lt;pos&gt; ...". */
    private interface GateRef {
        @Nullable BlockPos get(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gate(String name,
            BiFunction<GateRef, ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> rest) {
        final GateRef nearest = c -> nearest(c.getSource());
        final GateRef at = c -> BlockPosArgument.getLoadedBlockPos(c, "pos");
        return Commands.literal(name)
                .then(rest.apply(nearest, Commands.literal("nearest")))
                .then(rest.apply(at, Commands.argument("pos", BlockPosArgument.blockPos())));
    }

    private static @Nullable BlockPos nearest(CommandSourceStack src) {
        final ServerLevel level = src.getLevel();
        final BlockPos from = BlockPos.containing(src.getPosition());
        BlockPos best = null;
        double bestD = 64 * 64;
        final ChunkPos cp = ChunkPos.containing(from);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (!level.hasChunk(cp.x() + dx, cp.z() + dz)) continue;
                for (BlockEntity be : level.getChunk(cp.x() + dx, cp.z() + dz).getBlockEntities().values()) {
                    if (!(be instanceof GateControllerBlockEntity)) continue;
                    final double d = be.getBlockPos().distSqr(from);
                    if (d < bestD) {
                        bestD = d;
                        best = be.getBlockPos();
                    }
                }
            }
        }
        return best;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable(key, args), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }

    private static @Nullable GateControllerBlockEntity gateAt(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final BlockPos pos = ref.get(c);
        return pos == null ? null : PortalApi.gate(c.getSource().getLevel(), pos);
    }

    // --- worlds (spike and testing) ---

    private static int worldList(CommandContext<CommandSourceStack> c) {
        final MinecraftServer server = c.getSource().getServer();
        final List<PortalData.World> all = PortalData.get(server).worlds();
        if (all.isEmpty()) return ok(c, "commands.simplemachines.portal.no_worlds");
        for (PortalData.World w : all) {
            final boolean loaded = PortalWorlds.level(server, w.id) != null;
            c.getSource().sendSuccess(() -> Component.translatable("commands.simplemachines.portal.world_line", w.id, w.preset.toString(),
                    Long.toString(w.seed), loaded ? "loaded" : "not loaded", w.collected ? "collected" : "open"), false);
        }
        return all.size();
    }

    private static int worldCreate(CommandContext<CommandSourceStack> c) {
        final MinecraftServer server = c.getSource().getServer();
        final Identifier preset = IdentifierArgument.getId(c, "preset");
        if (!PortalResources.presets(server).containsKey(preset)) return fail(c, "commands.simplemachines.portal.no_preset", preset.toString());
        final PortalData data = PortalData.get(server);
        final ServerPlayer player = c.getSource().getPlayer();
        final GlobalPos back = player == null ? null : GlobalPos.of(player.level().dimension(), player.blockPosition());
        final PortalData.World w = new PortalData.World(data.newId(), preset, server.overworld().getRandom().nextLong(), null, List.of(), back);
        data.put(w);
        PortalWorlds.later(s -> {
            PortalWorlds.load(s, w);
            c.getSource().sendSuccess(() -> Component.translatable("commands.simplemachines.portal.world_made", w.id), true);
        });
        return ok(c, "commands.simplemachines.portal.world_queued", w.id);
    }

    private static int worldTp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        final MinecraftServer server = c.getSource().getServer();
        final String id = StringArgumentType.getString(c, "id");
        final ServerLevel level = PortalWorlds.level(server, id);
        if (level == null) return fail(c, "commands.simplemachines.portal.not_loaded", id);
        final ServerPlayer p = c.getSource().getPlayerOrException();
        final PortalData.World w = PortalData.get(server).world(id);
        final BlockPos spot = PortalTravel.landing(level, w, p.blockPosition(), (ServerLevel) p.level());
        p.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(spot), Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
        return ok(c, "commands.simplemachines.portal.tp", id);
    }

    private static int worldUnload(CommandContext<CommandSourceStack> c, boolean delete) {
        final String id = StringArgumentType.getString(c, "id");
        final PortalData.World w = PortalData.get(c.getSource().getServer()).world(id);
        if (w == null) return fail(c, "commands.simplemachines.portal.no_world", id);
        if (delete && PortalWorlds.occupiedOffline(c.getSource().getServer(), id)) {
            return fail(c, "commands.simplemachines.portal.delete_occupied", id);
        }
        PortalWorlds.later(s -> PortalWorlds.unload(s, id, delete, p -> PortalTravel.goBack(p, w.gate)));
        return ok(c, delete ? "commands.simplemachines.portal.deleting" : "commands.simplemachines.portal.unloading", id);
    }

    private static int worldLoad(CommandContext<CommandSourceStack> c) {
        final String id = StringArgumentType.getString(c, "id");
        final PortalData.World w = PortalData.get(c.getSource().getServer()).world(id);
        if (w == null) return fail(c, "commands.simplemachines.portal.no_world", id);
        PortalWorlds.later(s -> PortalWorlds.load(s, w));
        return ok(c, "commands.simplemachines.portal.loading", id);
    }

    // --- gates ---

    private static int applyCard(CommandContext<CommandSourceStack> c, GateRef ref, @Nullable Identifier card) throws CommandSyntaxException {
        final BlockPos pos = ref.get(c);
        if (pos == null || PortalApi.gate(c.getSource().getLevel(), pos) == null) return fail(c, "message.simplemachines.portal.no_gate");
        if (!PortalApi.setTarget(c.getSource().getLevel(), pos, card, List.of())) {
            return fail(c, "commands.simplemachines.portal.no_card", String.valueOf(card));
        }
        return ok(c, card == null ? "commands.simplemachines.portal.card_cleared" : "commands.simplemachines.portal.card_applied",
                String.valueOf(card), pos.toShortString());
    }

    private static int systemInfo(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final GateControllerBlockEntity g = gateAt(c, ref);
        if (g == null) return fail(c, "message.simplemachines.portal.no_gate");
        if (g.inTransit()) return ok(c, "commands.simplemachines.portal.system_transit", g.getBlockPos().toShortString());
        if (g.system() == null) return ok(c, "commands.simplemachines.portal.system_none", g.getBlockPos().toShortString());
        final StringBuilder planets = new StringBuilder();
        g.systemWorlds().forEach((card, world) -> planets.append(planets.isEmpty() ? "" : ", ").append(card).append(" = ").append(world));
        return ok(c, "commands.simplemachines.portal.system_info", g.system(), g.getBlockPos().toShortString(),
                planets.isEmpty() ? "-" : planets.toString());
    }

    private static int systemDepart(CommandContext<CommandSourceStack> c, GateRef ref, boolean force) throws CommandSyntaxException {
        final GateControllerBlockEntity g = gateAt(c, ref);
        if (g == null) return fail(c, "message.simplemachines.portal.no_gate");
        final String old = g.system();
        final String problem = g.depart(force);
        if (problem != null) return fail(c, problem, Integer.toString(PortalApi.playersAway(c.getSource().getLevel(), g.getBlockPos())));
        return ok(c, "commands.simplemachines.portal.system_departed", String.valueOf(old), g.getBlockPos().toShortString());
    }

    private static int systemArrive(CommandContext<CommandSourceStack> c, GateRef ref, String id) throws CommandSyntaxException {
        final GateControllerBlockEntity g = gateAt(c, ref);
        if (g == null) return fail(c, "message.simplemachines.portal.no_gate");
        g.arrive(id);
        return ok(c, "commands.simplemachines.portal.system_arrived", id, g.getBlockPos().toShortString());
    }

    private static int riftHold(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final BlockPos pos = ref.get(c);
        if (pos == null) return fail(c, "message.simplemachines.portal.no_gate");
        final String problem = PortalApi.holdRift(c.getSource().getLevel(), pos);
        if (problem != null) return fail(c, problem);
        return ok(c, "commands.simplemachines.portal.rift_held");
    }

    private static int riftDrop(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final BlockPos pos = ref.get(c);
        if (pos == null || PortalApi.gate(c.getSource().getLevel(), pos) == null) return fail(c, "message.simplemachines.portal.no_gate");
        if (!PortalApi.dropRift(c.getSource().getLevel(), pos)) return fail(c, "commands.simplemachines.portal.no_rift");
        return ok(c, "commands.simplemachines.portal.rift_dropped");
    }

    private static int riftInfo(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final BlockPos pos = ref.get(c);
        if (pos == null || PortalApi.gate(c.getSource().getLevel(), pos) == null) return fail(c, "message.simplemachines.portal.no_gate");
        final PortalData.Rift r = PortalRifts.of(c.getSource().getLevel(), pos);
        if (r == null) return fail(c, "commands.simplemachines.portal.no_rift");
        return ok(c, "commands.simplemachines.portal.rift_info", r.level.identifier().toString(), r.pos.toShortString(),
                Integer.toString(r.secondsLeft), Integer.toString(r.away.size()), Boolean.toString(r.low), Boolean.toString(r.held),
                Integer.toString(r.tier));
    }

    private static int open(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final GateControllerBlockEntity g = gateAt(c, ref);
        if (g == null) return fail(c, "message.simplemachines.portal.no_gate");
        final String problem = g.tryOpen(c.getSource().getPlayer());
        if (problem != null) return fail(c, problem);
        return ok(c, "commands.simplemachines.portal.opened");
    }

    private static int close(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final GateControllerBlockEntity g = gateAt(c, ref);
        if (g == null) return fail(c, "message.simplemachines.portal.no_gate");
        g.close(c.getSource().getLevel());
        return ok(c, "commands.simplemachines.portal.closed");
    }

    private static int enable(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final BlockPos pos = ref.get(c);
        final boolean on = BoolArgumentType.getBool(c, "on");
        if (pos == null || !PortalApi.setEnabled(c.getSource().getLevel(), pos, on)) return fail(c, "message.simplemachines.portal.no_gate");
        return ok(c, on ? "commands.simplemachines.portal.enabled" : "commands.simplemachines.portal.disabled");
    }

    private static int closedMode(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final BlockPos pos = ref.get(c);
        final boolean on = BoolArgumentType.getBool(c, "on");
        if (pos == null || !PortalApi.setClosedMode(c.getSource().getLevel(), pos, on)) return fail(c, "message.simplemachines.portal.no_gate");
        return ok(c, "commands.simplemachines.portal.closed_mode", Boolean.toString(on));
    }

    private static int info(CommandContext<CommandSourceStack> c, GateRef ref) throws CommandSyntaxException {
        final GateControllerBlockEntity g = gateAt(c, ref);
        if (g == null) return fail(c, "message.simplemachines.portal.no_gate");
        final GateFrame f = g.frame();
        final WorldPreset next = g.nextPreset(c.getSource().getLevel());
        return ok(c, "commands.simplemachines.portal.info", g.getBlockPos().toShortString(),
                f == null ? "none" : f.tier().id() + " " + f.width() + "x" + f.height(), String.valueOf(g.activeCard()),
                g.hiddenCardActive() ? "hidden" : "item", next == null ? "-" : next.id().toString(), String.valueOf(g.worldId()),
                Boolean.toString(g.enabled()), Boolean.toString(g.closedMode()), ownerName(c, g.owner()));
    }

    // --- research and cards ---

    private static java.util.Collection<ServerPlayer> self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return List.of(c.getSource().getPlayerOrException());
    }

    /** targets null: the shared pool, which counts for every player. */
    private static int unlock(CommandContext<CommandSourceStack> c, java.util.@Nullable Collection<ServerPlayer> targets, boolean on) {
        final String key = StringArgumentType.getString(c, "key").trim();
        final PortalData data = PortalData.get(c.getSource().getServer());
        if (targets == null) {
            if (on) data.unlockShared(key);
            else data.lockShared(key);
            return ok(c, on ? "commands.simplemachines.portal.unlocked_everyone" : "commands.simplemachines.portal.locked_everyone", key);
        }
        for (ServerPlayer p : targets) {
            if (on) data.unlock(p.getUUID(), key);
            else data.lock(p.getUUID(), key);
            if (!on && data.sharedUnlocks().contains(key)) fail(c, "commands.simplemachines.portal.still_shared", key);
        }
        return ok(c, on ? "commands.simplemachines.portal.unlocked" : "commands.simplemachines.portal.locked", key, names(targets));
    }

    private static int unlockAll(CommandContext<CommandSourceStack> c, java.util.@Nullable Collection<ServerPlayer> targets) {
        final MinecraftServer server = c.getSource().getServer();
        final PortalData data = PortalData.get(server);
        final List<String> keys = new java.util.ArrayList<>();
        for (Research r : PortalResources.research(server).values()) {
            keys.addAll(r.unlocks());
            keys.add("research:" + r.id());
        }
        for (WorldPreset p : PortalResources.presets(server).values()) if (p.needsResearch()) keys.add("preset:" + p.id());
        int n = 0;
        for (String k : keys) {
            if (targets == null) {
                if (data.unlockShared(k) && !k.startsWith("research:")) n++;
            } else {
                for (ServerPlayer p : targets) if (data.unlock(p.getUUID(), k) && !k.startsWith("research:")) n++;
            }
        }
        return ok(c, "commands.simplemachines.portal.unlocked_all", n, targets == null
                ? Component.translatable("commands.simplemachines.portal.everyone") : names(targets));
    }

    private static int unlocks(CommandContext<CommandSourceStack> c, java.util.@Nullable Collection<ServerPlayer> targets) {
        final PortalData data = PortalData.get(c.getSource().getServer());
        if (targets == null) {
            final var all = data.sharedUnlocks();
            c.getSource().sendSuccess(() -> Component.translatable("commands.simplemachines.portal.unlocks_everyone",
                    all.isEmpty() ? "-" : String.join(", ", all)), false);
            return all.size();
        }
        int n = 0;
        for (ServerPlayer p : targets) {
            final var own = data.ownUnlocks(p.getUUID());
            final var shared = data.sharedUnlocks();
            n += own.size();
            c.getSource().sendSuccess(() -> Component.translatable("commands.simplemachines.portal.unlocks_player", p.getDisplayName(),
                    own.isEmpty() ? "-" : String.join(", ", own), shared.isEmpty() ? "-" : String.join(", ", shared)), false);
        }
        return n;
    }

    private static Component ownerName(CommandContext<CommandSourceStack> c, java.util.@Nullable UUID id) {
        if (id == null) return Component.translatable("commands.simplemachines.portal.nobody");
        final ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayer(id);
        return p != null ? p.getDisplayName() : Component.literal(id.toString());
    }

    private static Component names(java.util.Collection<ServerPlayer> targets) {
        return targets.size() == 1 ? targets.iterator().next().getDisplayName() : Component.translatable("commands.simplemachines.portal.players", targets.size());
    }

    private static int giveCard(CommandContext<CommandSourceStack> c, @Nullable ServerPlayer target) throws CommandSyntaxException {
        final Identifier id = IdentifierArgument.getId(c, "card");
        final PortalCard card = PortalResources.cards(c.getSource().getServer()).get(id);
        if (card == null) return fail(c, "commands.simplemachines.portal.no_card", id.toString());
        final ServerPlayer p = target != null ? target : c.getSource().getPlayerOrException();
        p.getInventory().placeItemBackInInventory(Portal.cardStack(card));
        return ok(c, "commands.simplemachines.portal.gave_card", id.toString(), p.getDisplayName());
    }

    private static int cost(CommandContext<CommandSourceStack> c) {
        final Identifier id = IdentifierArgument.getId(c, "card");
        final PortalApi.JumpCost k = PortalApi.jumpCost(c.getSource().getServer(), id, 6);
        if (k == null) return fail(c, "commands.simplemachines.portal.no_card", id.toString());
        return ok(c, "commands.simplemachines.portal.cost", id.toString(), k.pellets(), k.energy());
    }

    private static int presets(CommandContext<CommandSourceStack> c) {
        final var all = PortalResources.presets(c.getSource().getServer()).values();
        final PortalData data = PortalData.get(c.getSource().getServer());
        final ServerPlayer who = c.getSource().getPlayer();
        for (WorldPreset p : all) {
            // locked for the player running it (the shared pool only from the console)
            final boolean locked = p.needsResearch() && !data.unlocked(who == null ? null : who.getUUID(), "preset:" + p.id());
            c.getSource().sendSuccess(() -> Component.literal(p.id() + "  tier " + p.tier() + ", " + p.minFrame().id() + " frame, richness "
                    + p.richness() + (locked ? ", locked" : "")), false);
        }
        return all.size();
    }

    private static int cards(CommandContext<CommandSourceStack> c) {
        final var all = PortalResources.cards(c.getSource().getServer()).values();
        for (PortalCard k : all) c.getSource().sendSuccess(() -> Component.literal(k.id() + "  " + k.kind().id()), false);
        return all.size();
    }

}
