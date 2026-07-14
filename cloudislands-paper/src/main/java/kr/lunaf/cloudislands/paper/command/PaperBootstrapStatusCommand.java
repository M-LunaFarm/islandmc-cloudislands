package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.bootstrap.PaperBootstrapStatus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

public final class PaperBootstrapStatusCommand implements CommandExecutor, TabCompleter {
    private final CloudIslandsPaperPlugin plugin;
    private final PaperBootstrapStatus status;

    private PaperBootstrapStatusCommand(CloudIslandsPaperPlugin plugin, PaperBootstrapStatus status) {
        this.plugin = plugin;
        this.status = status;
    }

    public static void install(CloudIslandsPaperPlugin plugin, PaperBootstrapStatus status) {
        PaperBootstrapStatusCommand fallback = new PaperBootstrapStatusCommand(plugin, status);
        install(plugin, "island", fallback);
        install(plugin, "ciadmin", fallback);
    }

    private static void install(CloudIslandsPaperPlugin plugin, String name, PaperBootstrapStatusCommand fallback) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("CloudIslands bootstrap command is missing from plugin.yml: " + name);
            return;
        }
        command.setExecutor(fallback);
        command.setTabCompleter(fallback);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        PaperBootstrapStatus.Snapshot snapshot = status.snapshot();
        if (command.getName().equalsIgnoreCase("ciadmin") && args.length == 1 && args[0].equalsIgnoreCase("retry")) {
            if (!sender.hasPermission("cloudislands.admin.reload")) {
                sender.sendMessage("CloudIslands bootstrap retry denied: missing cloudislands.admin.reload");
                return true;
            }
            if (!snapshot.retryable()) {
                sender.sendMessage("CloudIslands bootstrap retry unavailable: state=" + snapshot.state());
                return true;
            }
            sender.sendMessage("CloudIslands bootstrap retry starting: nextAttempt=" + (snapshot.attempt() + 1));
            plugin.retryBootstrap();
            sendStatus(sender, status.snapshot(), true);
            return true;
        }
        sendStatus(sender, snapshot, command.getName().equalsIgnoreCase("ciadmin") && sender.hasPermission("cloudislands.admin.status"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("ciadmin")
            && args.length == 1
            && status.snapshot().retryable()
            && sender.hasPermission("cloudislands.admin.reload")
            && "retry".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
            return List.of("retry");
        }
        return List.of();
    }

    private static void sendStatus(CommandSender sender, PaperBootstrapStatus.Snapshot snapshot, boolean includeFailure) {
        sender.sendMessage("CloudIslands bootstrap=" + snapshot.state() + " attempt=" + snapshot.attempt());
        if (snapshot.state() == PaperBootstrapStatus.State.STARTING) {
            sender.sendMessage("CloudIslands is still starting; retry this command shortly.");
        } else if (snapshot.state() == PaperBootstrapStatus.State.FAILED) {
            sender.sendMessage("CloudIslands gameplay is safely unavailable; use /ciadmin retry after correcting the startup problem.");
            if (includeFailure) {
                sender.sendMessage("CloudIslands bootstrap failure=" + snapshot.failureType() + ": " + snapshot.failureMessage());
            }
        } else if (snapshot.state() == PaperBootstrapStatus.State.STOPPED) {
            sender.sendMessage("CloudIslands runtime is stopped.");
        }
    }
}
