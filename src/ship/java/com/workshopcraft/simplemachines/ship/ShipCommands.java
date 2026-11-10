package com.workshopcraft.simplemachines.ship;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.workshopcraft.simplemachines.ship.block.SlidingDoorBlockEntity;
import com.workshopcraft.simplemachines.ship.block.TallBlock;
import com.workshopcraft.simplemachines.ship.block.TerminalBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/**
 * /simplemachines pod|startpod|area|lock|repair|damage|countdown|log, for pack makers and command hooks
 * (Simple Void area hooks, Simple Books quest rewards). Needs permission level 2.
 */
public final class ShipCommands {
    private ShipCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("simplemachines")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        root.then(Commands.literal("pod").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.literal("clear").executes(c -> pod(c, "")))
                .then(Commands.literal("auto").executes(c -> podAuto(c)))
                .then(Commands.argument("occupant", StringArgumentType.greedyString()).executes(c -> pod(c, StringArgumentType.getString(c, "occupant"))))));
        root.then(Commands.literal("startpod").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ShipCommands::startPod)));
        root.then(Commands.literal("area")
                .then(Commands.literal("list").executes(ShipCommands::areaList))
                .then(Commands.argument("key", StringArgumentType.string())
                        .executes(ShipCommands::areaGet)
                        .then(Commands.argument("state", StringArgumentType.string()).executes(ShipCommands::areaSet))));
        root.then(Commands.literal("lock").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.literal("clear").executes(c -> lock(c, "", "")))
                .then(Commands.argument("key", StringArgumentType.string())
                        .executes(c -> lock(c, StringArgumentType.getString(c, "key"), "repaired"))
                        .then(Commands.argument("state", StringArgumentType.string())
                                .executes(c -> lock(c, StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "state")))))));
        root.then(Commands.literal("repair").then(Commands.argument("from", BlockPosArgument.blockPos())
                .executes(c -> area(c, true, false)).then(Commands.argument("to", BlockPosArgument.blockPos()).executes(c -> area(c, true, true)))));
        root.then(Commands.literal("damage").then(Commands.argument("from", BlockPosArgument.blockPos())
                .executes(c -> area(c, false, false)).then(Commands.argument("to", BlockPosArgument.blockPos()).executes(c -> area(c, false, true)))));
        root.then(Commands.literal("countdown").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 5999)).executes(ShipCommands::countdown))));
        root.then(Commands.literal("log").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.literal("lock").then(Commands.argument("locked", BoolArgumentType.bool()).executes(ShipCommands::logLock)))
                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(ShipCommands::log))));
        d.register(root);
    }

    private static ServerLevel level(CommandContext<CommandSourceStack> c) {
        return c.getSource().getLevel();
    }

    private static int ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable(key, args), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }

    private static int pod(CommandContext<CommandSourceStack> c, String who) throws CommandSyntaxException {
        final BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!ShipApi.setPodOccupant(level(c), pos, who)) return fail(c, "commands.simplemachines.not_pod");
        return ok(c, "commands.simplemachines.pod", who.isEmpty() ? "-" : who);
    }

    private static int podAuto(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        final var pod = com.workshopcraft.simplemachines.ship.block.CryopodBlockEntity.at(level(c), BlockPosArgument.getLoadedBlockPos(c, "pos"));
        if (pod == null) return fail(c, "commands.simplemachines.not_pod");
        pod.setAuto(true);
        return ok(c, "commands.simplemachines.pod_auto");
    }

    private static int startPod(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!ShipApi.addStartPod(level(c), BlockPosArgument.getLoadedBlockPos(c, "pos"))) return fail(c, "commands.simplemachines.not_cryopod");
        return ok(c, "commands.simplemachines.startpod");
    }

    private static int areaList(CommandContext<CommandSourceStack> c) {
        final Map<String, String> all = ShipApi.areaStates(c.getSource().getServer());
        if (all.isEmpty()) return ok(c, "commands.simplemachines.area_none");
        all.forEach((k, v) -> c.getSource().sendSuccess(() -> Component.literal(k + " = " + v), false));
        return all.size();
    }

    private static int areaGet(CommandContext<CommandSourceStack> c) {
        final String key = StringArgumentType.getString(c, "key");
        final String state = ShipApi.getAreaState(level(c), key);
        return ok(c, "commands.simplemachines.area_get", key, state.isEmpty() ? "-" : state);
    }

    private static int areaSet(CommandContext<CommandSourceStack> c) {
        final String key = StringArgumentType.getString(c, "key");
        if (key.startsWith("void:")) return fail(c, "commands.simplemachines.area_void");
        final String state = StringArgumentType.getString(c, "state");
        ShipApi.setAreaState(c.getSource().getServer(), key, state);
        return ok(c, "commands.simplemachines.area_set", key, state);
    }

    private static int lock(CommandContext<CommandSourceStack> c, String key, String state) throws CommandSyntaxException {
        final BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        final ServerLevel level = level(c);
        if (!(level.getBlockEntity(TallBlock.base(level.getBlockState(pos), pos)) instanceof SlidingDoorBlockEntity door)) return fail(c, "commands.simplemachines.not_door");
        door.setLock(key, state);
        return key.isEmpty() ? ok(c, "commands.simplemachines.unlocked") : ok(c, "commands.simplemachines.locked", key, state);
    }

    private static int area(CommandContext<CommandSourceStack> c, boolean repair, boolean box) throws CommandSyntaxException {
        final BlockPos a = BlockPosArgument.getLoadedBlockPos(c, "from");
        final BlockPos b = box ? BlockPosArgument.getLoadedBlockPos(c, "to") : a;
        final int n = repair ? ShipApi.repairArea(level(c), a, b) : ShipApi.damageArea(level(c), a, b);
        return ok(c, repair ? "commands.simplemachines.repaired" : "commands.simplemachines.damaged", n);
    }

    private static int countdown(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        final int s = IntegerArgumentType.getInteger(c, "seconds");
        if (!ShipApi.setCountdown(level(c), BlockPosArgument.getLoadedBlockPos(c, "pos"), s)) return fail(c, "commands.simplemachines.not_countdown");
        return ok(c, "commands.simplemachines.countdown", s);
    }

    private static int log(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!(level(c).getBlockEntity(BlockPosArgument.getLoadedBlockPos(c, "pos")) instanceof TerminalBlockEntity t)) return fail(c, "commands.simplemachines.not_terminal");
        // "\n" in the command text starts a new line
        t.setLog(t.title(), StringArgumentType.getString(c, "text").replace("\\n", "\n"));
        return ok(c, "commands.simplemachines.log");
    }

    private static int logLock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!(level(c).getBlockEntity(BlockPosArgument.getLoadedBlockPos(c, "pos")) instanceof TerminalBlockEntity t)) return fail(c, "commands.simplemachines.not_terminal");
        t.setLocked(BoolArgumentType.getBool(c, "locked"));
        return ok(c, "commands.simplemachines.log_lock", BoolArgumentType.getBool(c, "locked"));
    }
}
