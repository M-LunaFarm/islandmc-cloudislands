package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class IslandCommandController implements CommandExecutor, TabCompleter, Listener {
    private final IslandCommandBackend backend;

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection) {
        this(plugin, coreApiClient, protection, 20);
    }

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, "Lobby");
    }

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, null);
    }

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, null);
    }

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, null);
    }

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages) {
        this.backend = new IslandCommandBackend(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return backend.onTabComplete(sender, command, alias, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return backend.onCommand(sender, command, label, args);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        backend.onQuit(event);
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        backend.onKick(event);
    }
}
