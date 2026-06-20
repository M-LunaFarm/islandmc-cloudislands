package kr.lunaf.cloudislands.paper.command;

import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.admin.AdminCommandController;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.gui.GuiActionExecutor;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.command.PluginCommand;

public final class PaperCommandRegistrar {
    private final CloudIslandsPaperPlugin plugin;

    public PaperCommandRegistrar(CloudIslandsPaperPlugin plugin) {
        this.plugin = plugin;
    }

    public GuiActionExecutor register(
        CloudIslandsPaperAgent agent,
        CoreApiClient client,
        String nodeId,
        int routeWaitSeconds,
        String fallbackServerName,
        EconomyBridge economyBridge,
        MessageRenderer messages,
        LocalCacheManager localCaches,
        Supplier<ActiveIslandRegistry> activeIslands
    ) {
        PluginCommand admin = plugin.getCommand("ciadmin");
        if (admin != null) {
            AdminCommandController adminController = new AdminCommandController(agent, client, nodeId, routeWaitSeconds, localCaches, messages);
            admin.setExecutor(adminController);
            admin.setTabCompleter(adminController);
        }
        PluginCommand island = plugin.getCommand("island");
        if (island != null) {
            IslandLevelScanService levelScanService = new IslandLevelScanService(plugin, activeIslands, client);
            IslandCommandController islandController = new IslandCommandController(plugin, client, agent.protection(), routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages);
            island.setExecutor(islandController);
            island.setTabCompleter(islandController);
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, islandController);
            return islandController;
        }
        return GuiActionExecutor.noop();
    }
}
