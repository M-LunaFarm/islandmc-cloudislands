package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommand;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommandContext;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommandResult;
import kr.lunaf.cloudislands.api.model.AddonIslandCommandSnapshot;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class AddonIslandCommandRegistry {
    private static final AddonIslandCommandRegistry GLOBAL = new AddonIslandCommandRegistry();
    private static final long EXECUTION_TIMEOUT_SECONDS = 10L;

    private final Map<String, RegisteredCommand> commandsByAlias = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> aliasesByAddon = new ConcurrentHashMap<>();
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    private volatile Plugin plugin;

    private AddonIslandCommandRegistry() {
    }

    public static AddonIslandCommandRegistry global() {
        return GLOBAL;
    }

    public void configure(Plugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        lifecycleGeneration.incrementAndGet();
    }

    public synchronized AddonIslandCommandSnapshot register(AddonIslandCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Addon island command is required");
        }
        String addonId = required(command.addonId(), "addonId");
        List<String> aliases = normalizeAliases(command.aliases());
        if (aliases.isEmpty()) {
            throw new IllegalArgumentException("Addon island command requires at least one alias");
        }
        if (command.minimumArguments() < 0 || command.maximumArguments() < command.minimumArguments()) {
            throw new IllegalArgumentException("Addon island command argument range is invalid");
        }
        for (String alias : aliases) {
            if (IslandCommandCatalog.SUBCOMMANDS.contains(alias) || SuperiorSkyblock2CommandAliasAdapter.playerAliases().contains(alias)) {
                throw new IllegalArgumentException("Addon island command alias collides with built-in command: " + alias);
            }
            RegisteredCommand current = commandsByAlias.get(alias);
            if (current != null && !current.addonId().equals(addonId)) {
                throw new IllegalArgumentException("Addon island command alias is already registered: " + alias);
            }
        }
        RegisteredCommand registered = new RegisteredCommand(addonId, command, aliases);
        aliases.forEach(alias -> commandsByAlias.put(alias, registered));
        aliasesByAddon.compute(addonId, (_id, current) -> {
            LinkedHashSet<String> combined = new LinkedHashSet<>(current == null ? Set.of() : current);
            combined.addAll(aliases);
            return Set.copyOf(combined);
        });
        return registered.snapshot();
    }

    public synchronized void unregisterAddon(String addonId) {
        String normalized = safe(addonId);
        Set<String> aliases = aliasesByAddon.remove(normalized);
        if (aliases != null) {
            aliases.forEach(alias -> commandsByAlias.computeIfPresent(alias, (_alias, command) -> command.addonId().equals(normalized) ? null : command));
        }
    }

    public boolean execute(Player player, String label, String[] args) {
        if (player == null || args == null || args.length == 0) {
            return false;
        }
        String alias = normalize(args[0]);
        RegisteredCommand registered = commandsByAlias.get(alias);
        if (registered == null) {
            return false;
        }
        Plugin configuredPlugin = plugin;
        if (configuredPlugin == null) {
            player.sendMessage("Addon island commands are unavailable while CloudIslands is stopping.");
            return true;
        }
        AddonIslandCommand command = registered.command();
        String permission = safe(command.permission());
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage("You do not have permission to use this addon island command.");
            return true;
        }
        List<String> arguments = args.length <= 1 ? List.of() : List.copyOf(Arrays.asList(args).subList(1, args.length));
        if (arguments.size() < command.minimumArguments() || arguments.size() > command.maximumArguments()) {
            String usage = safe(command.usage());
            player.sendMessage("Usage: /" + label + " " + registered.primaryAlias() + (usage.isBlank() ? "" : " " + usage));
            return true;
        }
        AddonIslandCommandContext context = new AddonIslandCommandContext(player.getUniqueId(), label, alias, arguments);
        CompletableFuture<AddonIslandCommandResult> execution;
        try {
            execution = command.execute(context);
        } catch (RuntimeException exception) {
            player.sendMessage("Addon island command failed: " + exception.getMessage());
            return true;
        }
        if (execution == null) {
            player.sendMessage("Addon island command failed: command returned no result");
            return true;
        }
        AddonCommandDeliveryTicket delivery = new AddonCommandDeliveryTicket(
            configuredPlugin,
            lifecycleGeneration.get(),
            player,
            player.getUniqueId()
        );
        execution.copy().orTimeout(EXECUTION_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS).whenComplete((result, error) -> deliver(delivery, activePlayer -> {
            if (error != null) {
                activePlayer.sendMessage("Addon island command failed: " + rootMessage(error));
                return;
            }
            AddonIslandCommandResult safeResult = result == null ? AddonIslandCommandResult.rejected("Addon island command returned no result") : result;
            safeResult.messages().forEach(activePlayer::sendMessage);
        }));
        return true;
    }

    private void deliver(AddonCommandDeliveryTicket ticket, java.util.function.Consumer<Player> action) {
        try {
            PaperSchedulers.run(ticket.expectedPlugin(), () -> {
                Player activePlayer = ticket.expectedPlugin().getServer().getPlayer(ticket.playerUuid());
                if (ticket.isCurrent(plugin, lifecycleGeneration.get(), activePlayer)) {
                    action.accept(activePlayer);
                }
            });
        } catch (RuntimeException ignored) {
            // Plugin disable can race completion; never fall back to Bukkit calls on the completion thread.
        }
    }

    public List<String> tabComplete(Player player, String label, String[] args) {
        if (player == null || args == null || args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            String typed = normalize(args[0]);
            return commandsByAlias.entrySet().stream()
                .filter(entry -> typed.isBlank() || entry.getKey().startsWith(typed))
                .filter(entry -> allowed(player, entry.getValue().command()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        }
        RegisteredCommand registered = commandsByAlias.get(normalize(args[0]));
        if (registered == null || !allowed(player, registered.command())) {
            return List.of();
        }
        List<String> arguments = List.copyOf(Arrays.asList(args).subList(1, args.length));
        try {
            CompletableFuture<List<String>> suggestions = registered.command().tabComplete(new AddonIslandCommandContext(player.getUniqueId(), label, args[0], arguments));
            if (suggestions == null || !suggestions.isDone() || suggestions.isCompletedExceptionally()) {
                return List.of();
            }
            List<String> values = suggestions.getNow(List.of());
            String typed = arguments.isEmpty() ? "" : arguments.get(arguments.size() - 1).toLowerCase(Locale.ROOT);
            return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).filter(value -> typed.isBlank() || value.toLowerCase(Locale.ROOT).startsWith(typed)).distinct().sorted().toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    public List<AddonIslandCommandSnapshot> snapshots() {
        return commandsByAlias.values().stream().distinct().map(RegisteredCommand::snapshot).sorted(Comparator.comparing(AddonIslandCommandSnapshot::addonId).thenComparing(AddonIslandCommandSnapshot::primaryAlias)).toList();
    }

    public java.util.Optional<AddonIslandCommandSnapshot> snapshotForAlias(String alias) {
        RegisteredCommand command = commandsByAlias.get(normalize(alias));
        return command == null ? java.util.Optional.empty() : java.util.Optional.of(command.snapshot());
    }

    public List<String> helpCommands() {
        return snapshots().stream().map(snapshot -> "섬 " + snapshot.primaryAlias() + (snapshot.usage().isBlank() ? "" : " " + snapshot.usage())).toList();
    }

    public synchronized void clear() {
        lifecycleGeneration.incrementAndGet();
        commandsByAlias.clear();
        aliasesByAddon.clear();
        plugin = null;
    }

    private static boolean allowed(Player player, AddonIslandCommand command) {
        String permission = safe(command.permission());
        return permission.isBlank() || player.hasPermission(permission);
    }

    private static List<String> normalizeAliases(List<String> aliases) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (aliases != null) {
            aliases.stream().map(AddonIslandCommandRegistry::normalize).filter(value -> !value.isBlank()).forEach(alias -> {
                if (alias.length() > 32 || !alias.matches("[a-z0-9][a-z0-9_-]*")) {
                    throw new IllegalArgumentException("Addon island command alias must match [a-z0-9][a-z0-9_-]* and be at most 32 characters: " + alias);
                }
                normalized.add(alias);
            });
        }
        return List.copyOf(normalized);
    }

    private static String required(String value, String field) {
        String normalized = safe(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record RegisteredCommand(String addonId, AddonIslandCommand command, List<String> aliases) {
        String primaryAlias() {
            return aliases.get(0);
        }

        AddonIslandCommandSnapshot snapshot() {
            return new AddonIslandCommandSnapshot(addonId, primaryAlias(), aliases, command.permission(), command.usage(), command.description());
        }
    }
}
