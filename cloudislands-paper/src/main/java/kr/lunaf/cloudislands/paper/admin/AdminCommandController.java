package kr.lunaf.cloudislands.paper.admin;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

public final class AdminCommandController implements CommandExecutor, TabCompleter {
    private final AdminCommandBackend backend;

    public AdminCommandController(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId) {
        this(agent, coreApiClient, nodeId, 20);
    }

    public AdminCommandController(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds) {
        this(agent, coreApiClient, nodeId, routeWaitSeconds, null);
    }

    public AdminCommandController(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds, LocalCacheManager localCaches) {
        this(agent, coreApiClient, nodeId, routeWaitSeconds, localCaches, null);
    }

    public AdminCommandController(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds, LocalCacheManager localCaches, MessageRenderer messages) {
        this(agent, coreApiClient, nodeId, routeWaitSeconds, localCaches, messages, true);
    }

    public AdminCommandController(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds, LocalCacheManager localCaches, MessageRenderer messages, boolean superiorSkyblock2MigrationEnabled) {
        this.backend = new AdminCommandBackend(agent, coreApiClient, nodeId, routeWaitSeconds, localCaches, messages, superiorSkyblock2MigrationEnabled);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return backend.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return backend.onTabComplete(sender, command, alias, args);
    }
}
