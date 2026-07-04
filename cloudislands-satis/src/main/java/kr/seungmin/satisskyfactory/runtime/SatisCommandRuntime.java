package kr.seungmin.satisskyfactory.runtime;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SatisCommandRuntime {
    private final JavaPlugin plugin;

    public SatisCommandRuntime(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public CommandRegistrationResult bindPluginCommand(
            String commandName,
            CommandExecutor executor,
            TabCompleter tabCompleter
    ) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            return CommandRegistrationResult.missing(commandName);
        }
        ensureCommandRegistered(command);
        command.setExecutor(executor);
        command.setTabCompleter(tabCompleter);
        return CommandRegistrationResult.present(commandName, command.isRegistered());
    }

    public void unregisterPluginCommand(String commandName) {
        unregisterPluginCommand(plugin.getCommand(commandName));
    }

    private void ensureCommandRegistered(PluginCommand command) {
        if (command == null || command.isRegistered()) {
            return;
        }
        commandMap().ifPresent(map -> map.register(plugin.getDescription().getName().toLowerCase(Locale.ROOT), command));
    }

    private void unregisterPluginCommand(PluginCommand command) {
        if (command == null) {
            return;
        }
        command.setExecutor((_sender, _command, _label, _args) -> true);
        command.setTabCompleter((_sender, _command, _alias, _args) -> java.util.List.of());
        if (command.isRegistered()) {
            commandMap().ifPresent(command::unregister);
        }
    }

    private Optional<CommandMap> commandMap() {
        try {
            Object value = plugin.getServer().getClass().getMethod("getCommandMap").invoke(plugin.getServer());
            if (value instanceof CommandMap map) {
                return Optional.of(map);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Failed to access Bukkit command map for Satis command exposure: " + exception.getMessage());
        }
        return Optional.empty();
    }

    public record CommandRegistrationResult(String commandName, boolean present, boolean registered) {
        public static CommandRegistrationResult missing(String commandName) {
            return new CommandRegistrationResult(commandName, false, false);
        }

        public static CommandRegistrationResult present(String commandName, boolean registered) {
            return new CommandRegistrationResult(commandName, true, registered);
        }
    }
}
