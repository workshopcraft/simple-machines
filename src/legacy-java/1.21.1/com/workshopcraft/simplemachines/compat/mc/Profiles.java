package com.workshopcraft.simplemachines.compat.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Players for commands: online ones, a name from the server's profile cache, or a UUID. */
public final class Profiles implements ArgumentType<Profiles.Result> {
    private Profiles() {
    }

    public static Profiles argument() {
        return new Profiles();
    }

    public static Collection<Entry> get(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        return c.getArgument(name, Result.class).resolve(c.getSource());
    }

    public static Collection<Entry> of(ServerPlayer player) {
        return List.of(new Entry(player.getUUID(), player.getDisplayName()));
    }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        if (reader.canRead() && reader.peek() == '@') {
            return wrap(GameProfileArgument.gameProfile().parse(reader));
        }
        while (reader.canRead() && reader.peek() != ' ') reader.skip();
        final String token = reader.getString().substring(start, reader.getCursor());
        try {
            final UUID id = UUID.fromString(token);
            return src -> List.of(entry(src.getServer(), id, null));
        } catch (IllegalArgumentException ignored) {
            reader.setCursor(start);
            return wrap(GameProfileArgument.gameProfile().parse(reader));
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return GameProfileArgument.gameProfile().listSuggestions(context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return GameProfileArgument.gameProfile().getExamples();
    }

    private static Result wrap(GameProfileArgument.Result parsed) {
        return src -> {
            final List<Entry> out = new ArrayList<>();
            for (GameProfile p : parsed.getNames(src)) out.add(entry(src.getServer(), p.getId(), p.getName()));
            return out;
        };
    }

    private static Entry entry(MinecraftServer server, UUID id, String fallback) {
        final ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return new Entry(id, online.getDisplayName());
        String name = fallback;
        final GameProfileCache cache = server.getProfileCache();
        if ((name == null || name.isBlank()) && cache != null) {
            name = cache.get(id).map(GameProfile::getName).orElse(null);
        }
        return new Entry(id, Component.literal(name == null || name.isBlank() ? id.toString() : name));
    }

    @FunctionalInterface
    public interface Result {
        Collection<Entry> resolve(CommandSourceStack src) throws CommandSyntaxException;
    }

    public record Entry(UUID id, Component name) {
    }
}
