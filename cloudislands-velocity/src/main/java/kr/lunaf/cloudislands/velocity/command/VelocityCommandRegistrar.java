package kr.lunaf.cloudislands.velocity.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;

public final class VelocityCommandRegistrar {
    private final VelocityMessages messages;

    public VelocityCommandRegistrar(VelocityMessages messages) {
        this.messages = messages;
    }

    public void register(
        CommandManager commands,
        List<String> commandAliases,
        Consumer<PlayerInvocation> playerExecutor,
        Function<String[], List<String>> playerSuggestions,
        BiPredicate<CommandSource, String[]> adminAccess,
        Consumer<PlayerInvocation> adminExecutor,
        Function<String[], List<String>> adminSuggestions
    ) {
        commands.register(commands.metaBuilder("섬").aliases(commandAliasArray(commandAliases)).build(), playerCommand(playerExecutor, playerSuggestions));
        commands.register(commands.metaBuilder("ciadmin").aliases("섬관리").build(), adminCommand(adminAccess, adminExecutor, adminSuggestions));
    }

    private SimpleCommand playerCommand(Consumer<PlayerInvocation> executor, Function<String[], List<String>> suggestions) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) {
                    invocation.source().sendMessage(messages.component("player-only"));
                    return;
                }
                if (!player.hasPermission("cloudislands.player")) {
                    player.sendMessage(messages.component("no-player-permission"));
                    return;
                }
                executor.accept(new PlayerInvocation(player, invocation.arguments()));
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return suggestions.apply(invocation.arguments());
            }
        };
    }

    private SimpleCommand adminCommand(BiPredicate<CommandSource, String[]> adminAccess, Consumer<PlayerInvocation> executor, Function<String[], List<String>> suggestions) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) {
                    invocation.source().sendMessage(messages.component("player-only"));
                    return;
                }
                if (!adminAccess.test(player, invocation.arguments())) {
                    player.sendMessage(messages.component("no-admin-permission"));
                    return;
                }
                executor.accept(new PlayerInvocation(player, invocation.arguments()));
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                if (!adminAccess.test(invocation.source(), invocation.arguments())) {
                    return List.of();
                }
                return suggestions.apply(invocation.arguments());
            }
        };
    }

    static String[] commandAliasArray(List<String> aliases) {
        List<String> result = new ArrayList<>();
        for (String alias : aliases) {
            String normalized = alias == null ? "" : alias.trim();
            if (normalized.isBlank() || normalized.equalsIgnoreCase("섬")) {
                continue;
            }
            if (result.stream().noneMatch(existing -> existing.equalsIgnoreCase(normalized))) {
                result.add(normalized);
            }
        }
        return result.toArray(String[]::new);
    }

    public record PlayerInvocation(Player player, String[] arguments) {
    }
}
