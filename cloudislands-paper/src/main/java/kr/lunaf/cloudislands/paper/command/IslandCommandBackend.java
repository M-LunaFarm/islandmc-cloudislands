package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.application.MemberManagementUseCase;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import kr.lunaf.cloudislands.paper.platform.player.BukkitPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.player.PaperPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class IslandCommandBackend {
    static final List<String> SUBCOMMANDS = IslandCommandCatalog.SUBCOMMANDS;
    static final List<String> HELP_COMMANDS = IslandCommandCatalog.HELP_COMMANDS;
    private final IslandBankCommandHandler bankCommands;
    private final IslandSnapshotCommandHandler snapshotCommands;
    private final IslandWarehouseCommandHandler warehouseCommands;
    private final IslandChatLogCommandHandler chatLogCommands;
    private final IslandProgressionCommandHandler progressionCommands;
    private final IslandEnvironmentCommandHandler environmentCommands;
    private final IslandSettingsCommandHandler settingsCommands;
    private final IslandHomeWarpCommandHandler homeWarpCommands;
    private final IslandVisitReviewCommandHandler visitReviewCommands;
    private final IslandLifecycleCommandHandler lifecycleCommands;
    private final IslandOverviewCommandHandler overviewCommands;
    private final IslandPermissionCommandHandler permissionCommands;
    private final IslandMembershipCommandHandler membershipCommands;
    private final IslandAdminNodeCommandHandler adminCommands;
    private final IslandRoutingCommandHandler routingCommands;
    private final IslandCommandRouter router;
    private final IslandCommandMessenger commandMessages;
    private final IslandCommandIslandContext islandContext;
    private final IslandCommandMemberPresentation memberPresentation;
    private final IslandCommandRuntimeServices runtimeServices;
    private final MemberManagementUseCase memberManagement;

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection) {
        this(plugin, coreApiClient, protection, 20);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, "Lobby");
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, locales, new BukkitPlayerGateway(), new BukkitWorldGateway(plugin));
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PaperPlayerGateway players, PaperWorldGateway worlds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, null, players, worlds);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales, PaperPlayerGateway players, PaperWorldGateway worlds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, locales, players, worlds, "island-1");
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales, PaperPlayerGateway players, PaperWorldGateway worlds, String configuredNodeId) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, locales, players, worlds, configuredNodeId, "default");
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales, PaperPlayerGateway players, PaperWorldGateway worlds, String configuredNodeId, String defaultGeneratorKey) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, locales, players, worlds, configuredNodeId, defaultGeneratorKey, true);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales, PaperPlayerGateway players, PaperWorldGateway worlds, String configuredNodeId, String defaultGeneratorKey, boolean guiMenusEnabled) {
        this.commandMessages = new IslandCommandMessenger(plugin, messages, locales);
        this.islandContext = new IslandCommandIslandContext(protection);
        this.memberManagement = new MemberManagementUseCase(coreApiClient);
        IslandCommandPlayerResolver playerResolver = new IslandCommandPlayerResolver(plugin, memberManagement);
        IslandCommandLocalTeleports localTeleports = new IslandCommandLocalTeleports(plugin, protection, players, worlds, commandMessages);
        IslandCommandConfirmations confirmations = new IslandCommandConfirmations(commandMessages);
        this.runtimeServices = new IslandCommandRuntimeServices(commandMessages, islandContext, localTeleports, confirmations, playerResolver);
        this.routingCommands = new IslandRoutingCommandHandler(plugin, coreApiClient, routeWaitSeconds, fallbackServerName, runtimeServices);
        this.memberPresentation = new IslandCommandMemberPresentation(plugin, coreApiClient, protection, commandMessages, islandContext, routingCommands);
        this.bankCommands = new IslandBankCommandHandler(plugin, coreApiClient, economyBridge, runtimeServices);
        this.snapshotCommands = new IslandSnapshotCommandHandler(plugin, coreApiClient, runtimeServices);
        this.warehouseCommands = new IslandWarehouseCommandHandler(plugin, coreApiClient, runtimeServices);
        this.chatLogCommands = new IslandChatLogCommandHandler(plugin, coreApiClient, runtimeServices);
        this.progressionCommands = new IslandProgressionCommandHandler(plugin, coreApiClient, levelScanService, runtimeServices, defaultGeneratorKey);
        this.environmentCommands = new IslandEnvironmentCommandHandler(plugin, coreApiClient, protection, runtimeServices);
        this.settingsCommands = new IslandSettingsCommandHandler(plugin, coreApiClient, runtimeServices, locales);
        this.homeWarpCommands = new IslandHomeWarpCommandHandler(plugin, coreApiClient, new IslandHomeWarpRuntimeAdapter(runtimeServices, routingCommands));
        this.visitReviewCommands = new IslandVisitReviewCommandHandler(plugin, coreApiClient, new IslandVisitReviewRuntimeAdapter(runtimeServices, routingCommands));
        this.lifecycleCommands = new IslandLifecycleCommandHandler(plugin, coreApiClient, runtimeServices);
        this.overviewCommands = new IslandOverviewCommandHandler(plugin, coreApiClient, runtimeServices);
        this.permissionCommands = new IslandPermissionCommandHandler(plugin, coreApiClient, runtimeServices);
        this.membershipCommands = new IslandMembershipCommandHandler(
            plugin,
            coreApiClient,
            new IslandMembershipRuntimeAdapter(runtimeServices, memberPresentation, permissionCommands)
        );
        this.adminCommands = new IslandAdminNodeCommandHandler(plugin, coreApiClient, configuredNodeId, runtimeServices);
        this.router = IslandCommandRouterFactory.create(
            bankCommands,
            snapshotCommands,
            warehouseCommands,
            chatLogCommands,
            progressionCommands,
            environmentCommands,
            settingsCommands,
            homeWarpCommands,
            visitReviewCommands,
            lifecycleCommands,
            overviewCommands,
            membershipCommands,
            adminCommands,
            commandMessages,
            guiMenusEnabled
        );
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return router.handleCommand(sender, command, label, args);
    }

    void executeGuiAction(Player player, GuiAction action, GuiClick click) {
        router.handleGuiAction(player, action, click);
    }

    public void onQuit(PlayerQuitEvent event) {
        routingCommands.clearRouteLoading(event.getPlayer());
    }

    public void onKick(PlayerKickEvent event) {
        routingCommands.clearRouteLoading(event.getPlayer());
    }

}
