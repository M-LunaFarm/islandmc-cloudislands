package kr.lunaf.cloudislands.paper.admin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.AdminAddonStateSummaryView;
import kr.lunaf.cloudislands.coreclient.AdminAuditEntryView;
import kr.lunaf.cloudislands.coreclient.AdminCoreConfigView;
import kr.lunaf.cloudislands.coreclient.AdminEventStreamView;
import kr.lunaf.cloudislands.coreclient.AdminEventView;
import kr.lunaf.cloudislands.coreclient.AdminIslandRuntimeView;
import kr.lunaf.cloudislands.coreclient.AdminMaintenanceResultView;
import kr.lunaf.cloudislands.coreclient.AdminMetricsSummaryView;
import kr.lunaf.cloudislands.coreclient.AdminNodeActionView;
import kr.lunaf.cloudislands.coreclient.AdminNodeSummaryView;
import kr.lunaf.cloudislands.coreclient.AdminRouteClearView;
import kr.lunaf.cloudislands.coreclient.AdminRouteDebugView;
import kr.lunaf.cloudislands.coreclient.AdminRouteSessionView;
import kr.lunaf.cloudislands.coreclient.AdminRouteTicketView;
import kr.lunaf.cloudislands.coreclient.AdminStorageStatusView;
import kr.lunaf.cloudislands.coreclient.BankMutationView;
import kr.lunaf.cloudislands.coreclient.BlockValueActionView;
import kr.lunaf.cloudislands.coreclient.BlockValueView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.EnvironmentActionView;
import kr.lunaf.cloudislands.coreclient.HomeWarpActionView;
import kr.lunaf.cloudislands.coreclient.IslandLifecycleActionView;
import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView;
import kr.lunaf.cloudislands.coreclient.JobActionView;
import kr.lunaf.cloudislands.coreclient.JobRecoveryView;
import kr.lunaf.cloudislands.coreclient.JobView;
import kr.lunaf.cloudislands.coreclient.MemberActionView;
import kr.lunaf.cloudislands.coreclient.PermissionActionView;
import kr.lunaf.cloudislands.coreclient.PlayerProfileView;
import kr.lunaf.cloudislands.coreclient.ProgressionRankingEntryView;
import kr.lunaf.cloudislands.coreclient.ProgressionUpgradePurchaseView;
import kr.lunaf.cloudislands.coreclient.ProgressionUpgradeRecalculationView;
import kr.lunaf.cloudislands.coreclient.ReviewActionView;
import kr.lunaf.cloudislands.coreclient.ReviewModerationView;
import kr.lunaf.cloudislands.coreclient.SettingsActionView;
import kr.lunaf.cloudislands.coreclient.TemplateView;
import kr.lunaf.cloudislands.coreclient.TemplateBundleVerificationView;
import kr.lunaf.cloudislands.coreclient.UpgradeRuleView;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;
import kr.lunaf.cloudislands.paper.AdminChatSpyRegistry;
import kr.lunaf.cloudislands.paper.AdminFlightOverrides;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfigReloadResult;
import kr.lunaf.cloudislands.paper.gui.AdminAuditMenu;
import kr.lunaf.cloudislands.paper.gui.AdminDashboardMenu;
import kr.lunaf.cloudislands.paper.gui.AdminEventMenu;
import kr.lunaf.cloudislands.paper.gui.AdminJobMenu;
import kr.lunaf.cloudislands.paper.gui.AdminMetricsMenu;
import kr.lunaf.cloudislands.paper.gui.AdminMigrationMenu;
import kr.lunaf.cloudislands.paper.gui.AdminNodeListMenu;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.gui.AdminRouteMenu;
import kr.lunaf.cloudislands.paper.gui.AdminReviewModerationMenu;
import kr.lunaf.cloudislands.paper.gui.AdminStorageMenu;
import kr.lunaf.cloudislands.paper.gui.AdminTemplateMenu;
import kr.lunaf.cloudislands.paper.gui.GuiSession;
import kr.lunaf.cloudislands.paper.gui.GuiSessions;
import kr.lunaf.cloudislands.paper.gui.GuiStateMenus;
import kr.lunaf.cloudislands.paper.gui.IslandChatMenu;
import kr.lunaf.cloudislands.paper.gui.IslandCreateMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMainMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMyIslandsMenu;
import kr.lunaf.cloudislands.paper.gui.IslandVisitMenu;
import kr.lunaf.cloudislands.paper.integration.IntegrationRuntimeCertification;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import kr.lunaf.cloudislands.paper.platform.world.AdminWorldSpawnGateway;
import kr.lunaf.cloudislands.paper.platform.world.AdminWorldSpawnGateway.SpawnUpdateResult;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class AdminCommandBackend implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = AdminCommandCatalog.ROOT_COMMANDS;
    private static final List<String> SETUP_COMMANDS = AdminCommandCatalog.SETUP_COMMANDS;
    private static final List<String> CONFIG_COMMANDS = AdminCommandCatalog.CONFIG_COMMANDS;
    private static final List<String> CACHE_COMMANDS = AdminCommandCatalog.CACHE_COMMANDS;
    private static final List<String> ADDON_COMMANDS = AdminCommandCatalog.ADDON_COMMANDS;
    private static final List<String> ADDON_FEATURES = AdminCommandCatalog.ADDON_FEATURES;
    private static final List<String> NODE_COMMANDS = AdminCommandCatalog.NODE_COMMANDS;
    private static final List<String> ISLAND_COMMANDS = AdminCommandCatalog.ISLAND_COMMANDS;
    private static final List<String> PLAYER_COMMANDS = AdminCommandCatalog.PLAYER_COMMANDS;
    private static final List<String> JOB_COMMANDS = AdminCommandCatalog.JOB_COMMANDS;
    private static final List<String> ROUTE_COMMANDS = AdminCommandCatalog.ROUTE_COMMANDS;
    private static final List<String> STORAGE_COMMANDS = AdminCommandCatalog.STORAGE_COMMANDS;
    private static final List<String> DIAGNOSTICS_COMMANDS = AdminCommandCatalog.DIAGNOSTICS_COMMANDS;
    private static final List<String> SUPPORT_BUNDLE_COMMANDS = AdminCommandCatalog.SUPPORT_BUNDLE_COMMANDS;
    private static final List<String> RANKING_COMMANDS = AdminCommandCatalog.RANKING_COMMANDS;
    private static final List<String> BLOCK_VALUE_COMMANDS = AdminCommandCatalog.BLOCK_VALUE_COMMANDS;
    private static final List<String> BLOCK_VALUE_MATERIALS = AdminCommandCatalog.BLOCK_VALUE_MATERIALS;
    private static final List<String> TEMPLATE_COMMANDS = AdminCommandCatalog.TEMPLATE_COMMANDS;
    private static final List<String> MIGRATION_COMMANDS = AdminCommandCatalog.MIGRATION_COMMANDS;
    private static final List<String> NODE_DANGER_REASONS = AdminCommandCatalog.NODE_DANGER_REASONS;
    private static final List<String> HELP_COMMANDS = AdminCommandCatalog.HELP_COMMANDS;
    private static final List<String> MIGRATION_HELP_COMMANDS = AdminCommandCatalog.MIGRATION_HELP_COMMANDS;
    private static final String BONUS_LIMIT_PREFIX = "BONUS:";
    private static final List<String> ADMIN_RUNTIME_TARGETS = List.of("player", "island", "all");
    private static final List<String> ADMIN_OPEN_MENU_IDS = List.of(
        "island.main",
        "island.create",
        "island.visit",
        "island.chat",
        "island.my-islands",
        "admin.audit",
        "admin.dashboard",
        "admin.events",
        "admin.metrics",
        "admin.node",
        "admin.storage",
        "admin.templates",
        "admin.route",
        "admin.jobs",
        "admin.reviews",
        "admin.migration"
    );
    private final CloudIslandsPaperAgent agent;
    private final CoreApiClient coreApiClient;
    private final String nodeId;
    private final int routeWaitSeconds;
    private final LocalCacheManager localCaches;
    private final MessageRenderer messages;
    private final boolean superiorSkyblock2MigrationEnabled;
    private final AdminMigrationCommandHandler migrationHandler;
    private final AdminConfigCommandHandler configHandler;

    AdminCommandBackend(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId) {
        this(agent, coreApiClient, nodeId, 20);
    }

    AdminCommandBackend(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds) {
        this(agent, coreApiClient, nodeId, routeWaitSeconds, null);
    }

    AdminCommandBackend(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds, LocalCacheManager localCaches) {
        this(agent, coreApiClient, nodeId, routeWaitSeconds, localCaches, null);
    }

    AdminCommandBackend(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds, LocalCacheManager localCaches, MessageRenderer messages) {
        this(agent, coreApiClient, nodeId, routeWaitSeconds, localCaches, messages, true);
    }

    AdminCommandBackend(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds, LocalCacheManager localCaches, MessageRenderer messages, boolean superiorSkyblock2MigrationEnabled) {
        this.agent = agent;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
        this.localCaches = localCaches;
        this.messages = messages;
        this.superiorSkyblock2MigrationEnabled = superiorSkyblock2MigrationEnabled;
        this.migrationHandler = new AdminMigrationCommandHandler(
            agent,
            coreApiClient,
            superiorSkyblock2MigrationEnabled,
            this::adminText,
            this::run,
            this::sendCommandUsage,
            this::messagesFor
        );
        this.configHandler = new AdminConfigCommandHandler(
            agent,
            coreApiClient,
            this::adminText,
            this::run,
            this::sendCommandUsage,
            this::coreConfigMessage,
            result -> maintenanceMessage("Core reload", result)
        );
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!hasAdminAccess(sender, args)) {
            sender.sendMessage(adminText("admin-command-no-permission", "권한이 없습니다."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(adminText("admin-command-status-agent-prefix", "CloudIslands agent role=") + agent.role() + " node=" + nodeId);
            sender.sendMessage(adminText("admin-command-status-online-prefix", "CloudIslands onlinePlayers=") + agent.plugin().getServer().getOnlinePlayers().size() + " routeWaitSeconds=" + routeWaitSeconds);
            return true;
        }
        if (args[0].equalsIgnoreCase("dashboard")) {
            return handleDashboard(sender);
        }
        if (args[0].equalsIgnoreCase("doctor")) {
            return handleDoctor(sender, args);
        }
        if (args[0].equalsIgnoreCase("setup")) {
            return handleSetup(sender, args);
        }
        if (isHelpRequest(args)) {
            usage(sender, label, helpPage(args));
            return true;
        }
        if (args[0].equalsIgnoreCase("cache") && args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            if (localCaches == null) {
                agent.permissionCache().invalidateAll();
            } else {
                localCaches.invalidateAll();
            }
            run(sender, "CloudIslands local cache cleared. Core cache clear", coreApiClient.adminMaintenance().clearCache().thenApply(result -> maintenanceMessage("Cache clear", result)));
            return true;
        }
        if (args[0].equalsIgnoreCase("addons")) {
            return handleAddons(sender, args);
        }
        if (args[0].equalsIgnoreCase("integrations")) {
            return handleIntegrations(sender, args);
        }
        if (args[0].equalsIgnoreCase("reload")) {
            run(sender, "Core reload", coreApiClient.adminMaintenance().reload().thenApply(result -> maintenanceMessage("Core reload", result)));
            return true;
        }
        if (args[0].equalsIgnoreCase("node")) {
            return handleNode(sender, args);
        }
        if (args[0].equalsIgnoreCase("island")) {
            return handleIsland(sender, args);
        }
        if (args[0].equalsIgnoreCase("player")) {
            return handlePlayer(sender, args);
        }
        if (args[0].equalsIgnoreCase("message")) {
            return handleAdminMessageCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("title")) {
            return handleAdminTitleCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("cmd")) {
            return handleAdminCmdCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("fly")) {
            return handleAdminFlyCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("spy")) {
            return handleAdminSpyCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("openmenu")) {
            return handleOpenMenu(sender, args);
        }
        if (args[0].equalsIgnoreCase("jobs")) {
            return handleJobs(sender, args);
        }
        if (args[0].equalsIgnoreCase("route")) {
            return handleRoute(sender, args);
        }
        if (args[0].equalsIgnoreCase("rankings")) {
            return handleRankings(sender, args);
        }
        if (args[0].equalsIgnoreCase("events")) {
            if (sender instanceof Player player) {
                AdminEventMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player));
                return true;
            }
            run(sender, "Events list", coreApiClient.adminEvents().list(100).thenApply(this::eventListMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("audit")) {
            if (sender instanceof Player player) {
                AdminAuditMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player));
                return true;
            }
            run(sender, "Audit logs", coreApiClient.adminAudit().list(100).thenApply(this::auditListMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("metrics")) {
            if (sender instanceof Player player) {
                AdminMetricsMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player));
                return true;
            }
            run(sender, "Core metrics", coreApiClient.adminMetrics().summary().thenApply(this::metricsMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("config")) {
            return configHandler.handle(sender, args);
        }
        if (args[0].equalsIgnoreCase("storage")) {
            return handleStorage(sender, args);
        }
        if (args[0].equalsIgnoreCase("diagnostics")) {
            return handleDiagnostics(sender, args);
        }
        if (args[0].equalsIgnoreCase("support-bundle")) {
            return handleSupportBundle(sender, args);
        }
        if (args[0].equalsIgnoreCase("block-values")) {
            return handleBlockValues(sender, args);
        }
        if (args[0].equalsIgnoreCase("upgrade-rules")) {
            run(sender, "Upgrade rules", coreApiClient.progression().upgradeRules().thenApply(this::upgradeRulesMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("bonus") || args[0].equalsIgnoreCase("addbonus") || args[0].equalsIgnoreCase("syncbonus")) {
            return handleBonusCompatibility(sender, args);
        }
        if (gameplayModifierCommand(args[0])) {
            return handleGameplayModifier(sender, args);
        }
        if (args[0].equalsIgnoreCase("setspawn")) {
            return handleSetSpawn(sender, args);
        }
        if (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) {
            return handleTemplate(sender, args);
        }
        if (args[0].equalsIgnoreCase("migrate-superiorskyblock2") || migrationAlias(args)) {
            return migrationHandler.handle(sender, migrationArgs(args));
        }
        usage(sender, label, 1);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!hasAdminAccess(sender, args)) {
            return List.of();
        }
        if (args.length == 1) {
            return matches(rootCommands(), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("command")) {
            return matches(List.of("list", "목록"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("command") && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return matches(commandListPageSuggestions(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cache")) {
            return matches(CACHE_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addons")) {
            return matches(ADDON_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("integrations")) {
            return matches(List.of("report", "export", "status"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return matches(CONFIG_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return matches(SETUP_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("explain")) {
            return matches(List.of("node", "velocity", "storage", "security"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addons") && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("feature") || args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("disable") || args[1].equalsIgnoreCase("reload"))) {
            return matches(addonIds(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("addons") && args[1].equalsIgnoreCase("feature")) {
            return matches(addonFeatureKeys(args[2]), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("addons") && args[1].equalsIgnoreCase("feature")) {
            return matches(List.of("true", "false", "on", "off", "enabled", "disabled"), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("node")) {
            return matches(NODE_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("island")) {
            return matches(ISLAND_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("bank")) {
            return matches(List.of("deposit", "withdraw"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("reviews")) {
            return matches(List.of("10", "25", "50", "100"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("moderate-review")) {
            return matches(onlinePlayerNames(), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("moderate-review")) {
            return matches(List.of("VISIBLE", "REPORTED", "HIDDEN"), args[4]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("bank")) {
            return matches(List.of("100", "1000", "10000"), args[4]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("member")) {
            return matches(List.of("add", "kick", "promote", "demote", "setleader"), args[2]);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("member") && args[2].equalsIgnoreCase("add")) {
            return matches(List.of("MEMBER", "MODERATOR", "CO_OWNER", "TRUSTED"), args[5]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            return matches(PLAYER_COMMANDS, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("message") || args[0].equalsIgnoreCase("title") || args[0].equalsIgnoreCase("cmd") || args[0].equalsIgnoreCase("fly"))) {
            return matches(ADMIN_RUNTIME_TARGETS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spy")) {
            List<String> suggestions = new ArrayList<>(onlinePlayerNames());
            suggestions.addAll(List.of("true", "false", "on", "off", "enabled", "disabled", "toggle"));
            return matches(suggestions, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("message") || args[0].equalsIgnoreCase("title") || args[0].equalsIgnoreCase("cmd") || args[0].equalsIgnoreCase("fly")) && args[1].equalsIgnoreCase("player")) {
            return matches(onlinePlayerNames(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spy")) {
            return matches(List.of("true", "false", "on", "off", "enabled", "disabled", "toggle"), args[2]);
        }
        if ((args.length == 3 && args[0].equalsIgnoreCase("fly") && args[1].equalsIgnoreCase("all"))
            || (args.length == 4 && args[0].equalsIgnoreCase("fly") && !args[1].equalsIgnoreCase("all"))) {
            return matches(List.of("true", "false", "on", "off", "enabled", "disabled"), args[args.length - 1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("openmenu")) {
            return matches(onlinePlayerNames(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("openmenu")) {
            return matches(ADMIN_OPEN_MENU_IDS, args[2]);
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("cmd")) {
            return matches(List.of("--confirm"), args[args.length - 1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("jobs")) {
            return matches(JOB_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            return matches(List.of(nodeId, "recovery"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            return matches(List.of("60000", "300000", "600000"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            return matches(List.of("16", "32", "64"), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("route")) {
            return matches(ROUTE_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("storage")) {
            return matches(STORAGE_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("diagnostics")) {
            return matches(DIAGNOSTICS_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("support-bundle")) {
            return matches(SUPPORT_BUNDLE_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("bulk-restore")) {
            return matches(List.of("1", "10", "100"), args[2]);
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("bulk-restore")) {
            return matches(onlinePlayerNames(), args[args.length - 1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rankings")) {
            return matches(RANKING_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rankings")) {
            return matches(List.of("10", "25", "50", "100"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("block-values")) {
            return matches(BLOCK_VALUE_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            return matches(BLOCK_VALUE_MATERIALS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("search")) {
            return matches(BLOCK_VALUE_MATERIALS, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("search")) {
            return matches(List.of("5", "10", "25", "50"), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            return matches(List.of("1.0", "10.0", "100.0"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            return matches(List.of("1", "10", "100"), args[4]);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            return matches(List.of("0", "64", "256"), args[5]);
        }
        if (args.length == 2 && gameplayModifierCommand(args[0])) {
            return matches(onlinePlayerNames(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("seteffect")) {
            return matches(List.of("SPEED", "HASTE", "JUMP_BOOST", "NIGHT_VISION", "REGENERATION"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setblockamount")) {
            return matches(BLOCK_VALUE_MATERIALS, args[2]);
        }
        if (args.length == 3 && rateModifierCommand(args[0])) {
            return matches(List.of("50", "100", "150", "200"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            return matches(worldNames(), args[1]);
        }
        if (args.length >= 3 && args.length <= 5 && args[0].equalsIgnoreCase("setspawn")) {
            return matches(List.of("0", "64", "100", "128"), args[args.length - 1]);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("setspawn")) {
            return matches(List.of("0", "90", "180", "270"), args[5]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("seteffect") || args[0].equalsIgnoreCase("setblockamount"))) {
            return matches(List.of("0", "1", "2", "3", "100"), args[3]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates"))) {
            return matches(TEMPLATE_COMMANDS, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && (args[1].equalsIgnoreCase("preview") || args[1].equalsIgnoreCase("validate") || args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("disable") || args[1].equalsIgnoreCase("seticon") || args[1].equalsIgnoreCase("setcost") || args[1].equalsIgnoreCase("setpermission"))) {
            return matches(List.of("default", "superiorskyblock2"), args[2]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("seticon")) {
            return matches(List.of("GRASS_BLOCK", "DIRT", "OAK_SAPLING", "DIAMOND_BLOCK"), args[3]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("setcost")) {
            return matches(List.of("0", "100", "1000", "10000"), args[3]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("setpermission")) {
            return matches(List.of("none", "cloudislands.template.vip", "cloudislands.template.premium"), args[3]);
        }
        if (args.length == 5 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("seticon")) {
            return matches(List.of("0", "1", "1000"), args[4]);
        }
        if (args.length == 5 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            return matches(List.of("true", "false", "enabled", "disabled", "enable", "disable", "on", "off", "활성", "비활성"), args[4]);
        }
        if (args.length == 6 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            return matches(List.of("1.0.0", "1.21.0", "1.21.4"), args[5]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
            return matches(List.of("superiorskyblock2"), args[1]);
        }
        if ((args.length == 2 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) || (args.length == 3 && migrationAlias(args))) {
            if (!superiorSkyblock2MigrationEnabled()) {
                return List.of();
            }
            return matches(MIGRATION_COMMANDS, migrationAlias(args) ? args[2] : args[1]);
        }
        if ((args.length == 3 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) || (args.length == 4 && migrationAlias(args))) {
            if (!superiorSkyblock2MigrationEnabled()) {
                return List.of();
            }
            String action = migrationAlias(args) ? args[2] : args[1];
            if (action.equalsIgnoreCase("approve") || action.equalsIgnoreCase("import")) {
                return matches(List.of("<approvalToken>"), migrationAlias(args) ? args[3] : args[2]);
            }
            if (action.equalsIgnoreCase("unlock")) {
                return matches(List.of("--confirm"), migrationAlias(args) ? args[3] : args[2]);
            }
            return matches(List.of("plugins/SuperiorSkyblock2"), migrationAlias(args) ? args[3] : args[2]);
        }
        if (args.length == 5 && migrationAlias(args) && args[2].equalsIgnoreCase("unlock")) {
            return matches(List.of("<token>"), args[4]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("node")) {
            return matches(List.of(nodeId), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("islands")) {
            return matches(List.of("25", "50", "100"), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("node") && (args[1].equalsIgnoreCase("kickall") || args[1].equalsIgnoreCase("shutdown-safe"))) {
            return matches(NODE_DANGER_REASONS, args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("player")) {
            return matches(onlinePlayerNames(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("debug")) {
            List<String> targets = new ArrayList<>();
            targets.add("all");
            targets.addAll(onlinePlayerNames());
            return matches(targets, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("route") && (args[1].equalsIgnoreCase("ticket") || args[1].equalsIgnoreCase("tickets"))) {
            return matches(onlinePlayerNames(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("clear")) {
            return matches(onlinePlayerNames(), args[2]);
        }
        return List.of();
    }

    private List<String> commandListPageSuggestions() {
        int maxPage = CommandListPolicy.pages(HELP_COMMANDS.size());
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= maxPage; page++) {
            pages.add(String.valueOf(page));
        }
        return pages;
    }

    private boolean handleAddons(CommandSender sender, String[] args) {
        if (args.length > 1 && (args[1].equalsIgnoreCase("state") || args[1].equalsIgnoreCase("state-summary"))) {
            run(sender, "Addon state summary", coreApiClient.adminAddonState().summary().thenApply(this::addonStateSummaryMessage));
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("endpoints")) {
            run(sender, "Addon endpoints", coreApiClient.adminCoreConfig().config().thenApply(this::addonEndpointMessage));
            return true;
        }
        CloudIslandsApi api = CloudIslandsProvider.get().orElse(null);
        if (api == null) {
            sender.sendMessage(adminText("admin-command-addons-api-missing", "CloudIslands API가 준비되지 않았습니다."));
            return true;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Addons list", api.addons().list().thenApply(this::addonListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("info")) {
            if (args.length < 3) {
                sender.sendMessage(adminText("admin-command-addons-info-usage", "사용법: /ciadmin addons info <addonId>"));
                return true;
            }
            run(sender, "Addon info", api.addons().get(args[2]).thenApply(addon -> addon.map(this::addonInfoMessage).orElse(adminText("admin-command-addons-not-found", "Addon: not found ") + args[2])));
            return true;
        }
        if (args[1].equalsIgnoreCase("feature")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-addons-feature-usage", "사용법: /ciadmin addons feature <addonId> <feature> [true|false]"));
                return true;
            }
            if (args.length > 4) {
                boolean enabled = booleanArgument(args[4], false);
                run(sender, "Addon feature set", api.addons().get(args[2]).thenCompose(addon -> {
                    if (addon.isEmpty()) {
                        return java.util.concurrent.CompletableFuture.completedFuture(adminText("admin-command-addons-not-found", "Addon: not found ") + args[2]);
                    }
                    if (!addonFeatureKnown(addon.get(), args[3])) {
                        return java.util.concurrent.CompletableFuture.completedFuture(adminText("admin-command-addons-feature-invalid", "알 수 없는 addon feature입니다: ") + args[3]);
                    }
                    return api.addons().setFeature(args[2], args[3], enabled)
                        .thenApply(refreshed -> refreshed.map(this::addonInfoMessage).orElse(adminText("admin-command-addons-not-found", "Addon: not found ") + args[2]));
                }));
                return true;
            }
            run(sender, "Addon feature", api.addons().get(args[2]).thenApply(addon -> {
                if (addon.isEmpty()) {
                    return adminText("admin-command-addons-not-found", "Addon: not found ") + args[2];
                }
                if (!addonFeatureKnown(addon.get(), args[3])) {
                    return adminText("admin-command-addons-feature-invalid", "알 수 없는 addon feature입니다: ") + args[3];
                }
                return addonFeatureMessage(addon.get(), args[2], args[3]);
            }));
            return true;
        }
        if (args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("disable")) {
            if (args.length < 3) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin addons enable <addonId>",
                    "/ciadmin addons disable <addonId>"
                ));
                return true;
            }
            boolean enabled = args[1].equalsIgnoreCase("enable");
            run(sender, "Addon " + args[1].toLowerCase(Locale.ROOT), api.addons().setEnabled(args[2], enabled).thenApply(addon -> addon.map(this::addonInfoMessage).orElse(adminText("admin-command-addons-not-found", "Addon: not found ") + args[2])));
            return true;
        }
        if (args[1].equalsIgnoreCase("reload")) {
            CompletableFuture<PaperRuntimeConfigReloadResult> configReload = configHandler.reloadRuntimeConfig();
            if (args.length > 2) {
                run(sender, "Addon reload", configReload.thenCompose(result -> result.applied()
                    ? api.addons().refresh(args[2]).thenApply(addon -> configHandler.reloadResultMessage(result) + " | " + addon.map(this::addonInfoMessage).orElse(adminText("admin-command-addons-not-found", "Addon: not found ") + args[2]))
                    : CompletableFuture.completedFuture(configHandler.reloadResultMessage(result))));
            } else {
                run(sender, "Addons reload", configReload.thenCompose(result -> result.applied()
                    ? api.addons().refreshAll().thenApply(addons -> configHandler.reloadResultMessage(result) + " | " + addonListMessage(addons))
                    : CompletableFuture.completedFuture(configHandler.reloadResultMessage(result))));
            }
            return true;
        }
        sendCommandUsage(sender, List.of(
            "/ciadmin addons list",
            "/ciadmin addons info <addonId>",
            "/ciadmin addons feature <addonId> <feature> [true|false]",
            "/ciadmin addons enable <addonId>",
            "/ciadmin addons disable <addonId>",
            "/ciadmin addons reload [addonId]",
            "/ciadmin addons state",
            "/ciadmin addons endpoints"
        ));
        return true;
    }

    private boolean handleDiagnostics(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("export")) {
            sender.sendMessage(adminText("admin-command-diagnostics-usage", "사용법: /ciadmin diagnostics export"));
            return true;
        }
        CompletableFuture<DiagnosticSection> config = diagnosticSection("core-config", coreApiClient.adminCoreConfig().config().thenApply(this::coreConfigMessage));
        CompletableFuture<DiagnosticSection> metrics = diagnosticSection("metrics", coreApiClient.adminMetrics().summary().thenApply(this::metricsMessage));
        CompletableFuture<DiagnosticSection> storage = diagnosticSection("storage", coreApiClient.adminStorage().status().thenApply(this::storageStatusMessage));
        CompletableFuture<AdminNodeSummaryView> nodeSnapshot = coreApiClient.adminNodes().listNodesSummary();
        CompletableFuture<DiagnosticSection> nodes = diagnosticSection("nodes", nodeSnapshot.thenApply(summary -> adminNodeSummaryMessage("Nodes", summary)));
        CompletableFuture<DiagnosticSection> heartbeatLag = diagnosticSection("heartbeat-lag", nodeSnapshot.thenApply(this::heartbeatLagDiagnosticBody));
        CompletableFuture<DiagnosticSection> jobs = diagnosticSection("jobs", coreApiClient.jobs().list().thenApply(this::jobListMessage));
        CompletableFuture<DiagnosticSection> routes = diagnosticSection("route-debug", coreApiClient.adminRoutes().debug(new UUID(0L, 0L)).thenApply(this::routeDebugMessage));
        CompletableFuture<DiagnosticSection> audit = diagnosticSection("audit", coreApiClient.adminAudit().list(25).thenApply(this::auditListMessage));
        CompletableFuture<DiagnosticSection> configValidation = configHandler.validationDiagnosticSectionAsync()
            .thenApply(body -> new DiagnosticSection(body.toString()));
        CompletableFuture<DiagnosticSection> effectiveConfig = configHandler.effectiveConfigDiagnosticSectionAsync()
            .thenApply(body -> new DiagnosticSection(body.toString()));
        DiagnosticExportContext exportContext = new DiagnosticExportContext(
            agent.plugin().getDataFolder().toPath().resolve("diagnostics"),
            nodeId,
            agent.role().name(),
            agent.plugin().getPluginMeta().getVersion(),
            agent.plugin().getServer().getOnlinePlayers().size()
        );
        DiagnosticSection runtimeCompatibility = runtimeCompatibilityDiagnosticSection();
        DiagnosticSection integrations = integrationsDiagnosticSection();
        run(sender, "Diagnostics export", CompletableFuture.allOf(config, metrics, storage, nodes, heartbeatLag, jobs, routes, audit, configValidation, effectiveConfig)
            .thenCompose(_ignored -> PaperSchedulers.supplyAsync(agent.plugin(), () -> writeDiagnostics(exportContext, List.of(config.join(), metrics.join(), storage.join(), nodes.join(), heartbeatLag.join(), jobs.join(), routes.join(), audit.join(), configValidation.join(), effectiveConfig.join(), runtimeCompatibility, integrations)))));
        return true;
    }

    private boolean handleSupportBundle(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("create")) {
            sender.sendMessage(adminText("admin-command-support-bundle-usage", "사용법: /ciadmin support-bundle create"));
            return true;
        }
        run(sender, "Support bundle", coreApiClient.adminSupportBundle().create().thenApply(this::writeSupportBundle));
        return true;
    }

    private CompletableFuture<DiagnosticSection> diagnosticSection(String name, CompletableFuture<? extends CharSequence> future) {
        return future.handle((body, error) -> {
            StringBuilder builder = new StringBuilder();
            builder.append("## ").append(name).append('\n');
            if (error != null) {
                builder.append("error=").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');
            } else {
                builder.append(redactDiagnostic(body == null ? "" : body.toString())).append('\n');
            }
            return new DiagnosticSection(builder.toString());
        });
    }

    private String writeDiagnostics(DiagnosticExportContext context, List<DiagnosticSection> sections) {
        try {
            Path directory = context.directory();
            Files.createDirectories(directory);
            String timestamp = Instant.now().toString().replace(':', '-');
            Path report = directory.resolve("cloudislands-diagnostics-" + timestamp + ".txt");
            StringBuilder builder = new StringBuilder();
            builder.append("CloudIslands diagnostics export\n");
            builder.append("generatedAt=").append(Instant.now()).append('\n');
            builder.append("nodeId=").append(context.nodeId()).append('\n');
            builder.append("agentRole=").append(context.agentRole()).append('\n');
            builder.append("pluginVersion=").append(context.pluginVersion()).append('\n');
            builder.append("onlinePlayers=").append(context.onlinePlayers()).append("\n\n");
            for (DiagnosticSection section : sections) {
                builder.append(section.content()).append('\n');
            }
            Files.writeString(report, builder.toString());
            return adminText("admin-command-diagnostics-exported-prefix", "Diagnostics exported: ") + report;
        } catch (IOException exception) {
            return adminText("admin-command-diagnostics-export-failed", "Diagnostics export failed: ") + exception.getMessage();
        }
    }

    private String writeSupportBundle(String coreBundleJson) {
        try {
            Path directory = agent.plugin().getDataFolder().toPath().resolve("support-bundles");
            Files.createDirectories(directory);
            String timestamp = Instant.now().toString().replace(':', '-');
            Path report = directory.resolve("cloudislands-support-bundle-" + timestamp + ".zip");
            String redacted = redactDiagnostic(coreBundleJson == null ? "{}" : coreBundleJson);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(report))) {
                writeZipEntry(zip, "core-support-bundle.json", redacted);
                writeZipEntry(zip, "paper-runtime.txt", supportBundleRuntimeManifest());
            }
            return adminText("admin-command-support-bundle-created-prefix", "Support bundle created: ") + report;
        } catch (IOException exception) {
            return adminText("admin-command-support-bundle-failed", "Support bundle failed: ") + exception.getMessage();
        }
    }

    private String supportBundleRuntimeManifest() {
        return "generatedAt=" + Instant.now() + '\n'
            + "nodeId=" + nodeId + '\n'
            + "agentRole=" + agent.role() + '\n'
            + "pluginVersion=" + agent.plugin().getPluginMeta().getVersion() + '\n'
            + "onlinePlayers=" + agent.plugin().getServer().getOnlinePlayers().size() + '\n'
            + "routeWaitSeconds=" + routeWaitSeconds + '\n'
            + "islandInspectCommand=/ciadmin island inspect <player|island> --json\n";
    }

    private static void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String heartbeatLagDiagnosticBody(AdminNodeSummaryView nodes) {
        return "nodeCount=" + nodes.nodeCount() + '\n'
            + "routeCandidateCount=" + nodes.routeCandidateCount() + '\n'
            + "staleNodeCount=" + nodes.staleNodeCount() + '\n'
            + "heartbeatTimeoutSeconds=" + nodes.heartbeatTimeoutSeconds() + '\n';
    }

    private static String redactDiagnostic(String value) {
        return AdminDiagnosticRedactor.redact(value);
    }

    private boolean handleIntegrations(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(integrationStatusMessage());
            return true;
        }
        sender.sendMessage(integrationCertificationMessage());
        return true;
    }

    private String integrationCertificationMessage() {
        if (!(agent.plugin() instanceof CloudIslandsPaperPlugin plugin)) {
            return integrationStatusMessage();
        }
        try {
            IntegrationRuntimeCertification.CertificationReport report = plugin.integrationRegistry().certificationReport();
            IntegrationReportFiles files = writeIntegrationCertificationReport(report);
            String remediation = failedIntegrationRemediation(report);
            return adminText("admin-command-integrations-prefix", "Integrations: ")
                + report.summaryLine()
                + " status=" + plugin.integrationRegistry().statusLine()
                + " json=" + files.json()
                + " markdown=" + files.markdown()
                + (remediation.isBlank() ? "" : " remediation=" + remediation);
        } catch (IOException | RuntimeException exception) {
            return adminText("admin-command-integrations-report-failed", "Integration certification report failed: ") + exception.getMessage()
                + " / " + integrationStatusMessage();
        }
    }

    private IntegrationReportFiles writeIntegrationCertificationReport(IntegrationRuntimeCertification.CertificationReport report) throws IOException {
        Path directory = agent.plugin().getDataFolder().toPath().resolve("integration-reports");
        Files.createDirectories(directory);
        String timestamp = Instant.now().toString().replace(':', '-');
        Path json = directory.resolve("cloudislands-integrations-" + timestamp + ".json");
        Path markdown = directory.resolve("cloudislands-integrations-" + timestamp + ".md");
        Files.writeString(json, report.toJson());
        Files.writeString(markdown, report.toMarkdown());
        return new IntegrationReportFiles(json, markdown);
    }

    private String failedIntegrationRemediation(IntegrationRuntimeCertification.CertificationReport report) {
        List<String> entries = report.failedOperations().stream()
            .limit(3)
            .map(entry -> entry.pluginName() + "=" + entry.remediation())
            .toList();
        return String.join("; ", entries);
    }

    private String integrationStatusMessage() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin) {
            return adminText("admin-command-integrations-prefix", "Integrations: ") + plugin.integrationRegistry().statusLine();
        }
        List<String> entries = new ArrayList<>();
        for (String pluginName : kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy.knownPlugins()) {
            boolean enabled = agent.plugin().getServer().getPluginManager().isPluginEnabled(pluginName);
            entries.add(pluginName + "=" + (enabled ? "enabled" : "missing") + ":" + kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy.category(pluginName));
        }
        return adminText("admin-command-integrations-prefix", "Integrations: ") + String.join(", ", entries);
    }

    private DiagnosticSection integrationsDiagnosticSection() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin) {
            return new DiagnosticSection(plugin.integrationRegistry().diagnosticsSection());
        }
        return diagnosticSection("integrations", CompletableFuture.completedFuture(integrationStatusMessage())).join();
    }

    private DiagnosticSection runtimeCompatibilityDiagnosticSection() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin && plugin.runtimeCompatibility() != null) {
            return new DiagnosticSection(plugin.runtimeCompatibility().diagnosticsSection());
        }
        return new DiagnosticSection("## runtime-compatibility\npaperAdapterId=unavailable\n");
    }

    private boolean handleDoctor(CommandSender sender, String[] args) {
        CompletableFuture<AdminCoreConfigView> coreConfig = coreApiClient.adminCoreConfig().config();
        CompletableFuture<DoctorPart> config = doctorPart("core-config", coreConfig.thenApply(this::coreConfigMessage));
        CompletableFuture<DoctorPart> setupReadiness = doctorPart("setup-readiness", coreConfig.thenApply(this::setupReadinessDiagnosticBody));
        CompletableFuture<DoctorPart> snapshotPolicy = doctorPart("snapshot-policy", coreConfig.thenApply(this::snapshotPolicyDiagnosticBody));
        CompletableFuture<DoctorPart> metrics = doctorPart("metrics", coreApiClient.adminMetrics().summary().thenApply(this::metricsMessage));
        CompletableFuture<DoctorPart> storage = doctorPart("storage", coreApiClient.adminStorage().status().thenApply(this::storageStatusMessage));
        CompletableFuture<DoctorPart> nodes = doctorPart("nodes", coreApiClient.adminNodes().listNodesSummary().thenApply(summary ->
            adminNodeSummaryMessage("Nodes", summary) + " / " + heartbeatLagDiagnosticBody(summary).replace('\n', ' ').trim()));
        CompletableFuture<DoctorPart> jobs = doctorPart("jobs", coreApiClient.jobs().list().thenApply(this::jobListMessage));
        CompletableFuture<DoctorPart> routes = doctorPart("route-debug", coreApiClient.adminRoutes().debug(new UUID(0L, 0L)).thenApply(this::routeDebugMessage));
        CompletableFuture<DoctorPart> audit = doctorPart("audit", coreApiClient.adminAudit().list(5).thenApply(this::auditListMessage));
        CompletableFuture<DoctorPart> templates = doctorPart("templates", coreApiClient.templates().list().thenApply(this::templateDoctorDiagnosticBody));
        CompletableFuture<DoctorPart> integrations = CompletableFuture.completedFuture(doctorPart("integrations", integrationStatusMessage()));
        CompletableFuture<DoctorPart> ss2Migration = CompletableFuture.completedFuture(doctorPart("ss2-migration", superiorSkyblock2MigrationDiagnosticBody()));
        run(sender, "Doctor", CompletableFuture.allOf(config, setupReadiness, snapshotPolicy, metrics, storage, nodes, jobs, routes, audit, templates, integrations, ss2Migration)
            .thenApply(_ignored -> doctorMessage(args, List.of(config.join(), setupReadiness.join(), snapshotPolicy.join(), metrics.join(), storage.join(), nodes.join(), jobs.join(), routes.join(), audit.join(), templates.join(), integrations.join(), ss2Migration.join()))));
        return true;
    }

    private String superiorSkyblock2MigrationDiagnosticBody() {
        if (!(agent.plugin() instanceof CloudIslandsPaperPlugin plugin)) {
            return "migrationEnabled=" + superiorSkyblock2MigrationEnabled + " legacyAliases=unavailable";
        }
        boolean migrationEnabled = plugin.runtimeConfig().migration().superiorSkyblock2Enabled();
        boolean legacyAliasesEnabled = plugin.runtimeConfig().migration().superiorSkyblock2LegacyAliasesEnabled();
        if (migrationEnabled && !legacyAliasesEnabled) {
            return "WARN migrationEnabled=true legacyAliases=false existing /is commands may break; enable migration.legacy-aliases.superiorskyblock2.enabled during cutover";
        }
        return "migrationEnabled=" + migrationEnabled + " legacyAliases=" + legacyAliasesEnabled;
    }

    private boolean handleSetup(CommandSender sender, String[] args) {
        String section = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "start";
        if (section.equals("wizard")) {
            section = "start";
        }
        if (section.equals("verify")) {
            sender.sendMessage(adminText("admin-command-setup-verify-prefix", "Setup verify delegates to /ciadmin doctor for live CRITICAL/WARN/INFO checks."));
            return handleDoctor(sender, new String[] {"doctor"});
        }
        if (section.equals("explain")) {
            if (args.length < 3) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin setup explain node",
                    "/ciadmin setup explain velocity",
                    "/ciadmin setup explain storage",
                    "/ciadmin setup explain security"
                ));
                return true;
            }
            sender.sendMessage(setupExplainMessage(args[2]));
            return true;
        }
        if (section.equals("export-redacted")) {
            run(sender, "Setup export-redacted", configHandler.effectiveConfigDiagnosticSectionAsync());
            return true;
        }
        if (!SETUP_COMMANDS.contains(section)) {
            sendCommandUsage(sender, List.of(
                "/ciadmin setup start",
                "/ciadmin setup wizard",
                "/ciadmin setup core",
                "/ciadmin setup redis",
                "/ciadmin setup database",
                "/ciadmin setup storage",
                "/ciadmin setup velocity",
                "/ciadmin setup paper-node",
                "/ciadmin setup verify",
                "/ciadmin setup explain <node|velocity|storage|security>",
                "/ciadmin setup export-redacted"
            ));
            return true;
        }
        sender.sendMessage(setupMessage(section));
        return true;
    }

    private String setupMessage(String section) {
        return switch (section) {
            case "core" -> "Setup core: configure core-api base-url/token/admin-token, then run /ciadmin config validate, /ciadmin config effective, and /ciadmin doctor.";
            case "redis" -> "Setup redis: configure Redis only as cache/event transport, verify it is not source-of-truth, then run /ciadmin doctor and /ciadmin metrics.";
            case "database" -> "Setup database: configure durable shared JDBC for production, avoid in-memory production state, then run /ciadmin config validate and /ciadmin doctor.";
            case "storage" -> "Setup storage: configure object storage credentials and bucket/prefix, then run /ciadmin storage, /ciadmin support-bundle create, and backup/restore rehearsal gates.";
            case "velocity" -> "Setup velocity: configure forwarding secret, fallback server, island pool names, route wait seconds, and hide-node-names; verify with /ciadmin status and /ciadmin doctor.";
            case "paper-node" -> "Setup paper-node: configure unique node.id, role, core-api, storage, integrations, and supported templates; verify with /ciadmin node list and /ciadmin doctor.";
            default -> "Setup start: complete /ciadmin setup core, redis, database, storage, velocity, paper-node, then run /ciadmin setup verify.";
        };
    }

    private String setupExplainMessage(String section) {
        return switch (section.toLowerCase(Locale.ROOT)) {
            case "node", "paper-node" -> "Setup explain node: every island node needs a unique node.id, unique velocity-server-name per pool, fresh heartbeat, shared storage, supported templates, and non-default identity.";
            case "velocity" -> "Setup explain velocity: Velocity must have the Paper backend name registered, modern forwarding enabled with the same secret, route ticket forwarding intact, and fallback routing that hides physical node names from players.";
            case "storage" -> "Setup explain storage: production pools should use shared object storage such as S3/MinIO, verify manifest.json plus checksums.sha256, and rehearse latest snapshot restore before opening migration writes.";
            case "security" -> "Setup explain security: keep Core tokens and forwarding secrets redacted, require proxy source allowlists, avoid direct island-node joins, and use /ciadmin setup export-redacted for support output.";
            default -> "Setup explain: choose node, velocity, storage, or security.";
        };
    }

    private boolean handleDashboard(CommandSender sender) {
        if (sender instanceof Player player) {
            AdminDashboardMenu.open(player, messagesFor(player));
            return true;
        }
        CompletableFuture<CharSequence> metrics = doctorPart("metrics", coreApiClient.adminMetrics().summary().thenApply(this::metricsMessage)).thenApply(DoctorPart::text);
        CompletableFuture<CharSequence> nodes = doctorPart("nodes", coreApiClient.adminNodes().listNodesSummary().thenApply(summary -> adminNodeSummaryMessage("Nodes", summary))).thenApply(DoctorPart::text);
        CompletableFuture<CharSequence> jobs = doctorPart("jobs", coreApiClient.jobs().list().thenApply(this::jobListMessage)).thenApply(DoctorPart::text);
        CompletableFuture<CharSequence> routes = doctorPart("routes", coreApiClient.adminRoutes().debug(new UUID(0L, 0L)).thenApply(this::routeDebugMessage)).thenApply(DoctorPart::text);
        CompletableFuture<CharSequence> storage = doctorPart("storage", coreApiClient.adminStorage().status().thenApply(this::storageStatusMessage)).thenApply(DoctorPart::text);
        CompletableFuture<CharSequence> integrations = CompletableFuture.completedFuture("integrations=" + integrationStatusMessage());
        run(sender, "Dashboard", CompletableFuture.allOf(metrics, nodes, jobs, routes, storage, integrations)
            .thenApply(_ignored -> dashboardMessage(List.of(metrics.join(), nodes.join(), jobs.join(), routes.join(), storage.join(), integrations.join()))));
        return true;
    }

    private CompletableFuture<DoctorPart> doctorPart(String label, CompletableFuture<? extends CharSequence> future) {
        return future.handle((body, error) -> {
            if (error != null) {
                return doctorPart(label, "ERROR(" + error.getClass().getSimpleName() + ")");
            }
            String text = body == null ? "" : body.toString().replace('\n', ' ').trim();
            String value = text.isBlank() ? "empty" : text;
            return doctorPart(label, value);
        });
    }

    private DoctorPart doctorPart(String label, String value) {
        String severity = doctorSeverity(value);
        return new DoctorPart(label, severity, value, doctorRecommendation(label, severity, value));
    }

    private String doctorSeverity(String body) {
        String normalized = body == null ? "" : body.toUpperCase(Locale.ROOT);
        if (normalized.contains("ERROR")
            || normalized.contains("INVALID")
            || normalized.contains("FAILED")
            || normalized.contains("DOWN")
            || normalized.contains("UNAVAILABLE")) {
            return "CRITICAL";
        }
        if (normalized.contains("WARN")
            || normalized.contains("MISSING")
            || normalized.contains("NOT-CERTIFIED")
            || normalized.contains("DEGRADED")) {
            return "WARN";
        }
        return "INFO";
    }

    private String doctorRecommendation(String label, String severity, String body) {
        if (severity.equals("INFO")) {
            return "none";
        }
        String normalized = body == null ? "" : body.toUpperCase(Locale.ROOT);
        if (label.equals("core-config")) {
            return "/ciadmin config validate";
        }
        if (label.equals("setup-readiness")) {
            return "/ciadmin setup verify";
        }
        if (label.equals("storage") || normalized.contains("STORAGE")) {
            return "/ciadmin storage";
        }
        if (label.equals("nodes") || normalized.contains("HEARTBEAT") || normalized.contains("NODE")) {
            return "/ciadmin node list";
        }
        if (label.equals("route-debug") || normalized.contains("ROUTE") || normalized.contains("TICKET")) {
            return "/ciadmin route debug all";
        }
        if (label.equals("templates") || normalized.contains("TEMPLATE") || normalized.contains("BUNDLE")) {
            return "/ciadmin template list";
        }
        if (label.equals("jobs") || normalized.contains("JOB")) {
            return "/ciadmin jobs list";
        }
        if (label.equals("integrations") || normalized.contains("INTEGRATION") || normalized.contains("NOT-CERTIFIED")) {
            return "/ciadmin integrations report";
        }
        if (label.equals("ss2-migration")) {
            return "/ciadmin config effective";
        }
        return "/ciadmin support-bundle create";
    }

    private CharSequence doctorMessage(String[] args, List<DoctorPart> parts) {
        DoctorReport report = new DoctorReport(agent.role().name(), nodeId, agent.plugin().getServer().getOnlinePlayers().size(), routeWaitSeconds, parts);
        if (hasOption(args, "--json")) {
            return doctorJson(report);
        }
        if (hasOption(args, "--markdown")) {
            return doctorMarkdown(report);
        }
        return doctorText(report);
    }

    private boolean hasOption(String[] args, String option) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    private CharSequence doctorText(DoctorReport report) {
        List<String> renderedParts = report.parts().stream().map(DoctorPart::text).toList();
        return "Doctor: role=" + report.role()
            + " node=" + report.nodeId()
            + " online=" + report.onlinePlayers()
            + " routeWaitSeconds=" + report.routeWaitSeconds()
            + " summary=" + report.summary()
            + (renderedParts.isEmpty() ? "" : " | " + String.join(" | ", renderedParts));
    }

    private CharSequence doctorJson(DoctorReport report) {
        String parts = report.parts().stream()
            .map(part -> "{\"label\":\"" + jsonEscape(part.label()) + "\",\"severity\":\"" + part.severity() + "\",\"message\":\"" + jsonEscape(part.message()) + "\",\"recommendedCommand\":\"" + jsonEscape(part.recommendedCommand()) + "\"}")
            .collect(java.util.stream.Collectors.joining(","));
        return "{\"doctor\":{\"role\":\"" + jsonEscape(report.role())
            + "\",\"nodeId\":\"" + jsonEscape(report.nodeId())
            + "\",\"onlinePlayers\":" + report.onlinePlayers()
            + ",\"routeWaitSeconds\":" + report.routeWaitSeconds()
            + ",\"summary\":\"" + report.summary()
            + "\",\"sections\":[" + parts + "]}}";
    }

    private CharSequence doctorMarkdown(DoctorReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# CloudIslands doctor").append('\n');
        builder.append("role=").append(report.role())
            .append(" node=").append(report.nodeId())
            .append(" online=").append(report.onlinePlayers())
            .append(" routeWaitSeconds=").append(report.routeWaitSeconds())
            .append(" summary=").append(report.summary())
            .append('\n');
        builder.append('\n').append("| Severity | Section | Recommended command | Detail |").append('\n');
        builder.append("|---|---|---|---|").append('\n');
        for (DoctorPart part : report.parts()) {
            builder.append("| ").append(part.severity())
                .append(" | ").append(markdownCell(part.label()))
                .append(" | `").append(markdownCell(part.recommendedCommand())).append("`")
                .append(" | ").append(markdownCell(part.message()))
                .append(" |").append('\n');
        }
        return builder.toString().trim();
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String markdownCell(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace("|", "\\|").replace('\n', ' ');
    }

    private record DoctorPart(String label, String severity, String message, String recommendedCommand) {
        String text() {
            return "[" + severity + "] " + label + "=" + message + " recommend=" + recommendedCommand;
        }
    }

    private record DoctorReport(String role, String nodeId, int onlinePlayers, int routeWaitSeconds, List<DoctorPart> parts) {
        String summary() {
            long critical = parts.stream().filter(part -> part.severity().equals("CRITICAL")).count();
            long warn = parts.stream().filter(part -> part.severity().equals("WARN")).count();
            long info = parts.stream().filter(part -> part.severity().equals("INFO")).count();
            return "CRITICAL=" + critical + ",WARN=" + warn + ",INFO=" + info;
        }
    }

    private record IslandInspectReport(
        String target,
        CoreGuiViews.IslandInfoView info,
        AdminIslandRuntimeView runtime,
        CoreGuiViews.BankView bank,
        List<CoreGuiViews.SnapshotView> snapshots,
        IslandVisitorStatsView visitors,
        List<JobView> jobs,
        List<AdminAuditEntryView> audit,
        AdminRouteDebugView routes,
        AdminStorageStatusView storage
    ) {
        private IslandInspectReport {
            target = target == null ? "" : target;
            snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
            audit = audit == null ? List.of() : List.copyOf(audit);
        }
    }

    private String snapshotPolicyDiagnosticBody(AdminCoreConfigView body) {
        return "snapshotLatest=" + longValue(body, "snapshotKeepLatest")
            + " snapshotRetention=" + longValue(body, "snapshotKeepHourly") + "/" + longValue(body, "snapshotKeepDaily") + "/" + longValue(body, "snapshotKeepWeekly") + "/" + longValue(body, "snapshotKeepManual")
            + " snapshotCompress=" + boolValue(body, "snapshotCompress")
            + " snapshotChecksum=" + textValue(body, "snapshotChecksumAlgorithm")
            + " snapshotTriggers=" + textValue(body, "snapshotRequiredTriggerReasons")
            + " snapshotRestore=" + textValue(body, "snapshotRestorePipeline");
    }

    private String setupReadinessDiagnosticBody(AdminCoreConfigView body) {
        boolean durableDatabase = boolValue(body, "coreSetupDatabaseDurable") || boolValue(body, "coreSetupDatabaseProductionDurable");
        boolean sharedStorage = boolValue(body, "storageSharedBackend") || boolValue(body, "storageMultiNodeSafe");
        long duplicateVelocityNames = longValue(body, "islandPoolDuplicateVelocityServerNameNodeCount");
        long defaultIdentityRisk = longValue(body, "islandPoolDefaultNodeIdentityRiskCount");
        long routeTicketTtl = longValue(body, "routeTicketTtlSeconds");
        List<String> warnings = new ArrayList<>();
        if (!durableDatabase) {
            warnings.add("WARN_DATABASE_NOT_DURABLE");
        }
        if (!sharedStorage) {
            warnings.add("WARN_STORAGE_NOT_SHARED");
        }
        if (duplicateVelocityNames > 0L) {
            warnings.add("WARN_DUPLICATE_VELOCITY_BACKEND_NAMES");
        }
        if (defaultIdentityRisk > 0L) {
            warnings.add("WARN_DEFAULT_NODE_IDENTITY");
        }
        if (routeTicketTtl <= 0L) {
            warnings.add("WARN_ROUTE_TICKET_TTL_MISSING");
        }
        return "coreApiReachable=true"
            + " redisReachable=policy:" + textValue(body, "redisRolePolicy")
            + " sqlRepositoryMode=" + textValue(body, "effectiveRepositoryMode")
            + " databaseDurable=" + durableDatabase
            + " storageType=" + textValue(body, "storageType")
            + " storageShared=" + sharedStorage
            + " velocityBackendNames=duplicateCount:" + duplicateVelocityNames
            + " nodeIdentity=defaultRiskCount:" + defaultIdentityRisk
            + " forwardingSecretCheck=security.forwarding-secret+velocity-modern-forwarding-required"
            + " routeTicketSmoke=ttl:" + routeTicketTtl + "s,debug:/ciadmin route debug all"
            + " templateChecksum=" + textValue(body, "storageRestoreChecksumPolicy")
            + " migrationReadiness=enabled:" + boolValue(body, "superiorSkyblock2MigrationEnabled") + ",inputOnly:" + boolValue(body, "superiorSkyblock2MigrationInputOnly")
            + " setupWarnings=" + (warnings.isEmpty() ? "none" : String.join(",", warnings));
    }

    private CharSequence dashboardMessage(List<CharSequence> parts) {
        List<String> renderedParts = parts.stream().map(CharSequence::toString).toList();
        return adminText("admin-command-dashboard-prefix", "Dashboard: ")
            + "role=" + agent.role()
            + " node=" + nodeId
            + " online=" + agent.plugin().getServer().getOnlinePlayers().size()
            + (renderedParts.isEmpty() ? "" : " | " + String.join(" | ", renderedParts));
    }

    private boolean handleNode(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) {
                MessageRenderer menuMessages = messagesFor(player);
                GuiSession menuSession = GuiSessions.begin(player, "admin.node");
                GuiStateMenus.openLoading(agent.plugin(), player, menuSession, menuMessages, adminText("admin-node-menu-title", "섬 노드 관리"));
                coreApiClient.adminNodes().nodeInfo(nodeId)
                    .thenAccept(summary -> openNodeMenuIfCurrent(player, menuSession, menuMessages, summary))
                    .exceptionally(error -> {
                        openNodeMenuIfCurrent(player, menuSession, menuMessages, null);
                        return null;
                    });
            } else {
                sender.sendMessage(adminText("admin-command-node-menu-player-only", "플레이어만 노드 관리 메뉴를 열 수 있습니다."));
            }
            return true;
        }
        if (args.length < 2) {
            if (sender instanceof Player player) {
                AdminNodeListMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player));
            } else {
                run(sender, "Node list", coreApiClient.adminNodes().listNodesSummary().thenApply(summary -> adminNodeSummaryMessage("Nodes", summary)));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("list")) {
            run(sender, "Node list", coreApiClient.adminNodes().listNodesSummary().thenApply(summary -> adminNodeSummaryMessage("Nodes", summary)));
            return true;
        }
        String targetNode = args.length > 2 ? args[2] : nodeId;
        if (args[1].equalsIgnoreCase("info")) {
            run(sender, "Node info", coreApiClient.adminNodes().nodeInfo(targetNode).thenApply(this::nodeInfoMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("islands")) {
            run(sender, "Node islands", coreApiClient.adminNodes().nodeIslandsSummary(targetNode, nodeIslandLimit(args)).thenApply(summary -> adminNodeSummaryMessage(adminText("admin-command-node-island-status-title", "노드 섬 현황"), summary)));
            return true;
        }
        if (args[1].equalsIgnoreCase("drain")) {
            run(sender, "Node drain", coreApiClient.adminNodeCommands().drainNode(targetNode).thenApply(result -> nodeActionSummaryMessage("Node drain", targetNode, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("undrain")) {
            run(sender, "Node undrain", coreApiClient.adminNodeCommands().undrainNode(targetNode).thenApply(result -> nodeActionSummaryMessage("Node undrain", targetNode, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("sweep")) {
            run(sender, "Node sweep", coreApiClient.adminNodeCommands().sweepNode(targetNode).thenApply(result -> nodeActionSummaryMessage("Node sweep", targetNode, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("kickall")) {
            run(sender, "Node kickall", coreApiClient.adminNodeCommands().kickAllNode(targetNode, args.length > 3 ? joined(args, 3) : "admin").thenApply(result -> nodeActionSummaryMessage("Node kickall", targetNode, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("shutdown-safe")) {
            run(sender, "Node shutdown-safe", coreApiClient.adminNodeCommands().shutdownNodeSafely(targetNode, args.length > 3 ? joined(args, 3) : "admin").thenApply(result -> nodeActionSummaryMessage("Node shutdown-safe", targetNode, result)));
            return true;
        }
        sendCommandUsage(sender, List.of(
            "/ciadmin node menu",
            "/ciadmin node list",
            "/ciadmin node info [node]",
            "/ciadmin node islands [node] [limit]",
            "/ciadmin node drain [node]",
            "/ciadmin node undrain [node]",
            "/ciadmin node sweep [node]",
            "/ciadmin node kickall [node]",
            "/ciadmin node shutdown-safe [node]"
        ));
        return true;
    }

    private void openNodeMenuIfCurrent(Player player, GuiSession menuSession, MessageRenderer menuMessages, CoreGuiViews.NodeSummaryView summary) {
        GuiSessions.runIfCurrent(agent.plugin(), player, menuSession, () -> {
            if (summary == null) {
                AdminNodeMenu.open(player, nodeId, menuMessages);
            } else {
                AdminNodeMenu.open(player, nodeId, summary, menuMessages);
            }
        });
    }

    private CompletableFuture<CharSequence> islandWhereMessage(String target) {
        UUID parsed = uuidOrNull(target);
        if (parsed != null) {
            return coreApiClient.adminIslands().runtime(parsed)
                .thenCompose(runtime -> runtime.code().isBlank()
                    ? CompletableFuture.completedFuture(runtimeInfoMessage(runtime))
                    : playerPrimaryIslandRuntime(parsed, target, runtimeInfoMessage(runtime)));
        }
        return coreApiClient.adminIslands().infoByName(target)
            .thenCompose(island -> {
                UUID islandId = uuidOrNull(island.islandId());
                if (islandId != null) {
                    return coreApiClient.adminIslands().runtime(islandId).thenApply(this::runtimeInfoMessage);
                }
                return findPlayerUuid(target).thenCompose(playerUuid -> {
                    if (playerUuid == null) {
                        return CompletableFuture.completedFuture(adminText("admin-command-island-where-target-not-found", "섬 또는 플레이어를 찾지 못했습니다: ") + target);
                    }
                    return playerPrimaryIslandRuntime(playerUuid, target, adminText("admin-command-island-where-target-not-found", "섬 또는 플레이어를 찾지 못했습니다: ") + target);
                });
            });
    }

    private CompletableFuture<CharSequence> playerPrimaryIslandRuntime(UUID playerUuid, String target, String fallback) {
        return coreApiClient.playerProfiles().profile(playerUuid)
            .thenCompose(profile -> {
                UUID islandId = uuidOrNull(profile.primaryIslandId());
                if (islandId == null) {
                    return CompletableFuture.completedFuture((CharSequence) (adminText("admin-command-player-primary-island-missing", "플레이어의 대표 섬이 없습니다: ") + target));
                }
                String playerLabel = profile.lastName().isBlank() ? shortId(profile.playerUuid()) : profile.lastName();
                return coreApiClient.adminIslands().runtime(islandId)
                    .thenApply(runtime -> (CharSequence) (adminText("admin-command-island-where-player-prefix", "Player island where: player=") + playerLabel + " " + runtimeInfoMessage(runtime)));
            })
            .exceptionally(_error -> fallback);
    }

    private CompletableFuture<CharSequence> islandInspectMessage(String target, boolean json) {
        return islandInspectInfo(target).thenCompose(info -> {
            UUID islandId = uuidOrNull(info.islandId());
            if (islandId == null) {
                IslandInspectReport report = new IslandInspectReport(target, info, missingRuntime(), new CoreGuiViews.BankView("0", ""), List.of(), new IslandVisitorStatsView("", 0L, 0L, List.of()), List.of(), List.of(), new AdminRouteDebugView(List.of(), List.of()), new AdminStorageStatusView(List.of()));
                return CompletableFuture.completedFuture(json ? islandInspectJson(report) : islandInspectText(report));
            }
            CompletableFuture<AdminIslandRuntimeView> runtime = withFallback(coreApiClient.adminIslands().runtime(islandId), missingRuntime());
            CompletableFuture<CoreGuiViews.BankView> bank = withFallback(coreApiClient.bank().islandBank(islandId), new CoreGuiViews.BankView("ERROR", ""));
            CompletableFuture<List<CoreGuiViews.SnapshotView>> snapshots = withFallback(coreApiClient.snapshots().listSnapshots(islandId, 5), List.of());
            CompletableFuture<IslandVisitorStatsView> visitors = withFallback(coreApiClient.visitorStats().stats(islandId, 10), new IslandVisitorStatsView(islandId.toString(), 0L, 0L, List.of()));
            CompletableFuture<List<JobView>> jobs = withFallback(coreApiClient.jobs().list(), List.of());
            CompletableFuture<List<AdminAuditEntryView>> audit = withFallback(coreApiClient.adminAudit().list(10), List.of());
            CompletableFuture<AdminRouteDebugView> routes = withFallback(coreApiClient.adminRoutes().debug(new UUID(0L, 0L)), new AdminRouteDebugView(List.of(), List.of()));
            CompletableFuture<AdminStorageStatusView> storage = withFallback(coreApiClient.adminStorage().status(), new AdminStorageStatusView(List.of()));
            return CompletableFuture.allOf(runtime, bank, snapshots, visitors, jobs, audit, routes, storage)
                .thenApply(_ignored -> {
                    IslandInspectReport report = new IslandInspectReport(target, info, runtime.join(), bank.join(), snapshots.join(), visitors.join(), jobs.join(), audit.join(), routes.join(), storage.join());
                    return json ? islandInspectJson(report) : islandInspectText(report);
                });
        });
    }

    private CompletableFuture<CoreGuiViews.IslandInfoView> islandInspectInfo(String target) {
        UUID parsed = uuidOrNull(target);
        if (parsed != null) {
            return coreApiClient.adminIslands().info(parsed)
                .handle((info, error) -> validIslandInfo(info) ? CompletableFuture.completedFuture(info) : playerPrimaryIslandInfo(parsed))
                .thenCompose(future -> future);
        }
        return coreApiClient.adminIslands().infoByName(target)
            .handle((info, error) -> validIslandInfo(info) ? CompletableFuture.completedFuture(info) : findPlayerUuid(target).thenCompose(playerUuid -> playerUuid == null ? CompletableFuture.completedFuture(missingIslandInfo()) : playerPrimaryIslandInfo(playerUuid)))
            .thenCompose(future -> future)
            .exceptionally(_error -> missingIslandInfo());
    }

    private CompletableFuture<CoreGuiViews.IslandInfoView> playerPrimaryIslandInfo(UUID playerUuid) {
        return coreApiClient.playerProfiles().profile(playerUuid)
            .thenCompose(profile -> {
                UUID islandId = uuidOrNull(profile.primaryIslandId());
                return islandId == null ? CompletableFuture.completedFuture(missingIslandInfo()) : coreApiClient.adminIslands().info(islandId);
            })
            .exceptionally(_error -> missingIslandInfo());
    }

    private boolean validIslandInfo(CoreGuiViews.IslandInfoView info) {
        return info != null && !info.islandId().isBlank() && uuidOrNull(info.islandId()) != null;
    }

    private CoreGuiViews.IslandInfoView missingIslandInfo() {
        return new CoreGuiViews.IslandInfoView("", "NOT_FOUND", "", 0L, "0", false, false, 0L, 0L, "");
    }

    private AdminIslandRuntimeView missingRuntime() {
        return new AdminIslandRuntimeView("", "", "", "", null, null, "", 0L, "", "", "UNAVAILABLE");
    }

    private <T> CompletableFuture<T> withFallback(CompletableFuture<T> future, T fallback) {
        return future.exceptionally(_error -> fallback);
    }

    private String islandInspectText(IslandInspectReport report) {
        if (!validIslandInfo(report.info())) {
            return adminText("admin-command-island-inspect-not-found", "Island inspect: target not found=") + report.target();
        }
        String islandId = report.info().islandId();
        List<JobView> islandJobs = islandJobs(report);
        List<AdminAuditEntryView> islandAudit = islandAudit(report);
        List<AdminRouteTicketView> islandTickets = islandRouteTickets(report);
        CoreGuiViews.SnapshotView latest = latestSnapshot(report.snapshots());
        return adminText("admin-command-island-inspect-prefix", "Island inspect: target=") + report.target()
            + adminText("admin-command-island-inspect-island-prefix", " island=") + shortId(islandId)
            + adminText("admin-command-island-info-owner-prefix", " owner=") + shortId(report.info().ownerUuid())
            + (report.info().name().isBlank() ? "" : adminText("admin-command-island-info-name-prefix", " name=") + report.info().name())
            + adminText("admin-command-island-info-state-prefix", " state=") + (report.info().state().isBlank() ? "UNKNOWN" : report.info().state())
            + adminText("admin-command-island-info-level-prefix", " level=") + report.info().level()
            + adminText("admin-command-island-info-worth-prefix", " worth=") + report.info().worth()
            + adminText("admin-command-island-inspect-bank-prefix", " bank=") + report.bank().balance()
            + " | " + runtimeInfoMessage(report.runtime())
            + " | " + islandInspectRouteSummary(islandTickets)
            + " | " + islandInspectVisitorSummary(report.visitors())
            + " | " + islandInspectSnapshotSummary(latest, report.snapshots().size())
            + " | " + islandInspectJobSummary(islandJobs)
            + " | " + islandInspectAuditSummary(islandAudit)
            + " | storage=" + storageStatusMessage(report.storage())
            + " | recommend=" + islandInspectRecommendation(report, islandJobs, islandTickets);
    }

    private String islandInspectJson(IslandInspectReport report) {
        List<JobView> islandJobs = islandJobs(report);
        List<AdminAuditEntryView> islandAudit = islandAudit(report);
        List<AdminRouteTicketView> islandTickets = islandRouteTickets(report);
        CoreGuiViews.SnapshotView latest = latestSnapshot(report.snapshots());
        return "{\"islandInspect\":{"
            + "\"target\":\"" + jsonEscape(report.target()) + "\","
            + "\"islandId\":\"" + jsonEscape(report.info().islandId()) + "\","
            + "\"name\":\"" + jsonEscape(report.info().name()) + "\","
            + "\"ownerUuid\":\"" + jsonEscape(report.info().ownerUuid()) + "\","
            + "\"state\":\"" + jsonEscape(report.info().state()) + "\","
            + "\"level\":" + report.info().level() + ","
            + "\"worth\":\"" + jsonEscape(report.info().worth()) + "\","
            + "\"activeNode\":\"" + jsonEscape(report.runtime().activeNode()) + "\","
            + "\"activeWorld\":\"" + jsonEscape(report.runtime().activeWorld()) + "\","
            + "\"runtimeCode\":\"" + jsonEscape(report.runtime().code()) + "\","
            + "\"bankBalance\":\"" + jsonEscape(report.bank().balance()) + "\","
            + "\"visits\":" + report.visitors().totalVisits() + ","
            + "\"uniqueVisitors\":" + report.visitors().uniqueVisitors() + ","
            + "\"latestSnapshot\":" + (latest.snapshotNo() <= 0L ? "null" : "{\"snapshotNo\":" + latest.snapshotNo() + ",\"checksum\":\"" + jsonEscape(shortChecksum(latest.checksum())) + "\",\"storagePath\":\"" + jsonEscape(latest.storagePath()) + "\"}") + ","
            + "\"snapshotCount\":" + report.snapshots().size() + ","
            + "\"pendingJobs\":" + islandJobs.size() + ","
            + "\"auditEvents\":" + islandAudit.size() + ","
            + "\"routeTickets\":" + islandTickets.size() + ","
            + "\"storageUnavailable\":" + report.storage().unavailableCount() + ","
            + "\"recommendedCommand\":\"" + jsonEscape(islandInspectRecommendation(report, islandJobs, islandTickets)) + "\""
            + "}}";
    }

    private List<JobView> islandJobs(IslandInspectReport report) {
        String islandId = report.info().islandId();
        return report.jobs().stream()
            .filter(job -> job.islandId().equalsIgnoreCase(islandId) || job.payload().values().stream().anyMatch(islandId::equalsIgnoreCase))
            .limit(10)
            .toList();
    }

    private List<AdminAuditEntryView> islandAudit(IslandInspectReport report) {
        String islandId = report.info().islandId();
        return report.audit().stream()
            .filter(entry -> entry.targetId().equalsIgnoreCase(islandId) || entry.payload().values().stream().anyMatch(islandId::equalsIgnoreCase))
            .limit(10)
            .toList();
    }

    private List<AdminRouteTicketView> islandRouteTickets(IslandInspectReport report) {
        String islandId = report.info().islandId();
        return report.routes().tickets().stream()
            .filter(ticket -> ticket.islandId().equalsIgnoreCase(islandId))
            .limit(10)
            .toList();
    }

    private CoreGuiViews.SnapshotView latestSnapshot(List<CoreGuiViews.SnapshotView> snapshots) {
        return snapshots.stream()
            .max((left, right) -> Long.compare(left.snapshotNo(), right.snapshotNo()))
            .orElse(new CoreGuiViews.SnapshotView(0L, "", 0L, ""));
    }

    private String islandInspectRouteSummary(List<AdminRouteTicketView> tickets) {
        if (tickets.isEmpty()) {
            return "routes=tickets=0";
        }
        String entries = tickets.stream().limit(3).map(this::ticketSummary).collect(java.util.stream.Collectors.joining(", "));
        return "routes=tickets=" + tickets.size() + " [" + entries + "]";
    }

    private String islandInspectVisitorSummary(IslandVisitorStatsView visitors) {
        return "visitors=total:" + visitors.totalVisits() + ",unique:" + visitors.uniqueVisitors();
    }

    private String islandInspectSnapshotSummary(CoreGuiViews.SnapshotView latest, int count) {
        if (latest.snapshotNo() <= 0L) {
            return "snapshot=none checked=" + count;
        }
        return "snapshot=#" + latest.snapshotNo()
            + (latest.storagePath().isBlank() ? " path=missing" : " path=" + latest.storagePath())
            + (latest.checksum().isBlank() ? " checksum=missing" : " checksum=" + shortChecksum(latest.checksum()))
            + " checked=" + count;
    }

    private String islandInspectJobSummary(List<JobView> jobs) {
        long failed = jobs.stream().filter(job -> job.state().equalsIgnoreCase("FAILED")).count();
        long pending = jobs.stream().filter(job -> job.state().equalsIgnoreCase("PENDING")).count();
        long claimed = jobs.stream().filter(job -> job.state().equalsIgnoreCase("CLAIMED")).count();
        return "jobs=total:" + jobs.size() + ",pending:" + pending + ",claimed:" + claimed + ",failed:" + failed;
    }

    private String islandInspectAuditSummary(List<AdminAuditEntryView> audit) {
        if (audit.isEmpty()) {
            return "audit=0";
        }
        String latest = audit.get(0).action().isBlank() ? "UNKNOWN_ACTION" : audit.get(0).action();
        return "audit=" + audit.size() + " latest=" + latest;
    }

    private String islandInspectRecommendation(IslandInspectReport report, List<JobView> jobs, List<AdminRouteTicketView> tickets) {
        if (!report.runtime().code().isBlank()) {
            return "/ciadmin doctor";
        }
        if (report.storage().unavailableCount() > 0L || latestSnapshot(report.snapshots()).storagePath().isBlank()) {
            return "/ciadmin storage verify " + report.info().islandId();
        }
        if (jobs.stream().anyMatch(job -> job.state().equalsIgnoreCase("FAILED"))) {
            return "/ciadmin jobs recover";
        }
        if (!tickets.isEmpty()) {
            return "/ciadmin route debug all";
        }
        return "none";
    }

    private boolean handleStorage(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                AdminStorageMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player));
            } else {
                run(sender, "Storage status", coreApiClient.adminStorage().status().thenApply(this::storageStatusMessage));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("status")) {
            run(sender, "Storage status", coreApiClient.adminStorage().status().thenApply(this::storageStatusMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("verify")) {
            if (args.length < 3) {
                sendStorageCommandUsage(sender);
                return true;
            }
            resolveIslandUuid(sender, args[2]).thenAccept(islandId -> {
                if (islandId != null) {
                    run(sender, "Storage verify", storageVerifyMessage(islandId));
                }
            }).exceptionally(error -> {
                message(sender, adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + args[2]);
                return null;
            });
            return true;
        }
        sendStorageCommandUsage(sender);
        return true;
    }

    private void sendStorageCommandUsage(CommandSender sender) {
        sendCommandUsage(sender, List.of(
            "/ciadmin storage",
            "/ciadmin storage status",
            "/ciadmin storage verify <island>"
        ));
    }

    private MessageRenderer messagesFor(Player player) {
        return messages == null || player == null ? messages : messages.forLocale(PlayerLocaleCache.clientLocale(player));
    }

    private boolean handleIsland(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reviews")) {
            int limit = args.length > 2 ? (int) Math.max(1L, Math.min(number(args[2], 10L), 100L)) : 10;
            if (sender instanceof Player player) {
                AdminReviewModerationMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player), limit);
            } else {
                run(sender, "Review moderation queue", coreApiClient.navigationCommands().reviewModerationQueue(limit).thenApply(this::reviewModerationQueueMessage));
            }
            return true;
        }
        if (args.length < 3) {
            sendIslandCommandUsage(sender);
            return true;
        }
        if (args[1].equalsIgnoreCase("info")) {
            UUID lookupId = uuidOrNull(args[2]);
            if (lookupId != null) {
                run(sender, "Island info", coreApiClient.adminIslands().info(lookupId).thenCompose(this::islandInfoDetailsMessage));
            } else {
                run(sender, "Island info", coreApiClient.adminIslands().infoByName(args[2]).thenCompose(this::islandInfoDetailsMessage));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("where")) {
            run(sender, "Island where", islandWhereMessage(args[2]));
            return true;
        }
        if (args[1].equalsIgnoreCase("inspect")) {
            run(sender, "Island inspect", islandInspectMessage(args[2], hasOption(args, "--json")));
            return true;
        }
        if (args[1].equalsIgnoreCase("bulk-restore")) {
            return handleBulkRestore(sender, args);
        }
        if (args[1].equalsIgnoreCase("bank")) {
            return handleIslandBank(sender, args);
        }
        if (args[1].equalsIgnoreCase("member")) {
            return handleIslandMember(sender, args);
        }
        UUID islandId = uuidOrNull(args[2]);
        if (islandId == null) {
            resolveIslandUuid(sender, args[2]).thenAccept(resolvedIslandId -> {
                if (resolvedIslandId == null) {
                    return;
                }
                String[] resolvedArgs = args.clone();
                resolvedArgs[2] = resolvedIslandId.toString();
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> handleIsland(sender, resolvedArgs));
            }).exceptionally(error -> {
                message(sender, adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + args[2]);
                return null;
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("visitor-stats") || args[1].equalsIgnoreCase("visitors")) {
            run(sender, "Island visitor stats", coreApiClient.visitorStats().stats(islandId, 10).thenApply(this::visitorStatsMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("ignore") || args[1].equalsIgnoreCase("unignore")) {
            boolean ignored = args[1].equalsIgnoreCase("ignore");
            String label = ignored ? "Island ignore" : "Island unignore";
            run(sender, label, coreApiClient.progressionCommands().setRankingIgnored(islandId, ignored).thenApply(result -> rankingIgnoreMessage(label, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("mission")) {
            return handleIslandMission(sender, args, islandId);
        }
        if (args[1].equalsIgnoreCase("rankup")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of("/ciadmin island rankup <islandUuid|islandName> <upgradeKey>"));
                return true;
            }
            run(sender, "Island rankup", coreApiClient.progressionCommands().adminPurchaseUpgrade(islandId, args[3]).thenApply(result -> upgradePurchaseMessage("Island rankup", result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("join")) {
            return handleIslandJoin(sender, args, islandId);
        }
        if (args[1].equalsIgnoreCase("setpermission")) {
            if (args.length < 6) {
                sendCommandUsage(sender, List.of("/ciadmin island setpermission <islandUuid|islandName> <role> <permission> <true|false>"));
                return true;
            }
            IslandPermission permission = islandPermission(args[4]);
            Boolean allowed = strictBooleanArgument(args[5]);
            if (permission == null || allowed == null) {
                sender.sendMessage(adminText("admin-command-permission-input-invalid", "올바른 권한과 true/false 값을 입력해주세요."));
                return true;
            }
            run(sender, "Island setpermission", coreApiClient.permissions().adminSetPermission(islandId, args[3], permission, allowed).thenApply(result -> permissionActionMessage("Island setpermission", result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("resetpermissions")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of("/ciadmin island resetpermissions <islandUuid|islandName> <role>"));
                return true;
            }
            run(sender, "Island resetpermissions", coreApiClient.permissions().adminResetPermissions(islandId, args[3]).thenApply(result -> permissionActionMessage("Island resetpermissions", result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("tp")) {
            if (sender instanceof Player player) {
                routeAdminTeleport(player, islandId);
            } else {
                sender.sendMessage(adminText("admin-command-island-tp-player-only", "플레이어만 섬으로 이동할 수 있습니다."));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("activate")) {
            run(sender, "Island activate", coreApiClient.lifecycle().activateIsland(islandId).thenApply(action -> islandLifecycleActionMessage("Island activate", islandId, action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("deactivate")) {
            run(sender, "Island deactivate", coreApiClient.lifecycle().deactivateIsland(islandId).thenApply(action -> islandLifecycleActionMessage("Island deactivate", islandId, action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("migrate")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-target-node-required", "대상 노드를 입력해주세요."));
                return true;
            }
            run(sender, "Island migrate", coreApiClient.lifecycle().migrateIsland(islandId, args[3]).thenApply(action -> islandLifecycleActionMessage("Island migrate", islandId, action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("save")) {
            String reason = args.length > 3 ? joined(args, 3) : "ADMIN_SAVE";
            run(sender, "Island save", coreApiClient.lifecycle().saveIsland(islandId, reason).thenApply(action -> islandLifecycleActionMessage("Island save", islandId, action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("snapshot")) {
            String reason = args.length > 3 ? joined(args, 3) : "ADMIN_MANUAL";
            run(sender, "Island snapshot", coreApiClient.lifecycle().snapshotIsland(islandId, reason).thenApply(action -> islandLifecycleActionMessage("Island snapshot", islandId, action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("snapshots")) {
            int limit = args.length > 3 ? (int) number(args[3], 20L) : 20;
            run(sender, "Island snapshots", coreApiClient.snapshots().listSnapshots(islandId, Math.max(1, Math.min(limit, 50))).thenApply(this::snapshotListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("rename")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-island-name-required", "새 섬 이름을 입력해주세요."));
                return true;
            }
            run(sender, "Island rename", coreApiClient.settingsCommands().adminSetName(islandId, joined(args, 3)).thenApply(result -> settingsActionMessage("Island rename", islandId, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("setbiome") || args[1].equalsIgnoreCase("biome")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-island-biome-required", "바이옴 키를 입력해주세요."));
                return true;
            }
            run(sender, "Island setbiome", coreApiClient.environmentCommands().adminSetBiome(islandId, args[3]).thenApply(result -> gameplayModifierMessage("Island setbiome", result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("delwarp") || args[1].equalsIgnoreCase("deletewarp")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-island-warp-required", "워프 이름을 입력해주세요."));
                return true;
            }
            run(sender, "Island delwarp", coreApiClient.homeWarpCommands().adminDeleteWarp(islandId, args[3]).thenApply(result -> homeWarpActionMessage("Island delwarp", islandId, args[3], result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("delhome") || args[1].equalsIgnoreCase("deletehome")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-island-home-required", "홈 이름을 입력해주세요."));
                return true;
            }
            run(sender, "Island delhome", coreApiClient.homeWarpCommands().adminDeleteHome(islandId, args[3]).thenApply(result -> homeWarpActionMessage("Island delhome", islandId, args[3], result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("setgenerator")) {
            if (args.length < 5) {
                sendCommandUsage(sender, List.of("/ciadmin island setgenerator <islandUuid|islandName> <generatorKey> <level>"));
                return true;
            }
            run(sender, "Island setgenerator", coreApiClient.generatorCommands().adminSetGenerator(islandId, args[3], (int) number(args[4], 1L)).thenApply(result -> generatorActionMessage("Island setgenerator", result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("addgenerator")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of("/ciadmin island addgenerator <islandUuid|islandName> <levels> [generatorKey]"));
                return true;
            }
            String generatorKey = args.length > 4 ? args[4] : "";
            run(sender, "Island addgenerator", coreApiClient.generatorCommands().adminAddGeneratorLevels(islandId, generatorKey, (int) number(args[3], 1L)).thenApply(result -> generatorActionMessage("Island addgenerator", result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("cleargenerator")) {
            run(sender, "Island cleargenerator", coreApiClient.generatorCommands().adminClearGenerator(islandId).thenApply(result -> generatorActionMessage("Island cleargenerator", result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("setblocklimit") || args[1].equalsIgnoreCase("addblocklimit") || args[1].equalsIgnoreCase("removeblocklimit")) {
            if (args.length < 4 || (!args[1].equalsIgnoreCase("removeblocklimit") && args.length < 5)) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin island setblocklimit <islandUuid|islandName> <materialKey> <value>",
                    "/ciadmin island addblocklimit <islandUuid|islandName> <materialKey> <delta>",
                    "/ciadmin island removeblocklimit <islandUuid|islandName> <materialKey>"
                ));
                return true;
            }
            String limitKey = GameplayParityPolicy.blockAmountLimitKey(args[3]);
            String label = "Island " + args[1].toLowerCase(Locale.ROOT);
            if (args[1].equalsIgnoreCase("addblocklimit")) {
                run(sender, label, coreApiClient.environmentCommands().adminAddLimit(islandId, limitKey, number(args[4], 0L)).thenApply(result -> gameplayModifierMessage(label, result)));
            } else if (args[1].equalsIgnoreCase("removeblocklimit")) {
                run(sender, label, coreApiClient.environmentCommands().adminSetLimit(islandId, limitKey, Long.MAX_VALUE).thenApply(result -> gameplayModifierMessage(label, result)));
            } else {
                run(sender, label, coreApiClient.environmentCommands().adminSetLimit(islandId, limitKey, number(args[4], 0L)).thenApply(result -> gameplayModifierMessage(label, result)));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("setrate")) {
            if (args.length < 5) {
                sendCommandUsage(sender, List.of("/ciadmin island setrate <islandUuid|islandName> <reviewerUuid|reviewerName> <rating> [comment]"));
                return true;
            }
            int rating = (int) number(args[4], 0L);
            String comment = args.length > 5 ? joined(args, 5) : "";
            resolvePlayerUuid(sender, args[3]).thenAccept(reviewerUuid -> {
                if (reviewerUuid == null) {
                    return;
                }
                run(sender, "Island setrate", coreApiClient.navigationCommands().setReview(islandId, reviewerUuid, rating, comment).thenApply(result -> reviewActionMessage("Island setrate", result)));
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("removeratings")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of("/ciadmin island removeratings <islandUuid|islandName> <reviewerUuid|reviewerName>"));
                return true;
            }
            resolvePlayerUuid(sender, args[3]).thenAccept(reviewerUuid -> {
                if (reviewerUuid == null) {
                    return;
                }
                run(sender, "Island removeratings", coreApiClient.navigationCommands().deleteReview(islandId, reviewerUuid).thenApply(result -> reviewActionMessage("Island removeratings", result)));
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("moderate-review")) {
            if (args.length < 5) {
                sendCommandUsage(sender, List.of("/ciadmin island moderate-review <islandUuid|islandName> <reviewerUuid|reviewerName> <VISIBLE|REPORTED|HIDDEN> [note]"));
                return true;
            }
            String moderationState = reviewModerationState(args[4]);
            if (moderationState.isBlank()) {
                sender.sendMessage("Review moderation state must be VISIBLE, REPORTED, or HIDDEN.");
                return true;
            }
            String note = args.length > 5 ? joined(args, 5) : "admin-review-moderation";
            UUID moderatorUuid = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
            resolvePlayerUuid(sender, args[3]).thenAccept(reviewerUuid -> {
                if (reviewerUuid == null) {
                    return;
                }
                run(sender, "Review moderation", coreApiClient.navigationCommands()
                    .moderateReview(islandId, reviewerUuid, moderatorUuid, moderationState, note)
                    .thenApply(this::reviewModerationMessage));
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("setsettings")) {
            if (args.length < 5) {
                sendCommandUsage(sender, List.of("/ciadmin island setsettings <islandUuid|islandName> <flag> <value>"));
                return true;
            }
            IslandFlag flag = islandFlag(args[3]);
            if (flag == null) {
                sender.sendMessage(adminText("admin-command-flag-input-invalid", "올바른 섬 플래그를 입력해주세요."));
                return true;
            }
            run(sender, "Island setsettings", coreApiClient.settingsCommands().adminSetFlag(islandId, flag, args[4]).thenApply(result -> settingsActionMessage("Island setsettings", islandId, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("resetsettings")) {
            run(sender, "Island resetsettings", coreApiClient.settingsCommands().adminResetFlags(islandId).thenApply(result -> settingsActionMessage("Island resetsettings", islandId, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("removeentitylimit")) {
            String limitKey = args.length > 3 ? GameplayParityPolicy.entityTypeLimitKey(args[3]) : "ENTITY";
            run(sender, "Island removeentitylimit", coreApiClient.environmentCommands().adminSetLimit(islandId, limitKey, Long.MAX_VALUE).thenApply(result -> gameplayModifierMessage("Island removeentitylimit", result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("setrolelimit")) {
            if (args.length < 5) {
                sendCommandUsage(sender, List.of("/ciadmin island setrolelimit <islandUuid|islandName> <role> <value>"));
                return true;
            }
            String roleKey = GameplayParityPolicy.normalizeGameplayKey(args[3], "MEMBER");
            String limitKey = GameplayParityPolicy.roleLimitKey(roleKey);
            String label = "Island setrolelimit";
            run(sender, label, coreApiClient.environmentCommands().adminSetLimit(islandId, limitKey, number(args[4], 0L)).thenApply(result -> gameplayModifierMessage(label, result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("setchestrow")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of("/ciadmin island setchestrow <islandUuid|islandName> <rows>"));
                return true;
            }
            long rows = Math.max(1L, Math.min(number(args[3], 6L), 6L));
            String label = "Island setchestrow";
            run(sender, label, coreApiClient.environmentCommands().adminSetLimit(islandId, GameplayParityPolicy.WAREHOUSE_ROWS_LIMIT_KEY, rows).thenApply(result -> gameplayModifierMessage(label, result)));
            return true;
        }
        String adminLimitKey = adminLimitKey(args[1]);
        if (!adminLimitKey.isBlank()) {
            if (args.length < 4) {
                sendCommandUsage(sender, args[1].toLowerCase(Locale.ROOT).contains("entitylimit")
                    ? List.of(
                        "/ciadmin island " + args[1].toLowerCase(Locale.ROOT) + " <islandUuid|islandName> <value>",
                        "/ciadmin island " + args[1].toLowerCase(Locale.ROOT) + " <islandUuid|islandName> <entityType> <value>"
                    )
                    : List.of("/ciadmin island " + args[1].toLowerCase(Locale.ROOT) + " <islandUuid|islandName> <value>"));
                return true;
            }
            boolean typedEntityLimit = args[1].toLowerCase(Locale.ROOT).contains("entitylimit") && args.length > 4;
            if (typedEntityLimit) {
                adminLimitKey = GameplayParityPolicy.entityTypeLimitKey(args[3]);
            }
            long value = number(args[typedEntityLimit ? 4 : 3], 0L);
            String label = "Island " + args[1].toLowerCase(Locale.ROOT);
            if (args[1].toLowerCase(Locale.ROOT).startsWith("add")) {
                run(sender, label, coreApiClient.environmentCommands().adminAddLimit(islandId, adminLimitKey, value).thenApply(result -> gameplayModifierMessage(label, result)));
            } else {
                run(sender, label, coreApiClient.environmentCommands().adminSetLimit(islandId, adminLimitKey, value).thenApply(result -> gameplayModifierMessage(label, result)));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("restore") || args[1].equalsIgnoreCase("rollback")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-snapshot-required", "스냅샷 번호를 입력해주세요."));
                return true;
            }
            if (!confirmed(args)) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin island restore <islandUuid|islandName> <snapshotNo> --confirm",
                    "/ciadmin island rollback <islandUuid|islandName> <snapshotNo> --confirm"
                ));
                return true;
            }
            long snapshotNo = number(args[3], 0L);
            if (snapshotNo <= 0L) {
                sender.sendMessage(adminText("admin-command-snapshot-invalid", "스냅샷 번호가 올바르지 않습니다: ") + args[3]);
                return true;
            }
            if (args[1].equalsIgnoreCase("rollback")) {
                run(sender, "Island rollback", coreApiClient.lifecycle().rollbackIslandSnapshot(islandId, snapshotNo).thenApply(action -> islandLifecycleActionMessage("Island rollback", islandId, action)));
            } else {
                run(sender, "Island restore", coreApiClient.lifecycle().restoreIslandSnapshot(islandId, snapshotNo).thenApply(action -> islandLifecycleActionMessage("Island restore", islandId, action)));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("quarantine")) {
            run(sender, "Island quarantine", coreApiClient.lifecycle().quarantineIsland(islandId, args.length > 3 ? joined(args, 3) : "admin").thenApply(action -> islandLifecycleActionMessage("Island quarantine", islandId, action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("recover") || args[1].equalsIgnoreCase("repair")) {
            String actionLabel = args[1].equalsIgnoreCase("recover") ? "Island recover" : "Island repair";
            run(sender, actionLabel, coreApiClient.lifecycle().repairIsland(islandId, args.length > 3 ? joined(args, 3) : "admin").thenApply(action -> islandLifecycleActionMessage(actionLabel, islandId, action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("delete")) {
            if (!confirmed(args)) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin island delete <islandUuid|islandName> --confirm"
                ));
                return true;
            }
            run(sender, "Island delete", coreApiClient.lifecycle().adminDeleteIsland(islandId).thenApply(action -> islandLifecycleActionMessage("Island delete", islandId, action)));
            return true;
        }
        sendIslandCommandUsage(sender);
        return true;
    }

    private boolean handleIslandMember(CommandSender sender, String[] args) {
        if (args.length < 5 || !List.of("add", "kick", "promote", "demote", "setleader").contains(args[2].toLowerCase(Locale.ROOT))) {
            sendCommandUsage(sender, List.of(
                "/ciadmin island member add <islandUuid|islandName> <playerUuid|playerName> [role]",
                "/ciadmin island member kick <islandUuid|islandName> <playerUuid|playerName>",
                "/ciadmin island member promote <islandUuid|islandName> <playerUuid|playerName>",
                "/ciadmin island member demote <islandUuid|islandName> <playerUuid|playerName>",
                "/ciadmin island member setleader <islandUuid|islandName> <playerUuid|playerName>"
            ));
            return true;
        }
        resolveIslandUuid(sender, args[3]).thenAccept(islandId -> {
            if (islandId == null) {
                return;
            }
            resolvePlayerUuid(sender, args[4]).thenAccept(playerUuid -> {
                if (playerUuid == null) {
                    return;
                }
                String operation = args[2].toLowerCase(Locale.ROOT);
                String label = switch (operation) {
                    case "add" -> "Island member add";
                    case "kick" -> "Island member kick";
                    case "promote" -> "Island member promote";
                    case "demote" -> "Island member demote";
                    default -> "Island member setleader";
                };
                CompletableFuture<MemberActionView> action = switch (operation) {
                    case "add" -> coreApiClient.memberCommands().adminAddMember(islandId, playerUuid, args.length > 5 ? args[5] : "MEMBER");
                    case "kick" -> coreApiClient.memberCommands().adminKickMember(islandId, playerUuid);
                    case "promote" -> coreApiClient.memberCommands().adminPromoteMember(islandId, playerUuid);
                    case "demote" -> coreApiClient.memberCommands().adminDemoteMember(islandId, playerUuid);
                    default -> coreApiClient.memberCommands().adminSetLeader(islandId, playerUuid);
                };
                run(sender, label, action.thenApply(result -> memberActionMessage(label, result)));
            });
        }).exceptionally(error -> {
            message(sender, adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + args[3]);
            return null;
        });
        return true;
    }

    private boolean handleIslandJoin(CommandSender sender, String[] args, UUID islandId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(adminText("admin-command-island-join-player-only", "플레이어만 섬에 강제 가입할 수 있습니다."));
            return true;
        }
        String roleKey = args.length > 3 ? args[3] : "MEMBER";
        String label = "Island join";
        run(sender, label, coreApiClient.memberCommands().adminAddMember(islandId, player.getUniqueId(), roleKey).thenApply(result -> memberActionMessage(label, result)));
        return true;
    }

    private boolean handleIslandMission(CommandSender sender, String[] args, UUID islandId) {
        if (args.length < 6 || !(args[3].equalsIgnoreCase("complete") || args[3].equalsIgnoreCase("progress"))) {
            sendCommandUsage(sender, List.of(
                "/ciadmin island mission complete <islandUuid|islandName> <playerUuid|playerName> <missionKey> [kind]",
                "/ciadmin island mission progress <islandUuid|islandName> <playerUuid|playerName> <missionKey> <amount> [kind]"
            ));
            return true;
        }
        String action = args[3].toLowerCase(Locale.ROOT);
        if (action.equals("progress") && args.length < 7) {
            sendCommandUsage(sender, List.of("/ciadmin island mission progress <islandUuid|islandName> <playerUuid|playerName> <missionKey> <amount> [kind]"));
            return true;
        }
        resolvePlayerUuid(sender, args[4]).thenAccept(actorUuid -> {
            if (actorUuid == null) {
                return;
            }
            String missionKey = args[5];
            String kind = action.equals("progress") && args.length > 7 ? args[7] : action.equals("complete") && args.length > 6 ? args[6] : "MISSION";
            String label = "Island mission " + action;
            if (action.equals("progress")) {
                run(sender, label, coreApiClient.progressionCommands().adminProgressMission(islandId, actorUuid, missionKey, kind, number(args[6], 1L)).thenApply(result -> missionActionMessage(label, result)));
            } else {
                run(sender, label, coreApiClient.progressionCommands().adminCompleteMission(islandId, actorUuid, missionKey, kind).thenApply(result -> missionActionMessage(label, result)));
            }
        });
        return true;
    }

    private boolean handleIslandBank(CommandSender sender, String[] args) {
        if (args.length < 5 || (!args[2].equalsIgnoreCase("deposit") && !args[2].equalsIgnoreCase("withdraw"))) {
            sendCommandUsage(sender, List.of(
                "/ciadmin island bank deposit <islandUuid|islandName> <amount>",
                "/ciadmin island bank withdraw <islandUuid|islandName> <amount>"
            ));
            return true;
        }
        UUID islandId = uuidOrNull(args[3]);
        if (islandId == null) {
            resolveIslandUuid(sender, args[3]).thenAccept(resolvedIslandId -> {
                if (resolvedIslandId == null) {
                    return;
                }
                String[] resolvedArgs = args.clone();
                resolvedArgs[3] = resolvedIslandId.toString();
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> handleIslandBank(sender, resolvedArgs));
            }).exceptionally(error -> {
                message(sender, adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + args[3]);
                return null;
            });
            return true;
        }
        String label = args[2].equalsIgnoreCase("deposit") ? "Island bank deposit" : "Island bank withdraw";
        CompletableFuture<BankMutationView> mutation = args[2].equalsIgnoreCase("deposit")
            ? coreApiClient.lifecycle().adminBankDeposit(islandId, args[4])
            : coreApiClient.lifecycle().adminBankWithdraw(islandId, args[4]);
        run(sender, label, mutation.thenApply(action -> adminBankMutationMessage(label, islandId, action)));
        return true;
    }

    private boolean handleBulkRestore(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(adminText("admin-command-bulk-restore-usage", "사용법: /ciadmin island bulk-restore <snapshotNo> <islandUuid|islandName>... --confirm"));
            return true;
        }
        if (!confirmed(args)) {
            sender.sendMessage(adminText("admin-command-bulk-restore-usage", "사용법: /ciadmin island bulk-restore <snapshotNo> <islandUuid|islandName>... --confirm"));
            return true;
        }
        long snapshotNo = number(args[2], 0L);
        if (snapshotNo <= 0L) {
            sender.sendMessage(adminText("admin-command-snapshot-invalid", "스냅샷 번호가 올바르지 않습니다: ") + args[2]);
            return true;
        }
        List<CompletableFuture<BulkRestoreEntry>> restores = new ArrayList<>();
        for (int index = 3; index < args.length - 1; index++) {
            String target = args[index];
            restores.add(resolveIslandUuid(sender, target).thenCompose(islandId -> {
                if (islandId == null) {
                    return CompletableFuture.completedFuture(new BulkRestoreEntry(target, null, false, "NOT_FOUND"));
                }
                return coreApiClient.lifecycle().restoreIslandSnapshot(islandId, snapshotNo)
                    .handle((action, error) -> {
                        if (error != null) {
                            return new BulkRestoreEntry(target, islandId, false, error.getClass().getSimpleName());
                        }
                        return new BulkRestoreEntry(target, islandId, action.accepted(), action.code());
                    });
            }));
        }
        run(sender, "Island bulk restore", CompletableFuture.allOf(restores.toArray(CompletableFuture[]::new))
            .thenApply(_ignored -> bulkRestoreMessage(snapshotNo, restores.stream().map(CompletableFuture::join).toList())));
        return true;
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        List<String> usage = List.of(
            "/ciadmin player info <playerUuid|playerName>",
            "/ciadmin player setisland <playerUuid|playerName> <islandUuid|islandName>",
            "/ciadmin player clearisland <playerUuid|playerName>",
            "/ciadmin player setdisbands <playerUuid|playerName> <value>",
            "/ciadmin player givedisbands <playerUuid|playerName> <delta>"
        );
        if (args.length < 3) {
            sendCommandUsage(sender, usage);
            return true;
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("info", "setisland", "clearisland", "setdisbands", "givedisbands").contains(operation)) {
            sendCommandUsage(sender, usage);
            return true;
        }
        if ((operation.equals("setisland") || operation.equals("setdisbands") || operation.equals("givedisbands")) && args.length < 4) {
            String key = operation.equals("setisland")
                ? "admin-command-island-target-required"
                : operation.equals("setdisbands") ? "admin-command-player-disbands-value-required" : "admin-command-player-disbands-delta-required";
            String fallback = operation.equals("setisland")
                ? "섬 UUID 또는 이름을 입력해주세요."
                : operation.equals("setdisbands") ? "디스밴드 횟수를 입력해주세요." : "추가할 디스밴드 횟수를 입력해주세요.";
            sender.sendMessage(adminText(key, fallback));
            return true;
        }
        String requestedIslandTarget = operation.equals("setisland") ? args[3] : "";
        int requestedDisbands = operation.equals("setdisbands")
            ? boundedInt(Math.max(0L, number(args[3], 0L)))
            : operation.equals("givedisbands") ? boundedInt(number(args[3], 0L)) : 0;
        resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
            if (playerUuid == null) {
                return;
            }
            if (operation.equals("setisland")) {
                setPlayerPrimaryIsland(sender, playerUuid, requestedIslandTarget);
                return;
            }
            CompletableFuture<? extends CharSequence> action = switch (operation) {
                case "info" -> coreApiClient.playerProfiles().profile(playerUuid).thenApply(this::playerInfoMessage);
                case "clearisland" -> coreApiClient.playerProfileCommands().clearPrimaryIsland(playerUuid).thenApply(profile -> playerActionMessage("Player clearisland", profile));
                case "setdisbands" -> coreApiClient.playerProfileCommands().setDisbandsRemaining(playerUuid, requestedDisbands).thenApply(profile -> playerDisbandsActionMessage("Player setdisbands", profile));
                default -> coreApiClient.playerProfileCommands().addDisbandsRemaining(playerUuid, requestedDisbands).thenApply(profile -> playerDisbandsActionMessage("Player givedisbands", profile));
            };
            run(sender, "Player " + operation, action);
        }).exceptionally(error -> {
            message(sender, adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[2]);
            return null;
        });
        return true;
    }

    private void setPlayerPrimaryIsland(CommandSender sender, UUID playerUuid, String islandTarget) {
        resolveIslandUuid(sender, islandTarget)
            .thenAccept(islandId -> {
                if (islandId != null) {
                    run(sender, "Player setisland", coreApiClient.playerProfileCommands().setPrimaryIsland(playerUuid, islandId)
                        .thenApply(profile -> playerActionMessage("Player setisland", profile)));
                }
            })
            .exceptionally(error -> {
                message(sender, adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + islandTarget);
                return null;
            });
    }

    private boolean handleAdminMessageCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || !ADMIN_RUNTIME_TARGETS.contains(args[1].toLowerCase(Locale.ROOT))) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        String targetMode = args[1].toLowerCase(Locale.ROOT);
        int messageStart = targetMode.equals("all") ? 2 : 3;
        if (args.length <= messageStart) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        String message = joined(args, messageStart).trim();
        if (message.isBlank()) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        resolveAdminRuntimeTargetUuids(sender, targetMode, targetMode.equals("all") ? "all" : args[2]).thenAccept(targetUuids ->
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> {
                List<Player> recipients = onlinePlayers(targetUuids);
                if (recipients.isEmpty()) {
                    sender.sendMessage(adminText("admin-command-runtime-no-online-targets", "온라인 대상 플레이어가 없습니다."));
                    return;
                }
                Component component = messages == null ? Component.text(message) : messages.componentText(message);
                recipients.forEach(player -> player.sendMessage(component));
                auditAdminRuntimeAction(sender, "message." + targetMode, targetMode.equals("all") ? "all" : args[2], recipients.size(), false);
                sender.sendMessage(adminText("admin-command-runtime-message-sent-prefix", "Admin message sent recipients=") + recipients.size());
            })
        );
        return true;
    }

    private boolean handleAdminTitleCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || !ADMIN_RUNTIME_TARGETS.contains(args[1].toLowerCase(Locale.ROOT))) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        String targetMode = args[1].toLowerCase(Locale.ROOT);
        int titleStart = targetMode.equals("all") ? 2 : 3;
        if (args.length <= titleStart) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        TitlePayload payload = titlePayload(args, titleStart);
        if (payload.title().isBlank()) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        resolveAdminRuntimeTargetUuids(sender, targetMode, targetMode.equals("all") ? "all" : args[2]).thenAccept(targetUuids ->
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> {
                List<Player> recipients = onlinePlayers(targetUuids);
                if (recipients.isEmpty()) {
                    sender.sendMessage(adminText("admin-command-runtime-no-online-targets", "온라인 대상 플레이어가 없습니다."));
                    return;
                }
                Component titleText = messages == null ? Component.text(payload.title()) : messages.componentText(payload.title());
                Component subtitleText = messages == null ? Component.text(payload.subtitle()) : messages.componentText(payload.subtitle());
                Title title = Title.title(titleText, subtitleText);
                recipients.forEach(player -> player.showTitle(title));
                auditAdminRuntimeAction(sender, "title." + targetMode, targetMode.equals("all") ? "all" : args[2], recipients.size(), false);
                sender.sendMessage(adminText("admin-command-runtime-title-sent-prefix", "Admin title sent recipients=") + recipients.size());
            })
        );
        return true;
    }

    private boolean handleAdminCmdCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || !ADMIN_RUNTIME_TARGETS.contains(args[1].toLowerCase(Locale.ROOT))) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        if (!sender.hasPermission("cloudislands.admin.cmd")) {
            sender.sendMessage(adminText("admin-command-no-permission", "권한이 없습니다."));
            return true;
        }
        if (!adminCommandDispatchEnabled()) {
            sender.sendMessage(adminText("admin-command-cmd-disabled", "관리자 명령 실행은 config-v2/security.yml admin-command-dispatch.enabled=false 로 차단되어 있습니다."));
            return true;
        }
        if (!confirmed(args)) {
            sendAdminRuntimeCommandUsage(sender);
            sender.sendMessage(adminText("admin-command-cmd-confirm-required", "관리자 명령 실행은 --confirm 이 필요합니다."));
            return true;
        }
        String targetMode = args[1].toLowerCase(Locale.ROOT);
        int commandStart = targetMode.equals("all") ? 2 : 3;
        if (args.length <= commandStart) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        String command = joinedExcludingTrailingConfirm(args, commandStart).trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isBlank()) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        String commandToRun = command;
        resolveAdminRuntimeTargetUuids(sender, targetMode, targetMode.equals("all") ? "all" : args[2]).thenAccept(targetUuids ->
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> {
                List<Player> recipients = onlinePlayers(targetUuids);
                if (recipients.isEmpty()) {
                    sender.sendMessage(adminText("admin-command-runtime-no-online-targets", "온라인 대상 플레이어가 없습니다."));
                    return;
                }
                for (Player player : recipients) {
                    String expanded = commandToRun
                        .replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString());
                    agent.plugin().getServer().dispatchCommand(agent.plugin().getServer().getConsoleSender(), expanded);
                }
                auditAdminRuntimeAction(sender, "cmd." + targetMode, targetMode.equals("all") ? "all" : args[2], recipients.size(), true);
                sender.sendMessage(adminText("admin-command-runtime-cmd-sent-prefix", "Admin command dispatched recipients=") + recipients.size());
            })
        );
        return true;
    }

    private boolean handleAdminFlyCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || !ADMIN_RUNTIME_TARGETS.contains(args[1].toLowerCase(Locale.ROOT))) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        String targetMode = args[1].toLowerCase(Locale.ROOT);
        int stateIndex = targetMode.equals("all") ? 2 : 3;
        if (args.length <= stateIndex || !isBooleanArgument(args[stateIndex])) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        boolean allowFlight = booleanArgument(args[stateIndex], false);
        resolveAdminRuntimeTargetUuids(sender, targetMode, targetMode.equals("all") ? "all" : args[2]).thenAccept(targetUuids ->
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> {
                List<Player> recipients = onlinePlayers(targetUuids);
                if (recipients.isEmpty()) {
                    sender.sendMessage(adminText("admin-command-runtime-no-online-targets", "온라인 대상 플레이어가 없습니다."));
                    return;
                }
                AdminFlightOverrides overrides = adminFlightOverrides();
                for (Player player : recipients) {
                    if (overrides != null) {
                        overrides.set(player.getUniqueId(), allowFlight);
                    }
                    player.setAllowFlight(allowFlight);
                    if (!allowFlight && player.isFlying()) {
                        player.setFlying(false);
                    }
                }
                auditAdminRuntimeAction(sender, "fly." + targetMode, targetMode.equals("all") ? "all" : args[2], recipients.size(), false);
                sender.sendMessage(adminText("admin-command-runtime-fly-updated-prefix", "Admin fly updated recipients=") + recipients.size() + " enabled=" + allowFlight);
            })
        );
        return true;
    }

    private boolean handleAdminSpyCommand(CommandSender sender, String[] args) {
        AdminChatSpyRegistry spies = adminChatSpies();
        if (spies == null) {
            sender.sendMessage(adminText("admin-command-spy-unavailable", "관리자 채팅 감시 상태를 사용할 수 없습니다."));
            return true;
        }
        Player target;
        String mode;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sendAdminRuntimeCommandUsage(sender);
                return true;
            }
            target = player;
            mode = "toggle";
        } else if (isSpyModeArgument(args[1])) {
            if (!(sender instanceof Player player)) {
                sendAdminRuntimeCommandUsage(sender);
                return true;
            }
            target = player;
            mode = args[1];
        } else {
            target = agent.plugin().getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[1]);
                return true;
            }
            mode = args.length > 2 ? args[2] : "toggle";
        }
        if (!isSpyModeArgument(mode)) {
            sendAdminRuntimeCommandUsage(sender);
            return true;
        }
        boolean enabled = mode.equalsIgnoreCase("toggle") ? !spies.enabled(target) : booleanArgument(mode, false);
        spies.set(target.getUniqueId(), enabled);
        auditAdminSpy(sender, target, enabled);
        sender.sendMessage(adminText("admin-command-spy-updated-prefix", "Admin chat spy updated: ")
            + "target=" + target.getName()
            + " enabled=" + enabled);
        if (sender != target) {
            target.sendMessage(adminText("admin-command-spy-target-updated-prefix", "Admin chat spy enabled=") + enabled);
        }
        return true;
    }

    private CompletableFuture<List<UUID>> resolveAdminRuntimeTargetUuids(CommandSender sender, String targetMode, String target) {
        if (targetMode.equals("all")) {
            return CompletableFuture.completedFuture(agent.plugin().getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList());
        }
        if (targetMode.equals("player")) {
            return resolvePlayerUuid(sender, target).thenApply(uuid -> uuid == null ? List.of() : List.of(uuid));
        }
        return resolveIslandUuid(sender, target).thenCompose(islandId -> {
            if (islandId == null) {
                return CompletableFuture.completedFuture(List.of());
            }
            return coreApiClient.islands().listMembers(islandId).thenApply(members -> members.stream()
                .map(CoreGuiViews.MemberView::playerUuid)
                .map(this::uuidOrNull)
                .filter(uuid -> uuid != null)
                .distinct()
                .toList());
        });
    }

    private List<Player> onlinePlayers(List<UUID> targetUuids) {
        Set<UUID> seen = new HashSet<>();
        List<Player> players = new ArrayList<>();
        for (UUID uuid : targetUuids == null ? List.<UUID>of() : targetUuids) {
            if (uuid == null || !seen.add(uuid)) {
                continue;
            }
            Player player = agent.plugin().getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    private void auditAdminRuntimeAction(CommandSender sender, String action, String target, int recipientCount, boolean commandDispatch) {
        String actor = sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
        agent.plugin().getLogger().warning(
            "CloudIslands admin runtime action"
                + " action=" + action
                + " actor=" + actor
                + " target=" + target
                + " recipients=" + recipientCount
                + " commandDispatch=" + commandDispatch
        );
    }

    private boolean adminCommandDispatchEnabled() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin) {
            return plugin.runtimeConfig().security().adminCommandDispatchEnabled();
        }
        return false;
    }

    private AdminFlightOverrides adminFlightOverrides() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin) {
            return plugin.adminFlightOverrides();
        }
        return null;
    }

    private AdminChatSpyRegistry adminChatSpies() {
        if (agent.plugin() instanceof CloudIslandsPaperPlugin plugin) {
            return plugin.adminChatSpies();
        }
        return null;
    }

    private void sendAdminRuntimeCommandUsage(CommandSender sender) {
        sendCommandUsage(sender, List.of(
            "/ciadmin message player <playerUuid|playerName> <message>",
            "/ciadmin message island <islandUuid|islandName> <message>",
            "/ciadmin message all <message>",
            "/ciadmin title player <playerUuid|playerName> <title> [subtitle]",
            "/ciadmin title island <islandUuid|islandName> <title> [subtitle]",
            "/ciadmin title all <title> [subtitle]",
            "/ciadmin cmd player <playerUuid|playerName> <command> --confirm",
            "/ciadmin cmd island <islandUuid|islandName> <command> --confirm",
            "/ciadmin cmd all <command> --confirm",
            "/ciadmin fly player <playerUuid|playerName> <true|false>",
            "/ciadmin fly island <islandUuid|islandName> <true|false>",
            "/ciadmin fly all <true|false>",
            "/ciadmin spy [true|false|toggle]",
            "/ciadmin spy <player> [true|false|toggle]"
        ));
    }

    private boolean handleJobs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                AdminJobMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player));
            } else {
                run(sender, "Jobs list", coreApiClient.jobs().list().thenApply(this::jobListMessage));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("list")) {
            run(sender, "Jobs list", coreApiClient.jobs().list().thenApply(this::jobListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("recover")) {
            String recoverNodeId = args.length > 2 ? args[2] : nodeId;
            long minIdleMillis = args.length > 3 ? number(args[3], 60000L) : 60000L;
            int maxJobs = args.length > 4 ? (int) number(args[4], 20L) : 20;
            run(sender, "Jobs recover", coreApiClient.jobCommands().recover(recoverNodeId, minIdleMillis, maxJobs).thenApply(this::jobRecoveryMessage));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(adminText("admin-command-job-id-required", "작업 ID를 입력해주세요."));
            return true;
        }
        UUID jobId = uuid(sender, args[2]);
        if (jobId == null) {
            return true;
        }
        if (args[1].equalsIgnoreCase("retry")) {
            run(sender, "Job retry", coreApiClient.jobCommands().retry(jobId).thenApply(action -> jobActionMessage("retry", action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("cancel")) {
            run(sender, "Job cancel", coreApiClient.jobCommands().cancel(jobId).thenApply(action -> jobActionMessage("cancel", action)));
            return true;
        }
        sendCommandUsage(sender, List.of(
            "/ciadmin jobs list",
            "/ciadmin jobs retry <jobId>",
            "/ciadmin jobs cancel <jobId>",
            "/ciadmin jobs recover [nodeId] [minIdleMillis] [maxJobs]"
        ));
        return true;
    }

    private boolean handleRankings(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("level")) {
            int limit = args.length > 2 ? (int) number(args[2], 10L) : 10;
            run(sender, "Level rankings", coreApiClient.progression().topLevel(limit).thenApply(rankings -> rankingListMessage("Level rankings", rankings)));
            return true;
        }
        if (args[1].equalsIgnoreCase("worth") || args[1].equalsIgnoreCase("value")) {
            int limit = args.length > 2 ? (int) number(args[2], 10L) : 10;
            run(sender, "Worth rankings", coreApiClient.progression().topWorth(limit).thenApply(rankings -> rankingListMessage("Worth rankings", rankings)));
            return true;
        }
        sendCommandUsage(sender, List.of(
            "/ciadmin rankings level [limit]",
            "/ciadmin rankings worth [limit]"
        ));
        return true;
    }

    private boolean handleBonusCompatibility(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendCommandUsage(sender, List.of(
                "/ciadmin bonus <islandUuid|islandName>",
                "/ciadmin addbonus <islandUuid|islandName> <bonusKey> <delta>",
                "/ciadmin syncbonus <islandUuid|islandName>"
            ));
            return true;
        }
        if (args[0].equalsIgnoreCase("addbonus") && args.length < 4) {
            sendCommandUsage(sender, List.of("/ciadmin addbonus <islandUuid|islandName> <bonusKey> <delta>"));
            return true;
        }
        String operation = args[0].toLowerCase(Locale.ROOT);
        String bonusKey = operation.equals("addbonus") ? bonusLimitKey(args[2]) : "";
        long bonusDelta = operation.equals("addbonus") ? number(args[3], 0L) : 0L;
        resolveIslandUuid(sender, args[1]).thenAccept(islandId -> {
            if (islandId == null) {
                return;
            }
            if (operation.equals("bonus")) {
                run(sender, "Island bonus", coreApiClient.environment().limitViews(islandId).thenApply(this::bonusListMessage));
                return;
            }
            if (operation.equals("addbonus")) {
                run(sender, "Island addbonus", coreApiClient.environmentCommands().adminAddLimit(islandId, bonusKey, bonusDelta).thenApply(result -> gameplayModifierMessage("Island addbonus", result)));
                return;
            }
            run(sender, "Island syncbonus", coreApiClient.progressionCommands().adminRecalculateUpgrades(islandId).thenApply(result -> bonusSyncMessage("Island syncbonus", result)));
        }).exceptionally(error -> {
            message(sender, adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + args[1]);
            return null;
        });
        return true;
    }

    private boolean handleRoute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                AdminRouteMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player));
            } else {
                sendRouteCommandUsage(sender);
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("debug")) {
            if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
                run(sender, "Route debug", coreApiClient.adminRoutes().debug(new UUID(0L, 0L)).thenApply(this::routeDebugMessage));
                return true;
            }
            resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
                if (playerUuid == null) {
                    return;
                }
                run(sender, "Route debug", coreApiClient.adminRoutes().debug(playerUuid).thenApply(this::routeDebugMessage));
            }).exceptionally(error -> {
                message(sender, adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[2]);
                return null;
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("ticket") || args[1].equalsIgnoreCase("tickets")) {
            if (args.length < 3) {
                sender.sendMessage(adminText("admin-command-route-ticket-target-required", "티켓 UUID, 플레이어 UUID 또는 플레이어 이름을 입력해주세요."));
                return true;
            }
            UUID ticketId = uuidOrNull(args[2]);
            if (ticketId != null) {
                run(sender, "Route ticket", coreApiClient.adminRoutes().ticket(ticketId).thenApply(this::routeTicketMessage));
            } else {
                resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
                    if (playerUuid == null) {
                        return;
                    }
                    run(sender, "Route ticket", coreApiClient.adminRoutes().ticketForPlayer(playerUuid).thenApply(this::routeTicketMessage));
                }).exceptionally(error -> {
                    message(sender, adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[2]);
                    return null;
                });
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            if (args.length < 3) {
                sender.sendMessage(adminText("admin-command-player-target-required", "플레이어 이름 또는 UUID를 입력해주세요."));
                return true;
            }
            UUID ticketId = args.length > 3 ? uuid(sender, args[3]) : new UUID(0L, 0L);
            if (ticketId == null) {
                return true;
            }
            resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
                if (playerUuid == null) {
                    return;
                }
                run(sender, "Route clear", coreApiClient.adminRoutes().clear(playerUuid, ticketId).thenApply(this::routeClearMessage));
            }).exceptionally(error -> {
                message(sender, adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[2]);
                return null;
            });
            return true;
        }
        sendRouteCommandUsage(sender);
        return true;
    }

    private boolean handleBlockValues(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Block values", coreApiClient.blockValues().list().thenApply(this::blockValueListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("search")) {
            if (args.length < 3) {
                sender.sendMessage(adminText("admin-command-block-values-search-usage", "사용법: /ciadmin block-values search <query> [limit]"));
                return true;
            }
            String query = args[2];
            int limit = args.length > 3 ? (int) number(args[3], 10L) : 10;
            run(sender, "Block value search", coreApiClient.blockValues().list().thenApply(values -> blockValueSearchMessage(query, values, limit)));
            return true;
        }
        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 6) {
                sender.sendMessage(adminText("admin-command-block-values-set-usage", "사용법: /ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>"));
                return true;
            }
            UUID actorUuid = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
            run(sender, "Block value set", coreApiClient.blockValueCommands().set(actorUuid, args[2], args[3], number(args[4], 0L), number(args[5], 0L)).thenApply(result -> blockValueActionMessage("Block value set", args[2], result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("reload")) {
            run(sender, "Block values reload", coreApiClient.adminMaintenance().reload().thenApply(result -> maintenanceMessage("Block values reload", result)));
            return true;
        }
        sendCommandUsage(sender, List.of(
            "/ciadmin block-values list",
            "/ciadmin block-values search <query> [limit]",
            "/ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>",
            "/ciadmin block-values reload"
        ));
        return true;
    }

    private boolean handleGameplayModifier(CommandSender sender, String[] args) {
        UUID actorUuid = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        if (args[0].equalsIgnoreCase("setblockamount")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of("/ciadmin setblockamount <island> <materialKey> <amount>"));
                return true;
            }
            resolveIslandUuid(sender, args[1]).thenAccept(islandId -> {
                if (islandId == null) {
                    return;
                }
                run(sender, "Set block amount", coreApiClient.environmentCommands().setLimit(islandId, actorUuid, GameplayParityPolicy.blockAmountLimitKey(args[2]), number(args[3], 0L)).thenApply(result -> gameplayModifierMessage("Set block amount", result)));
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("seteffect")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of("/ciadmin seteffect <island> <effectKey> <amplifier>"));
                return true;
            }
            resolveIslandUuid(sender, args[1]).thenAccept(islandId -> {
                if (islandId == null) {
                    return;
                }
                run(sender, "Set island effect", coreApiClient.environmentCommands().setLimit(islandId, actorUuid, "EFFECT:" + normalizeGameplayKey(args[2]), number(args[3], 0L)).thenApply(result -> gameplayModifierMessage("Set island effect", result)));
            });
            return true;
        }
        if (rateModifierCommand(args[0])) {
            if (args.length < 3) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin setcropgrowth <island> <percent>",
                    "/ciadmin setmobdrops <island> <percent>",
                    "/ciadmin setspawnerrates <island> <percent>"
                ));
                return true;
            }
            resolveIslandUuid(sender, args[1]).thenAccept(islandId -> {
                if (islandId == null) {
                    return;
                }
                run(sender, gameplayModifierLabel(args[0]), coreApiClient.environmentCommands().setLimit(islandId, actorUuid, gameplayModifierLimitKey(args[0]), number(args[2], 100L)).thenApply(result -> gameplayModifierMessage(gameplayModifierLabel(args[0]), result)));
            });
            return true;
        }
        return false;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        SpawnUpdateResult result;
        if (args.length == 1 && sender instanceof Player player) {
            result = worldSpawnGateway().setFromPlayer(player);
        } else {
            if (args.length < 5) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin setspawn",
                    "/ciadmin setspawn <world> <x> <y> <z> [yaw]"
                ));
                return true;
            }
            Double x = decimalArgument(args[2]);
            Double y = decimalArgument(args[3]);
            Double z = decimalArgument(args[4]);
            Double yaw = args.length > 5 ? decimalArgument(args[5]) : 0.0D;
            if (x == null || y == null || z == null || yaw == null) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin setspawn",
                    "/ciadmin setspawn <world> <x> <y> <z> [yaw]"
                ));
                return true;
            }
            result = worldSpawnGateway().set(args[1], x, y, z, yaw.floatValue());
        }
        if (!result.worldFound()) {
            sender.sendMessage(adminText("admin-command-world-not-found", "월드를 찾지 못했습니다."));
            return true;
        }
        auditAdminSetSpawn(sender, result);
        sender.sendMessage(adminText("admin-command-setspawn-updated-prefix", "Admin spawn updated: ")
            + "world=" + result.worldName()
            + " x=" + formatCoordinate(result.x())
            + " y=" + formatCoordinate(result.y())
            + " z=" + formatCoordinate(result.z())
            + " yaw=" + formatCoordinate(result.yaw())
            + " accepted=" + result.accepted());
        return true;
    }

    private boolean handleOpenMenu(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendCommandUsage(sender, List.of("/ciadmin openmenu <player> <menuId>"));
            return true;
        }
        Player target = agent.plugin().getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[1]);
            return true;
        }
        String menuId = normalizeMenuId(args[2]);
        if (!ADMIN_OPEN_MENU_IDS.contains(menuId)) {
            sender.sendMessage(adminText("admin-command-openmenu-unsupported-prefix", "지원하지 않는 메뉴입니다: ")
                + args[2]
                + adminText("admin-command-openmenu-supported-prefix", " supported=")
                + String.join(",", ADMIN_OPEN_MENU_IDS));
            auditAdminOpenMenu(sender, target, args[2], false);
            return true;
        }
        openAdminRequestedMenu(target, menuId);
        auditAdminOpenMenu(sender, target, menuId, true);
        sender.sendMessage(adminText("admin-command-openmenu-opened-prefix", "Admin menu opened: ")
            + "target=" + target.getName()
            + " menu=" + menuId);
        return true;
    }

    private void openAdminRequestedMenu(Player target, String menuId) {
        MessageRenderer targetMessages = messagesFor(target);
        switch (menuId) {
            case "island.main" -> IslandMainMenu.open(target, targetMessages);
            case "island.create" -> IslandCreateMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "island.visit" -> IslandVisitMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "island.chat" -> IslandChatMenu.open(target, targetMessages);
            case "island.my-islands" -> IslandMyIslandsMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.audit" -> AdminAuditMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.dashboard" -> AdminDashboardMenu.open(target, targetMessages);
            case "admin.events" -> AdminEventMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.metrics" -> AdminMetricsMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.node" -> AdminNodeListMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.storage" -> AdminStorageMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.templates" -> AdminTemplateMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.route" -> AdminRouteMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.jobs" -> AdminJobMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            case "admin.reviews" -> AdminReviewModerationMenu.open(agent.plugin(), coreApiClient, target, targetMessages, 36);
            case "admin.migration" -> AdminMigrationMenu.open(agent.plugin(), coreApiClient, target, targetMessages);
            default -> throw new IllegalArgumentException("Unsupported menu id: " + menuId);
        }
    }

    private String normalizeMenuId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private boolean handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            if (sender instanceof Player player) {
                AdminTemplateMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player));
                return true;
            }
            run(sender, "Template list", coreApiClient.templates().list().thenApply(this::templateListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("import")) {
            if (args.length < 3) {
                sendTemplateCommandUsage(sender);
                return true;
            }
            String templateId = normalizeTemplateId(args[2]);
            String displayName = args.length > 3 ? joined(args, 3) : args[2];
            run(sender, "Template import", coreApiClient.templateCommands().upsert(templateId, displayName, false, "").thenApply(template -> templateActionMessage("Template import", templateId, template) + " / " + templateValidationStatus(template)));
            return true;
        }
        if (args[1].equalsIgnoreCase("import-bundle")) {
            if (args.length < 5) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin templates import-bundle <id> <bundlePath> <checksum> [sizeBytes] [displayName]"
                ));
                return true;
            }
            String templateId = normalizeTemplateId(args[2]);
            String bundlePath = args[3];
            String checksum = args[4];
            long bundleSizeBytes = args.length > 5 ? number(args[5], 0L) : 0L;
            String displayName = args.length > 6 ? joined(args, 6) : templateId;
            TemplateView template = templateBundleView(templateId, displayName, bundlePath, checksum, bundleSizeBytes);
            run(sender, "Template import bundle", coreApiClient.templateCommands().importBundle(template).thenApply(result -> templateActionMessage("Template import bundle", templateId, result) + " / " + templateValidationStatus(result)));
            return true;
        }
        if (args[1].equalsIgnoreCase("upsert")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin templates upsert <id> <name> [enabled|disabled] [minNodeVersion]"
                ));
                return true;
            }
            boolean enabled = args.length < 5 || booleanArgument(args[4], false);
            String minNodeVersion = args.length > 5 ? args[5] : "";
            run(sender, "Template upsert", coreApiClient.templateCommands().upsert(args[2], args[3], enabled, minNodeVersion).thenApply(template -> templateActionMessage("Template upsert", args[2], template)));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(adminText("admin-command-template-id-required", "템플릿 ID를 입력해주세요."));
            return true;
        }
        if (args[1].equalsIgnoreCase("preview")) {
            run(sender, "Template preview", coreApiClient.templates().list().thenApply(templates -> templatePreviewMessage(args[2], templates)));
            return true;
        }
        if (args[1].equalsIgnoreCase("validate")) {
            run(sender, "Template validate", coreApiClient.templates().list().thenApply(templates -> templateValidateMessage(args[2], templates)));
            return true;
        }
        if (args[1].equalsIgnoreCase("verify-bundle") || args[1].equalsIgnoreCase("verify")) {
            run(sender, "Template verify bundle", coreApiClient.templateCommands().verifyBundle(args[2]).thenApply(this::templateBundleVerificationMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("seticon")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin templates seticon <name> <material> [customModelData]"
                ));
                return true;
            }
            run(sender, "Template set icon", coreApiClient.templates().get(args[2]).thenCompose(template -> {
                int customModelData = args.length > 4 ? (int) number(args[4], 0L) : template.iconCustomModelData();
                return coreApiClient.templateCommands().upsert(templateWithCatalogFields(template, template.requiredPermission(), args[3], customModelData, template.creationCost()));
            }).thenApply(template -> templateActionMessage("Template set icon", args[2], template)
                + adminText("admin-command-template-icon-prefix", " icon=") + template.iconMaterial()
                + (template.iconCustomModelData() <= 0 ? "" : adminText("admin-command-template-model-data-prefix", " modelData=") + template.iconCustomModelData())));
            return true;
        }
        if (args[1].equalsIgnoreCase("setcost")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin templates setcost <name> <amount>"
                ));
                return true;
            }
            run(sender, "Template set cost", coreApiClient.templates().get(args[2]).thenCompose(template -> coreApiClient.templateCommands().upsert(templateWithCatalogFields(template, template.requiredPermission(), template.iconMaterial(), template.iconCustomModelData(), args[3]))).thenApply(template -> templateActionMessage("Template set cost", args[2], template)
                + adminText("admin-command-template-cost-prefix", " cost=") + template.creationCost()));
            return true;
        }
        if (args[1].equalsIgnoreCase("setpermission")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin templates setpermission <name> <permission>"
                ));
                return true;
            }
            run(sender, "Template set permission", coreApiClient.templates().get(args[2]).thenCompose(template -> coreApiClient.templateCommands().upsert(templateWithCatalogFields(template, templatePermissionArgument(args[3]), template.iconMaterial(), template.iconCustomModelData(), template.creationCost()))).thenApply(template -> templateActionMessage("Template set permission", args[2], template)
                + adminText("admin-command-template-permission-prefix", " permission=") + (template.requiredPermission().isBlank() ? "none" : template.requiredPermission())));
            return true;
        }
        if (args[1].equalsIgnoreCase("enable")) {
            run(sender, "Template enable", coreApiClient.templateCommands().enable(args[2]).thenApply(template -> templateActionMessage("Template enable", args[2], template)));
            return true;
        }
        if (args[1].equalsIgnoreCase("disable")) {
            run(sender, "Template disable", coreApiClient.templateCommands().disable(args[2]).thenApply(template -> templateActionMessage("Template disable", args[2], template)));
            return true;
        }
        if (args[1].equalsIgnoreCase("delete")) {
            boolean confirm = args.length > 3 && (args[3].equalsIgnoreCase("--confirm") || args[3].equalsIgnoreCase("confirm") || booleanArgument(args[3], false));
            if (!confirm) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin templates delete <id> --confirm"
                ));
                return true;
            }
            run(sender, "Template delete", coreApiClient.templateCommands().delete(args[2], true).thenApply(accepted -> templateBooleanActionMessage("Template delete", args[2], accepted)));
            return true;
        }
        if (args[1].equalsIgnoreCase("reorder")) {
            if (args.length < 4) {
                sendCommandUsage(sender, List.of(
                    "/ciadmin templates reorder <id> <sortOrder>"
                ));
                return true;
            }
            int sortOrder = (int) number(args[3], 0L);
            run(sender, "Template reorder", coreApiClient.templateCommands().reorder(args[2], sortOrder).thenApply(template -> templateActionMessage("Template reorder", args[2], template) + adminText("admin-command-template-sort-prefix", " sort=") + template.sortOrder()));
            return true;
        }
        sendTemplateCommandUsage(sender);
        return true;
    }

    private void sendTemplateCommandUsage(CommandSender sender) {
        sendCommandUsage(sender, List.of(
            "/ciadmin templates list",
            "/ciadmin templates import <name>",
            "/ciadmin templates import-bundle <id> <bundlePath> <checksum> [sizeBytes] [displayName]",
            "/ciadmin templates upsert <id> <name> [enabled|disabled] [minNodeVersion]",
            "/ciadmin templates seticon <name> <material>",
            "/ciadmin templates setcost <name> <amount>",
            "/ciadmin templates setpermission <name> <permission>",
            "/ciadmin templates enable <id>",
            "/ciadmin templates disable <id>",
            "/ciadmin templates preview <id>",
            "/ciadmin templates validate <id>",
            "/ciadmin templates verify-bundle <id>",
            "/ciadmin templates delete <id> --confirm",
            "/ciadmin templates reorder <id> <sortOrder>"
        ));
    }

    private boolean superiorSkyblock2MigrationEnabled() {
        return superiorSkyblock2MigrationEnabled;
    }

    private void sendIslandCommandUsage(CommandSender sender) {
        sendCommandUsage(sender, List.of(
            "/ciadmin island info <islandUuid|islandName>",
            "/ciadmin island where <playerUuid|playerName|islandUuid|islandName>",
            "/ciadmin island inspect <playerUuid|playerName|islandUuid|islandName>",
            "/ciadmin island inspect <playerUuid|playerName|islandUuid|islandName> --json",
            "/ciadmin island visitor-stats <islandUuid|islandName>",
            "/ciadmin island reviews [limit]",
            "/ciadmin island moderate-review <islandUuid|islandName> <reviewerUuid|reviewerName> <VISIBLE|REPORTED|HIDDEN> [note]",
            "/ciadmin island tp <islandUuid|islandName>",
            "/ciadmin island activate <islandUuid|islandName>",
            "/ciadmin island deactivate <islandUuid|islandName>",
            "/ciadmin island migrate <islandUuid|islandName> <node>",
            "/ciadmin island save <islandUuid|islandName> [reason]",
            "/ciadmin island snapshot <islandUuid|islandName> [reason]",
            "/ciadmin island snapshots <islandUuid|islandName> [limit]",
            "/ciadmin island restore <islandUuid|islandName> <snapshotNo> --confirm",
            "/ciadmin island rollback <islandUuid|islandName> <snapshotNo> --confirm",
            "/ciadmin island bulk-restore <snapshotNo> <islandUuid|islandName>... --confirm",
            "/ciadmin island bank deposit <islandUuid|islandName> <amount>",
            "/ciadmin island bank withdraw <islandUuid|islandName> <amount>",
            "/ciadmin island member add <islandUuid|islandName> <playerUuid|playerName> [role]",
            "/ciadmin island member kick <islandUuid|islandName> <playerUuid|playerName>",
            "/ciadmin island member promote <islandUuid|islandName> <playerUuid|playerName>",
            "/ciadmin island member demote <islandUuid|islandName> <playerUuid|playerName>",
            "/ciadmin island member setleader <islandUuid|islandName> <playerUuid|playerName>",
            "/ciadmin island join <islandUuid|islandName> [role]",
            "/ciadmin island rankup <islandUuid|islandName> <upgradeKey>",
            "/ciadmin island setchestrow <islandUuid|islandName> <rows>",
            "/ciadmin island rename <islandUuid|islandName> <name>",
            "/ciadmin island setbiome <islandUuid|islandName> <biomeKey>",
            "/ciadmin island delhome <islandUuid|islandName> <home>",
            "/ciadmin island delwarp <islandUuid|islandName> <warp>",
            "/ciadmin island setgenerator <islandUuid|islandName> <generatorKey> <level>",
            "/ciadmin island addgenerator <islandUuid|islandName> <levels> [generatorKey]",
            "/ciadmin island cleargenerator <islandUuid|islandName>",
            "/ciadmin island setbanklimit <islandUuid|islandName> <value>",
            "/ciadmin island addbanklimit <islandUuid|islandName> <delta>",
            "/ciadmin island setentitylimit <islandUuid|islandName> [entityType] <value>",
            "/ciadmin island addentitylimit <islandUuid|islandName> [entityType] <delta>",
            "/ciadmin island removeentitylimit <islandUuid|islandName> [entityType]",
            "/ciadmin island setteamlimit <islandUuid|islandName> <value>",
            "/ciadmin island addteamlimit <islandUuid|islandName> <delta>",
            "/ciadmin island setrolelimit <islandUuid|islandName> <role> <value>",
            "/ciadmin island setwarpslimit <islandUuid|islandName> <value>",
            "/ciadmin island addwarpslimit <islandUuid|islandName> <delta>",
            "/ciadmin island setsize <islandUuid|islandName> <value>",
            "/ciadmin island addsize <islandUuid|islandName> <delta>",
            "/ciadmin island setblocklimit <islandUuid|islandName> <materialKey> <value>",
            "/ciadmin island addblocklimit <islandUuid|islandName> <materialKey> <delta>",
            "/ciadmin island removeblocklimit <islandUuid|islandName> <materialKey>",
            "/ciadmin island ignore <islandUuid|islandName>",
            "/ciadmin island unignore <islandUuid|islandName>",
            "/ciadmin island setpermission <islandUuid|islandName> <role> <permission> <true|false>",
            "/ciadmin island resetpermissions <islandUuid|islandName> <role>",
            "/ciadmin island quarantine <islandUuid|islandName> [reason]",
            "/ciadmin island recover <islandUuid|islandName> [reason]",
            "/ciadmin island repair <islandUuid|islandName> [reason]",
            "/ciadmin island delete <islandUuid|islandName> --confirm"
        ));
    }

    private void sendRouteCommandUsage(CommandSender sender) {
        sendCommandUsage(sender, List.of(
            "/ciadmin route debug [all|playerUuid|playerName]",
            "/ciadmin route ticket <ticketUuid|playerUuid|playerName>",
            "/ciadmin route tickets <playerUuid|playerName>",
            "/ciadmin route clear <playerUuid|playerName> [ticketUuid]"
        ));
    }

    private void sendCommandUsage(CommandSender sender, List<String> commands) {
        List<String> commandNames = commands.stream()
            .map(AdminCommandBackend::usageCommandName)
            .toList();
        CommandListPolicy.Page commandPage = CommandListPolicy.page(commandNames, 1, "ciadmin command list");
        String title = adminText("admin-command-subcommand-list-title", "CloudIslands 관리자 명령어 목록");
        sender.sendMessage(title.replace(CommandListPolicy.HEADER_SUFFIX, "").trim() + " " + commandPage.page() + "/" + commandPage.pages() + " commands=" + commandPage.rangeSummary() + CommandListPolicy.HEADER_SUFFIX);
        for (String line : CommandListPolicy.displayLines(commandPage)) {
            sender.sendMessage(line);
        }
    }

    private static String usageCommandName(String command) {
        String value = command == null ? "" : command.trim();
        while (value.startsWith("/")) {
            value = value.substring(1).trim();
        }
        return value;
    }

    private List<String> rootCommands() {
        if (superiorSkyblock2MigrationEnabled()) {
            return ROOT_COMMANDS;
        }
        return ROOT_COMMANDS.stream()
            .filter(command -> !command.equals("migrate-superiorskyblock2"))
            .toList();
    }

    private List<String> helpCommands() {
        List<String> baseCommands = HELP_COMMANDS.stream()
            .filter(command -> !isMigrationCommandHelp(command))
            .toList();
        if (!superiorSkyblock2MigrationEnabled()) {
            return baseCommands;
        }
        List<String> commands = new ArrayList<>(baseCommands);
        commands.addAll(MIGRATION_HELP_COMMANDS);
        return commands;
    }

    private static boolean isMigrationCommandHelp(String command) {
        return command != null && command.startsWith("ciadmin migrate-superiorskyblock2");
    }

    private void routeAdminTeleport(Player player, UUID islandId) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        coreApiClient.adminIslandTeleport(playerSession.playerUuid(), islandId)
            .thenAccept(ticket -> routeTicket(playerSession, ticket, adminText("admin-command-route-failed", "관리자 섬 이동에 실패했습니다."), 0))
            .exceptionally(error -> {
                message(playerSession, routeFailureMessage(error, adminText("admin-command-route-failed", "관리자 섬 이동에 실패했습니다.")));
                return null;
            });
    }

    private String routeFailureMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CoreApiException coreError) {
                return switch (coreError.code()) {
                    case "ISLAND_LOADING_FAILED" -> adminText("admin-command-route-island-loading", "섬을 아직 이동할 수 있는 상태가 아닙니다.");
                    case "ISLAND_NOT_FOUND" -> adminText("admin-command-route-island-not-found", "해당 섬을 찾을 수 없습니다.");
                    default -> fallback;
                };
            }
            if (current instanceof IOException) {
                return adminText("admin-command-route-service-maintenance", "현재 섬 서비스 일부 기능이 점검 중입니다.");
            }
            current = current.getCause();
        }
        return fallback;
    }

    private void routeTicket(PlayerConnectionSession playerSession, RouteTicket ticket, String failureMessage, int attempt) {
        runIfCurrent(playerSession,
            _player -> routeTicketCurrent(playerSession, ticket, failureMessage, attempt),
            () -> clearFailedRoute(ticket, "PLAYER_SESSION_REPLACED"));
    }

    private void routeTicketCurrent(PlayerConnectionSession playerSession, RouteTicket ticket, String failureMessage, int attempt) {
        if (ticket.state().name().equals("READY")) {
            publishAndConnect(playerSession, ticket, failureMessage);
            return;
        }
        if (attempt >= routeWaitSeconds) {
            message(playerSession, failureMessage);
            return;
        }
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() ->
            coreApiClient.routingCommands().routeTicketStatus(ticket).thenAccept(status -> {
                if (status.isPresent()) {
                    routeTicket(playerSession, status.get(), failureMessage, attempt + 1);
                } else {
                    message(playerSession, failureMessage);
                }
            }).exceptionally(error -> {
                message(playerSession, routeFailureMessage(error, failureMessage));
                return null;
            }));
    }

    private void publishAndConnect(PlayerConnectionSession playerSession, RouteTicket ticket, String failureMessage) {
        runIfCurrent(playerSession, _player -> coreApiClient.routingCommands().publishRouteSession(ticket)
                .thenRun(() -> connectWithTicket(playerSession, ticket, ticket.payload().getOrDefault("targetServerName", ticket.targetNode())))
                .exceptionally(error -> {
                    clearFailedRoute(ticket, "SESSION_PUBLISH_FAILED");
                    message(playerSession, routeFailureMessage(error, failureMessage));
                    return null;
                }),
            () -> clearFailedRoute(ticket, "PLAYER_SESSION_REPLACED"));
    }

    private void connectWithTicket(PlayerConnectionSession playerSession, RouteTicket ticket, String targetServerName) {
        runIfCurrent(playerSession, player -> {
            if (targetServerName == null || targetServerName.isBlank()) {
                clearFailedRoute(ticket, "TARGET_SERVER_NOT_FOUND");
                player.sendMessage(adminText("admin-command-route-target-missing", "섬 이동 경로를 찾을 수 없습니다."));
                return;
            }
            if (!agent.plugin().getServer().getMessenger().isOutgoingChannelRegistered(agent.plugin(), "BungeeCord")) {
                clearFailedRoute(ticket, "BUNGEE_CONNECT_UNAVAILABLE");
                player.sendMessage(adminText("admin-command-route-request-failed", "섬 이동 요청을 만들 수 없습니다."));
                return;
            }
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeUTF("Connect");
                output.writeUTF(targetServerName);
                player.sendPluginMessage(agent.plugin(), "BungeeCord", bytes.toByteArray());
                player.sendMessage(adminText("admin-command-route-connecting", "섬으로 이동합니다."));
            } catch (IOException | RuntimeException exception) {
                clearFailedRoute(ticket, "PLUGIN_MESSAGE_FAILED");
                player.sendMessage(adminText("admin-command-route-request-failed", "섬 이동 요청을 만들 수 없습니다."));
            }
        }, () -> clearFailedRoute(ticket, "PLAYER_SESSION_REPLACED"));
    }

    private void message(PlayerConnectionSession playerSession, String text) {
        runIfCurrent(playerSession, player -> player.sendMessage(text), () -> { });
    }

    private void runIfCurrent(PlayerConnectionSession playerSession, java.util.function.Consumer<Player> action, Runnable staleAction) {
        PaperSchedulers.run(agent.plugin(), () -> {
            Player activePlayer = agent.plugin().getServer().getPlayer(playerSession.playerUuid());
            if (playerSession.isCurrent(activePlayer)) {
                action.accept(activePlayer);
            } else {
                staleAction.run();
            }
        });
    }

    private void clearFailedRoute(RouteTicket ticket) {
        clearFailedRoute(ticket, "PLUGIN_MESSAGE_FAILED");
    }

    private void clearFailedRoute(RouteTicket ticket, String reason) {
        coreApiClient.routingCommands().clearRoute(ticket, reason).exceptionally(error -> null);
    }

    private void run(CommandSender sender, String action, CompletableFuture<? extends CharSequence> future) {
        PlayerConnectionSession playerSession = sender instanceof Player player
            ? PlayerConnectionSession.capture(player)
            : null;
        future.thenAccept(body -> {
                String text = body == null ? "" : body.toString();
                deliverAsyncAdminMessage(sender, playerSession, action + adminText("admin-command-action-complete", " 완료") + (text.isBlank() ? "" : ": " + text));
            })
            .exceptionally(error -> {
                deliverAsyncAdminMessage(sender, playerSession, action + adminText("admin-command-action-failed", " 실패"));
                return null;
        });
    }

    private void deliverAsyncAdminMessage(CommandSender sender, PlayerConnectionSession playerSession, String text) {
        if (playerSession != null) {
            message(playerSession, text);
            return;
        }
        message(sender, text);
    }

    private record DiagnosticSection(String content) {
        private DiagnosticSection {
            content = content == null ? "" : content;
        }
    }

    private record DiagnosticExportContext(Path directory, String nodeId, String agentRole, String pluginVersion, int onlinePlayers) {
    }

    private record IntegrationReportFiles(Path json, Path markdown) {
    }

    private record TitlePayload(String title, String subtitle) {
    }

    private record BulkRestoreEntry(String requested, UUID islandId, boolean accepted, String code) {
        private BulkRestoreEntry {
            requested = requested == null ? "" : requested;
            code = code == null ? "" : code;
        }
    }

    private String storageStatusMessage(AdminStorageStatusView status) {
        if (status.nodes().isEmpty()) {
            return adminText("admin-command-storage-no-node", "Storage status: registered node 없음");
        }
        List<String> entries = new ArrayList<>();
        for (AdminStorageStatusView.NodeView node : status.nodes()) {
            if (!node.nodeId().isBlank()) {
                entries.add(node.nodeId() + "=" + (node.storageAvailable() ? "OK" : "DOWN") + storageMetricSuffix(node));
            }
        }
        return entries.isEmpty()
            ? adminText("admin-command-storage-no-node", "Storage status: registered node 없음")
            : adminText("admin-command-storage-status-prefix", "Storage status: ") + String.join(", ", entries) + adminText("admin-command-storage-unavailable-prefix", " / unavailable=") + status.unavailableCount();
    }

    private String storageMetricSuffix(AdminStorageStatusView.NodeView node) {
        if (node.backend().isBlank()
            && node.totalFailures() == 0L
            && !node.primaryDegraded()
            && node.uploadSeconds() == 0.0D
            && node.downloadSeconds() == 0.0D) {
            return "";
        }
        return adminText("admin-command-storage-metric-failures-prefix", "(failures=") + node.totalFailures()
            + ", primaryDegraded=" + node.primaryDegraded()
            + adminText("admin-command-storage-metric-up-prefix", ", up=") + seconds(node.uploadSeconds()) + "s"
            + adminText("admin-command-storage-metric-down-prefix", ", down=") + seconds(node.downloadSeconds()) + "s"
            + adminText("admin-command-storage-bundle-policy-prefix", ", bundle=") + "portable"
            + adminText("admin-command-storage-manifest-policy-prefix", ", manifest=") + "manifest.json+checksums.sha256"
            + adminText("admin-command-storage-restore-policy-prefix", ", restore=") + "verify-manifest-checksum)";
    }

    private CompletableFuture<CharSequence> storageVerifyMessage(UUID islandId) {
        CompletableFuture<AdminStorageStatusView> storage = coreApiClient.adminStorage().status();
        CompletableFuture<AdminIslandRuntimeView> runtime = coreApiClient.adminIslands().runtime(islandId);
        CompletableFuture<List<CoreGuiViews.SnapshotView>> snapshots = coreApiClient.snapshots().listSnapshots(islandId, 5);
        return CompletableFuture.allOf(storage, runtime, snapshots)
            .thenApply(ignored -> (CharSequence) storageVerifyMessage(islandId, storage.join(), runtime.join(), snapshots.join()));
    }

    private String storageVerifyMessage(UUID islandId, AdminStorageStatusView status, AdminIslandRuntimeView runtime, List<CoreGuiViews.SnapshotView> snapshots) {
        return adminText("admin-command-storage-verify-prefix", "Storage verify: island=") + shortId(islandId.toString())
            + " | " + runtimeInfoMessage(runtime)
            + " | " + snapshotStorageSummary(snapshots)
            + " | " + storageStatusMessage(status);
    }

    private String snapshotStorageSummary(List<CoreGuiViews.SnapshotView> snapshots) {
        if (snapshots.isEmpty()) {
            return adminText("admin-command-storage-verify-snapshot-empty", "snapshot=none");
        }
        CoreGuiViews.SnapshotView latest = snapshots.stream()
            .max((left, right) -> Long.compare(left.snapshotNo(), right.snapshotNo()))
            .orElse(snapshots.get(0));
        return adminText("admin-command-storage-verify-snapshot-prefix", "snapshot=#") + latest.snapshotNo()
            + (latest.storagePath().isBlank() ? adminText("admin-command-storage-verify-path-missing", " path=missing") : adminText("admin-command-snapshot-path-prefix", " path=") + latest.storagePath())
            + (latest.checksum().isBlank() ? adminText("admin-command-storage-verify-checksum-missing", " checksum=missing") : adminText("admin-command-snapshot-checksum-prefix", " checksum=") + shortChecksum(latest.checksum()))
            + adminText("admin-command-storage-verify-snapshot-count-prefix", " checked=") + snapshots.size();
    }

    private String jobListMessage(List<JobView> jobs) {
        if (jobs.isEmpty()) {
            return adminText("admin-command-jobs-empty", "Jobs: empty");
        }
        int pending = 0;
        int claimed = 0;
        int failed = 0;
        int done = 0;
        int other = 0;
        List<String> entries = new ArrayList<>();
        for (JobView job : jobs) {
            String state = job.state();
            if (state.equalsIgnoreCase("PENDING")) {
                pending++;
            } else if (state.equalsIgnoreCase("CLAIMED")) {
                claimed++;
            } else if (state.equalsIgnoreCase("FAILED")) {
                failed++;
            } else if (state.equalsIgnoreCase("DONE") || state.equalsIgnoreCase("COMPLETED")) {
                done++;
            } else {
                other++;
            }
            if (entries.size() < 10) {
                entries.add(jobSummary(job));
            }
        }
        return adminText("admin-command-jobs-total-prefix", "Jobs: total=") + jobs.size()
            + adminText("admin-command-jobs-pending-prefix", " pending=") + pending
            + adminText("admin-command-jobs-claimed-prefix", " claimed=") + claimed
            + adminText("admin-command-jobs-failed-prefix", " failed=") + failed
            + adminText("admin-command-jobs-done-prefix", " done=") + done
            + adminText("admin-command-jobs-other-prefix", " other=") + other
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String jobSummary(JobView job) {
        String id = job.id();
        String type = job.type();
        String state = job.state();
        String targetNode = job.targetNode();
        long attempts = job.attempts();
        String error = job.error();
        String shortId = id.length() > 8 ? id.substring(0, 8) : id;
        StringBuilder builder = new StringBuilder(shortId.isBlank() ? adminText("admin-command-job-summary-default-id", "job") : shortId)
            .append(' ')
            .append(type.isBlank() ? "UNKNOWN" : type)
            .append(' ')
            .append(state.isBlank() ? "UNKNOWN" : state)
            .append(adminText("admin-command-job-attempts-prefix", " attempts="))
            .append(attempts);
        if (!targetNode.isBlank()) {
            builder.append(adminText("admin-command-job-node-prefix", " node=")).append(targetNode);
        }
        if (!error.isBlank()) {
            builder.append(adminText("admin-command-job-error-prefix", " error=")).append(error);
        }
        return builder.toString();
    }

    private String adminBankMutationMessage(String label, UUID requestedIslandId, BankMutationView action) {
        String targetId = action.islandId().isBlank() ? requestedIslandId.toString() : action.islandId();
        StringBuilder builder = new StringBuilder(label)
            .append(": ")
            .append(action.accepted() ? adminText("admin-command-action-result-accepted", "accepted") : adminText("admin-command-action-result-rejected", "rejected"))
            .append(adminText("admin-command-action-result-target-prefix", " target="))
            .append(compactTarget(targetId))
            .append(adminText("admin-command-bank-result-balance-prefix", " balance="))
            .append(action.balance());
        if (!action.code().isBlank()) {
            builder.append(adminText("admin-command-action-result-code-prefix", " code=")).append(action.code());
        }
        return builder.toString();
    }

    private String memberActionMessage(String label, MemberActionView action) {
        StringBuilder builder = new StringBuilder(label)
            .append(": ")
            .append(action.accepted() ? adminText("admin-command-action-result-accepted", "accepted") : adminText("admin-command-action-result-rejected", "rejected"));
        if (!action.code().isBlank()) {
            builder.append(adminText("admin-command-action-result-code-prefix", " code=")).append(action.code());
        }
        if (!action.expiresAt().isBlank()) {
            builder.append(adminText("admin-command-action-result-expires-prefix", " expires=")).append(action.expiresAt());
        }
        return builder.toString();
    }

    private String jobActionMessage(String action, JobActionView result) {
        if (!result.accepted()) {
            return adminText("admin-command-job-prefix", "Job ") + action + adminText("admin-command-job-failed-code-prefix", ": failed code=") + result.code();
        }
        return adminText("admin-command-job-prefix", "Job ") + action + ": " + adminText("admin-command-job-accepted", "accepted");
    }

    private String jobRecoveryMessage(JobRecoveryView result) {
        if (!result.accepted()) {
            return adminText("admin-command-job-prefix", "Job ") + "recover" + adminText("admin-command-job-failed-code-prefix", ": failed code=") + result.code();
        }
        return adminText("admin-command-job-recover-prefix", "Job recover: recovered=") + (result.recovered().isBlank() ? "0" : result.recovered());
    }

    private String islandLifecycleActionMessage(String label, UUID requestedIslandId, IslandLifecycleActionView action) {
        String targetId = action.islandId().isBlank() ? requestedIslandId.toString() : action.islandId();
        StringBuilder builder = new StringBuilder(label)
            .append(": ")
            .append(action.accepted() ? adminText("admin-command-action-result-accepted", "accepted") : adminText("admin-command-action-result-rejected", "rejected"))
            .append(adminText("admin-command-action-result-target-prefix", " target="))
            .append(compactTarget(targetId));
        if (!action.code().isBlank()) {
            builder.append(adminText("admin-command-action-result-code-prefix", " code=")).append(action.code());
            String detail = adminCodeDetail(action.code());
            if (!detail.isBlank()) {
                builder.append(adminText("admin-command-action-result-detail-prefix", " detail=")).append(detail);
            }
        }
        if (action.snapshotNo() > 0L) {
            builder.append(adminText("admin-command-action-result-snapshot-prefix", " snapshot=")).append(action.snapshotNo());
        }
        if (!action.storagePath().isBlank()) {
            builder.append(adminText("admin-command-action-result-storage-path-prefix", " storagePath=")).append(action.storagePath());
        }
        return builder.toString();
    }

    private String bulkRestoreMessage(long snapshotNo, List<BulkRestoreEntry> entries) {
        long accepted = entries.stream().filter(BulkRestoreEntry::accepted).count();
        String joinedEntries = entries.stream()
            .map(entry -> {
                String target = entry.islandId() == null ? entry.requested() : compactTarget(entry.islandId().toString());
                String code = entry.code().isBlank() ? (entry.accepted() ? "RESTORE_REQUESTED" : "REJECTED") : entry.code();
                return target + "=" + (entry.accepted() ? "accepted" : "rejected") + "(" + code + ")";
            })
            .toList()
            .stream()
            .collect(java.util.stream.Collectors.joining(", "));
        return adminText("admin-command-bulk-restore-prefix", "Bulk restore")
            + adminText("admin-command-action-result-snapshot-prefix", " snapshot=") + snapshotNo
            + adminText("admin-command-bulk-restore-accepted-prefix", " accepted=") + accepted
            + "/" + entries.size()
            + (joinedEntries.isBlank() ? "" : " " + joinedEntries);
    }

    private String visitorStatsMessage(IslandVisitorStatsView stats) {
        List<String> recent = stats.recentVisitors().stream()
            .limit(5)
            .map(visitor -> visitorDisplayName(visitor) + (visitor.lastVisitedAt().isBlank() ? "" : "@" + visitor.lastVisitedAt()))
            .toList();
        return adminText("admin-command-visitor-stats-prefix", "Visitor stats: island=") + shortId(stats.islandId())
            + adminText("admin-command-visitor-stats-total-prefix", " total=") + stats.totalVisits()
            + adminText("admin-command-visitor-stats-unique-prefix", " unique=") + stats.uniqueVisitors()
            + (recent.isEmpty() ? "" : adminText("admin-command-visitor-stats-recent-prefix", " recent=") + String.join(",", recent));
    }

    private String visitorDisplayName(IslandVisitorStatsView.RecentVisitorView visitor) {
        return visitor.visitorName().isBlank() ? shortId(visitor.visitorUuid()) : visitor.visitorName().trim();
    }

    private String adminCodeDetail(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        if (code.startsWith("NO_READY_NODE")) {
            return "no-ready-node";
        }
        if (code.startsWith("TARGET_NODE")) {
            return "target-node-blocked";
        }
        if (code.startsWith("ACTIVE_NODE")) {
            return "active-node-blocked";
        }
        return switch (code) {
            case "ACTIVATION_LOCKED" -> "activation-in-progress";
            case "VISITOR_SOFT_FULL" -> "visitor-denied-soft-full";
            case "CREATE_LOCKED" -> "player-create-lock-held";
            case "NODE_UNAVAILABLE" -> "node-unavailable";
            default -> "";
        };
    }

    private String compactTarget(String targetId) {
        return targetId != null && targetId.length() == 36 && targetId.indexOf('-') > 0 ? shortId(targetId) : targetId;
    }

    private String islandInfoMessage(CoreGuiViews.IslandInfoView island) {
        if (island.islandId().isBlank()) {
            return adminText("admin-command-island-info-failed-prefix", "Island: failed code=") + "ISLAND_NOT_FOUND";
        }
        return adminText("admin-command-island-info-id-prefix", "Island: id=") + shortId(island.islandId())
            + adminText("admin-command-island-info-owner-prefix", " owner=") + shortId(island.ownerUuid())
            + (island.name().isBlank() ? "" : adminText("admin-command-island-info-name-prefix", " name=") + island.name())
            + adminText("admin-command-island-info-state-prefix", " state=") + (island.state().isBlank() ? "UNKNOWN" : island.state())
            + adminText("admin-command-island-info-size-prefix", " size=") + island.size()
            + adminText("admin-command-island-info-level-prefix", " level=") + island.level()
            + adminText("admin-command-island-info-worth-prefix", " worth=") + island.worth()
            + adminText("admin-command-island-info-public-prefix", " public=") + island.publicAccess();
    }

    private CompletableFuture<CharSequence> islandInfoDetailsMessage(CoreGuiViews.IslandInfoView island) {
        String summary = islandInfoMessage(island);
        UUID islandId = uuidOrNull(island.islandId());
        if (islandId == null) {
            return CompletableFuture.completedFuture(summary);
        }
        CompletableFuture<List<IslandLimitSnapshot>> limits = withFallback(coreApiClient.environment().limits(islandId), List.of());
        CompletableFuture<List<CoreGuiViews.UpgradeView>> upgrades = withFallback(coreApiClient.progression().upgrades(islandId), List.of());
        return CompletableFuture.allOf(limits, upgrades).thenApply(_ignored -> {
            List<String> sections = AdminIslandInfoSections.collect(limits.join(), upgrades.join()).stream()
                .map(this::islandInfoSectionMessage)
                .filter(section -> !section.isBlank())
                .toList();
            return sections.isEmpty() ? summary : summary + " | " + String.join(" | ", sections);
        });
    }

    private String islandInfoSectionMessage(AdminIslandInfoSections.Section section) {
        return switch (section.kind()) {
            case EFFECTS -> adminText("admin-command-island-info-effects-prefix", "effects=") + section.value();
            case ROLE_LIMITS -> adminText("admin-command-island-info-role-limits-prefix", "roleLimits=") + section.value();
            case UPGRADES -> adminText("admin-command-island-info-upgrades-prefix", "upgrades=") + section.value();
        };
    }

    private String runtimeInfoMessage(AdminIslandRuntimeView runtime) {
        if (!runtime.code().isBlank()) {
            return adminText("admin-command-runtime-failed-prefix", "Island runtime: failed code=") + runtime.code();
        }
        return adminText("admin-command-runtime-island-prefix", "Island runtime: island=") + shortId(runtime.islandId())
            + adminText("admin-command-runtime-state-prefix", " state=") + (runtime.state().isBlank() ? "UNKNOWN" : runtime.state())
            + (runtime.activeNode().isBlank() ? "" : adminText("admin-command-runtime-node-prefix", " node=") + runtime.activeNode())
            + (runtime.activeWorld().isBlank() ? "" : adminText("admin-command-runtime-world-prefix", " world=") + runtime.activeWorld())
            + (runtime.hasCell() ? adminText("admin-command-runtime-cell-prefix", " cell=") + runtime.cellX() + "," + runtime.cellZ() : "")
            + adminText("admin-command-runtime-fence-prefix", " fence=") + runtime.fencingToken();
    }

    private String playerInfoMessage(PlayerProfileView profile) {
        if (profile.playerUuid().isBlank()) {
            return adminText("admin-command-player-info-failed-prefix", "Player: failed code=") + "PLAYER_NOT_FOUND";
        }
        String playerUuid = profile.playerUuid();
        String lastName = profile.lastName();
        String islandId = profile.primaryIslandId();
        return adminText("admin-command-player-info-uuid-prefix", "Player: uuid=") + shortId(playerUuid)
            + (lastName.isBlank() ? "" : adminText("admin-command-player-info-name-prefix", " name=") + lastName)
            + (islandId.isBlank() ? adminText("admin-command-player-info-island-none", " island=none") : adminText("admin-command-player-info-island-prefix", " island=") + shortId(islandId))
            + adminText("admin-command-player-info-disbands-prefix", " disbands=") + profile.disbandsRemaining();
    }

    private String playerActionMessage(String label, PlayerProfileView profile) {
        if (profile.playerUuid().isBlank()) {
            return label + adminText("admin-command-player-action-failed-code-prefix", ": failed code=") + "PLAYER_NOT_FOUND";
        }
        String islandId = profile.primaryIslandId();
        return label
            + adminText("admin-command-action-result-accepted-target-prefix", ": accepted target=")
            + shortId(profile.playerUuid())
            + (islandId.isBlank() ? adminText("admin-command-player-info-island-none", " island=none") : adminText("admin-command-player-info-island-prefix", " island=") + shortId(islandId));
    }

    private String playerDisbandsActionMessage(String label, PlayerProfileView profile) {
        if (profile.playerUuid().isBlank()) {
            return label + adminText("admin-command-player-action-failed-code-prefix", ": failed code=") + "PLAYER_NOT_FOUND";
        }
        return label
            + adminText("admin-command-action-result-accepted-target-prefix", ": accepted target=")
            + shortId(profile.playerUuid())
            + adminText("admin-command-player-info-disbands-prefix", " disbands=") + profile.disbandsRemaining();
    }

    private String rankingListMessage(String label, List<ProgressionRankingEntryView> rankings) {
        if (rankings.isEmpty()) {
            return label + adminText("admin-command-ranking-empty-suffix", ": empty");
        }
        List<String> entries = new ArrayList<>();
        int total = rankings.size();
        int rank = 0;
        for (ProgressionRankingEntryView ranking : rankings) {
            rank++;
            if (entries.size() < 10) {
                entries.add("#" + rank
                    + " " + shortId(ranking.islandId())
                    + adminText("admin-command-ranking-level-prefix", " level=") + ranking.level()
                    + adminText("admin-command-ranking-worth-prefix", " worth=") + ranking.worth());
            }
        }
        return label + adminText("admin-command-ranking-total-prefix", ": total=") + total + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String rankingIgnoreMessage(String label, kr.lunaf.cloudislands.coreclient.ProgressionRankingIgnoreView result) {
        return label
            + adminText("admin-command-action-result-accepted-prefix", ": accepted=")
            + result.accepted()
            + adminText("admin-command-action-result-code-prefix", " code=")
            + result.code()
            + adminText("admin-command-action-result-target-prefix", " target=")
            + shortId(result.islandId())
            + adminText("admin-command-ranking-ignore-state-prefix", " ignored=")
            + result.ignored();
    }

    private String permissionActionMessage(String label, PermissionActionView result) {
        return label
            + adminText("admin-command-action-result-accepted-prefix", ": accepted=")
            + result.accepted()
            + adminText("admin-command-action-result-code-prefix", " code=")
            + result.code();
    }

    private String missionActionMessage(String label, kr.lunaf.cloudislands.coreclient.ProgressionMissionCompletionView result) {
        return label
            + adminText("admin-command-action-result-accepted-prefix", ": accepted=")
            + result.accepted()
            + adminText("admin-command-action-result-code-prefix", " code=")
            + result.code()
            + adminText("admin-command-mission-key-prefix", " mission=")
            + result.missionKey()
            + adminText("admin-command-mission-progress-prefix", " progress=")
            + result.progress()
            + "/"
            + result.goal();
    }

    private String upgradePurchaseMessage(String label, ProgressionUpgradePurchaseView result) {
        return label
            + adminText("admin-command-action-result-accepted-prefix", ": accepted=")
            + result.accepted()
            + adminText("admin-command-action-result-code-prefix", " code=")
            + result.code()
            + adminText("admin-command-upgrade-key-prefix", " upgrade=")
            + result.upgradeKey()
            + adminText("admin-command-upgrade-level-prefix", " level=")
            + result.level()
            + adminText("admin-command-upgrade-cost-prefix", " cost=")
            + result.cost();
    }

    private String reviewActionMessage(String label, ReviewActionView result) {
        return label
            + adminText("admin-command-action-result-accepted-prefix", ": accepted=")
            + result.accepted()
            + adminText("admin-command-action-result-code-prefix", " code=")
            + result.code();
    }

    private String reviewModerationQueueMessage(List<ReviewModerationView> reviews) {
        if (reviews.isEmpty()) {
            return "Review moderation queue: empty";
        }
        return "Review moderation queue: total=" + reviews.size() + " / "
            + String.join(" | ", reviews.stream().map(this::reviewModerationMessage).limit(20).toList());
    }

    private String reviewModerationMessage(ReviewModerationView review) {
        return "island=" + (review.islandName().isBlank() ? review.islandId() : review.islandName() + "(" + review.islandId() + ")")
            + " reviewer=" + (review.reviewerName().isBlank() ? review.reviewerUuid() : review.reviewerName() + "(" + review.reviewerUuid() + ")")
            + " state=" + review.moderationState()
            + " reports=" + review.reportCount()
            + (review.reportReason().isBlank() ? "" : " reason=" + review.reportReason())
            + (review.moderationNote().isBlank() ? "" : " note=" + review.moderationNote());
    }

    private static String reviewModerationState(String value) {
        if (value == null) {
            return "";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "visible", "show", "restore", "표시", "복구" -> "VISIBLE";
            case "reported", "pending", "신고", "대기" -> "REPORTED";
            case "hidden", "hide", "숨김" -> "HIDDEN";
            default -> "";
        };
    }

    private String blockValueListMessage(List<BlockValueView> values) {
        if (values.isEmpty()) {
            return adminText("admin-command-block-values-empty", "Block values: empty");
        }
        List<String> entries = new ArrayList<>();
        for (BlockValueView value : values) {
            if (entries.size() < 10) {
                entries.add(value.materialKey()
                    + adminText("admin-command-block-values-worth-prefix", " worth=") + value.worth()
                    + adminText("admin-command-block-values-level-prefix", " level=") + value.levelPoints()
                    + adminText("admin-command-block-values-limit-prefix", " limit=") + value.limit());
            }
        }
        return adminText("admin-command-block-values-total-prefix", "Block values: total=") + values.size() + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String blockValueSearchMessage(String query, List<BlockValueView> values, int limit) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int cappedLimit = Math.max(1, Math.min(limit, 50));
        List<BlockValueView> safeValues = values == null ? List.of() : values;
        List<BlockValueView> matches = safeValues.stream()
            .filter(value -> value != null && value.materialKey().toLowerCase(Locale.ROOT).contains(normalizedQuery))
            .limit(cappedLimit)
            .toList();
        if (matches.isEmpty()) {
            return adminText("admin-command-block-values-search-empty-prefix", "Block values search: no matches for ") + normalizedQuery;
        }
        return adminText("admin-command-block-values-search-prefix", "Block values search: query=") + normalizedQuery
            + adminText("admin-command-block-values-search-total-prefix", " matches=") + matches.size()
            + " / " + blockValueListMessage(matches);
    }

    private String blockValueActionMessage(String label, String targetId, BlockValueActionView result) {
        if (!result.accepted()) {
            return label + ": " + adminText("admin-command-action-result-rejected", "rejected")
                + adminText("admin-command-action-result-target-prefix", " target=") + compactTarget(targetId)
                + (result.code().isBlank() ? "" : adminText("admin-command-action-result-code-prefix", " code=") + result.code());
        }
        String resolvedTarget = result.materialKey().isBlank() ? targetId : result.materialKey();
        return label + adminText("admin-command-action-result-accepted-target-prefix", ": accepted target=") + shortId(resolvedTarget);
    }

    private String gameplayModifierMessage(String label, EnvironmentActionView result) {
        return label
            + " accepted=" + result.accepted()
            + " code=" + result.code()
            + " key=" + result.key()
            + " value=" + result.value()
            + " island=" + shortId(result.islandId());
    }

    private String settingsActionMessage(String label, UUID islandId, SettingsActionView result) {
        return label
            + " accepted=" + result.accepted()
            + " code=" + result.code()
            + " island=" + shortId(islandId.toString());
    }

    private String homeWarpActionMessage(String label, UUID islandId, String name, HomeWarpActionView result) {
        return label
            + " accepted=" + result.accepted()
            + " code=" + result.code()
            + " island=" + shortId(islandId.toString())
            + " name=" + name;
    }

    private String generatorActionMessage(String label, IslandGeneratorSnapshot result) {
        return label
            + " accepted=true"
            + " island=" + shortId(result.islandId().toString())
            + " generator=" + result.generatorKey()
            + " level=" + result.level();
    }

    private String templateListMessage(List<TemplateView> templates) {
        if (templates.isEmpty()) {
            return adminText("admin-command-templates-empty", "Templates: empty");
        }
        List<String> entries = new ArrayList<>();
        int enabled = 0;
        for (TemplateView template : templates) {
            if (template.enabled()) {
                enabled++;
            }
            if (entries.size() < 10) {
                String minNodeVersion = template.minNodeVersion();
                entries.add(template.id()
                    + " " + (template.enabled() ? adminText("admin-command-template-enabled", "enabled") : adminText("admin-command-template-disabled", "disabled"))
                    + (minNodeVersion.isBlank() ? "" : adminText("admin-command-template-min-prefix", " min=") + minNodeVersion)
                    + (template.bundleStoragePath().isBlank() ? "" : adminText("admin-command-template-bundle-prefix", " bundle=") + shortId(template.bundleStoragePath()))
                    + (template.creationCost().isBlank() || "0".equals(template.creationCost()) ? "" : adminText("admin-command-template-cost-prefix", " cost=") + template.creationCost()));
            }
        }
        return adminText("admin-command-templates-total-prefix", "Templates: total=") + templates.size() + adminText("admin-command-templates-enabled-prefix", " enabled=") + enabled + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String templateDoctorDiagnosticBody(List<TemplateView> templates) {
        if (templates.isEmpty()) {
            return "templates=empty WARN_TEMPLATE_CATALOG_EMPTY";
        }
        int ok = 0;
        int warn = 0;
        int invalid = 0;
        List<String> entries = new ArrayList<>();
        for (TemplateView template : templates) {
            String status = templateValidationStatus(template);
            if ("OK".equals(status)) {
                ok++;
            } else if (status.startsWith("WARN_")) {
                warn++;
            } else {
                invalid++;
            }
            if (entries.size() < 6 && !"OK".equals(status)) {
                entries.add(template.id() + "=" + status);
            }
        }
        return "templates=" + templates.size()
            + " ok=" + ok
            + " warn=" + warn
            + " invalid=" + invalid
            + (entries.isEmpty() ? "" : " issues=" + String.join(",", entries));
    }

    private String templateActionMessage(String label, String targetId, TemplateView template) {
        String resolvedTarget = template.id().isBlank() ? targetId : template.id();
        return label + adminText("admin-command-action-result-accepted-target-prefix", ": accepted target=") + shortId(resolvedTarget)
            + adminText("admin-command-template-enabled-prefix", " enabled=") + template.enabled()
            + (template.requiredPermission().isBlank() ? "" : adminText("admin-command-template-permission-prefix", " permission=") + template.requiredPermission())
            + adminText("admin-command-template-icon-prefix", " icon=") + template.iconMaterial()
            + (template.creationCost().isBlank() || "0".equals(template.creationCost()) ? "" : adminText("admin-command-template-cost-prefix", " cost=") + template.creationCost())
            + (template.bundleStoragePath().isBlank() ? "" : adminText("admin-command-template-bundle-prefix", " bundle=") + shortId(template.bundleStoragePath()));
    }

    private String templateBooleanActionMessage(String label, String targetId, boolean accepted) {
        return label + ": " + (accepted ? adminText("admin-command-action-result-accepted", "accepted") : adminText("admin-command-action-result-rejected", "rejected"))
            + adminText("admin-command-action-result-target-prefix", " target=") + compactTarget(targetId);
    }

    private String templatePreviewMessage(String targetId, List<TemplateView> templates) {
        TemplateView template = templateById(targetId, templates);
        if (template == null) {
            return adminText("admin-command-template-not-found", "Template: not found ") + compactTarget(targetId);
        }
        return adminText("admin-command-template-preview-prefix", "Template preview: ")
            + "id=" + template.id()
            + adminText("admin-command-template-name-prefix", " name=") + (template.displayName().isBlank() ? template.id() : template.displayName())
            + adminText("admin-command-template-category-prefix", " category=") + template.category()
            + adminText("admin-command-template-enabled-prefix", " enabled=") + template.enabled()
            + (template.minNodeVersion().isBlank() ? "" : adminText("admin-command-template-min-prefix", " min=") + template.minNodeVersion())
            + adminText("admin-command-template-size-prefix", " size=") + template.defaultIslandSize()
            + (template.creationCost().isBlank() || "0".equals(template.creationCost()) ? "" : adminText("admin-command-template-cost-prefix", " cost=") + template.creationCost())
            + (template.requiredPermission().isBlank() ? "" : adminText("admin-command-template-permission-prefix", " permission=") + template.requiredPermission())
            + adminText("admin-command-template-bundle-prefix", " bundle=") + (template.bundleStoragePath().isBlank() ? "not-certified" : shortId(template.bundleStoragePath()))
            + adminText("admin-command-template-checksum-prefix", " checksum=") + (template.bundleChecksum().isBlank() ? "not-certified" : shortId(template.bundleChecksum()));
    }

    private String templateValidateMessage(String targetId, List<TemplateView> templates) {
        TemplateView template = templateById(targetId, templates);
        if (template == null) {
            return adminText("admin-command-template-not-found", "Template: not found ") + compactTarget(targetId);
        }
        return adminText("admin-command-template-validate-prefix", "Template validate: ")
            + "id=" + template.id()
            + adminText("admin-command-template-validation-status-prefix", " status=") + templateValidationStatus(template)
            + adminText("admin-command-template-enabled-prefix", " enabled=") + template.enabled()
            + (template.minNodeVersion().isBlank() ? adminText("admin-command-template-min-missing", " min=missing") : adminText("admin-command-template-min-prefix", " min=") + template.minNodeVersion())
            + adminText("admin-command-template-bundle-prefix", " bundle=") + (template.bundleStoragePath().isBlank() ? "not-certified" : shortId(template.bundleStoragePath()))
            + adminText("admin-command-template-checksum-prefix", " checksum=") + (template.bundleChecksum().isBlank() ? "not-certified" : shortId(template.bundleChecksum()));
    }

    private String templateValidationStatus(TemplateView template) {
        if (template.id().isBlank() || template.displayName().isBlank()) {
            return "INVALID_METADATA";
        }
        if ("superiorskyblock2".equalsIgnoreCase(template.id()) && template.enabled()) {
            return "BLOCKED_MIGRATION_INPUT_ONLY";
        }
        if (template.enabled() && template.minNodeVersion().isBlank()) {
            return "WARN_MIN_NODE_VERSION_MISSING";
        }
        if (!template.bundleStoragePath().isBlank() && template.bundleChecksum().isBlank()) {
            return "INVALID_BUNDLE_CHECKSUM_MISSING";
        }
        if (template.enabled() && template.bundleStoragePath().isBlank()) {
            return "WARN_BUNDLE_MISSING";
        }
        return "OK";
    }

    private String templateBundleVerificationMessage(TemplateBundleVerificationView view) {
        return adminText("admin-command-template-verify-prefix", "Template bundle verify: ")
            + "id=" + view.templateId()
            + adminText("admin-command-template-ok-prefix", " ok=") + view.ok()
            + adminText("admin-command-template-bundle-prefix", " bundle=") + shortId(view.bundleStoragePath())
            + adminText("admin-command-template-checksum-prefix", " checksum=") + shortId(view.bundleChecksum())
            + adminText("admin-command-template-size-prefix", " size=") + view.bundleSizeBytes();
    }

    private static TemplateView templateBundleView(String templateId, String displayName, String bundlePath, String checksum, long bundleSizeBytes) {
        return new TemplateView(
            templateId,
            displayName,
            "",
            "default",
            false,
            "",
            "",
            "GRASS_BLOCK",
            0,
            "",
            bundlePath,
            checksum,
            bundleSizeBytes,
            3,
            300,
            0.5D,
            100.0D,
            0.5D,
            180.0F,
            0.0F,
            "default",
            "normal",
            "minecraft:plains",
            "BLUE",
            "0",
            "0",
            0,
            List.of()
        );
    }

    private static TemplateView templateWithCatalogFields(TemplateView template, String requiredPermission, String iconMaterial, int iconCustomModelData, String creationCost) {
        return new TemplateView(
            template.id(),
            template.displayName(),
            template.description(),
            template.category(),
            template.enabled(),
            template.minNodeVersion(),
            requiredPermission,
            iconMaterial,
            iconCustomModelData,
            template.previewImageKey(),
            template.bundleStoragePath(),
            template.bundleChecksum(),
            template.bundleSizeBytes(),
            template.schemaVersion(),
            template.defaultIslandSize(),
            template.spawnWorldOffsetX(),
            template.spawnWorldOffsetY(),
            template.spawnWorldOffsetZ(),
            template.spawnYaw(),
            template.spawnPitch(),
            template.homeName(),
            template.environmentPreset(),
            template.biomeKey(),
            template.borderColor(),
            template.bankInitialBalance(),
            creationCost,
            template.sortOrder(),
            template.tags()
        );
    }

    private static String templatePermissionArgument(String value) {
        if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("clear") || value.equals("-")) {
            return "";
        }
        return value;
    }

    private TemplateView templateById(String targetId, List<TemplateView> templates) {
        String normalized = normalizeTemplateId(targetId);
        for (TemplateView template : templates) {
            if (template.id().equalsIgnoreCase(normalized)) {
                return template;
            }
        }
        return null;
    }

    private String normalizeTemplateId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "-");
        return normalized.isBlank() ? "default" : normalized;
    }

    private String upgradeRulesMessage(List<UpgradeRuleView> rules) {
        if (rules.isEmpty()) {
            return adminText("admin-command-upgrade-rules-empty", "Upgrade rules: empty");
        }
        List<String> entries = new ArrayList<>();
        for (UpgradeRuleView rule : rules) {
            if (entries.size() < 10) {
                entries.add(rule.key()
                    + adminText("admin-command-upgrade-rules-type-prefix", " type=") + rule.type()
                    + adminText("admin-command-upgrade-rules-max-prefix", " max=") + rule.maxLevel()
                    + adminText("admin-command-upgrade-rules-base-prefix", " base=") + rule.baseCost());
            }
        }
        return adminText("admin-command-upgrade-rules-total-prefix", "Upgrade rules: total=") + rules.size() + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String bonusListMessage(List<CoreGuiViews.LimitView> limits) {
        List<String> entries = limits.stream()
            .filter(limit -> limit.key().startsWith(BONUS_LIMIT_PREFIX))
            .map(limit -> limit.key().substring(BONUS_LIMIT_PREFIX.length()) + "=" + limit.value())
            .sorted()
            .toList();
        if (entries.isEmpty()) {
            return adminText("admin-command-bonus-empty", "Island bonus: empty");
        }
        return adminText("admin-command-bonus-prefix", "Island bonus: ") + String.join(", ", entries);
    }

    private String bonusSyncMessage(String label, ProgressionUpgradeRecalculationView result) {
        return label
            + adminText("admin-command-action-result-accepted-target-prefix", ": accepted target=")
            + shortId(result.islandId())
            + adminText("admin-command-bonus-sync-applied-prefix", " applied=") + result.applied();
    }

    private String maintenanceMessage(String label, AdminMaintenanceResultView result) {
        if (!result.code().isBlank()) {
            return label + adminText("admin-command-maintenance-failed-code-prefix", ": failed code=") + result.code();
        }
        return label + adminText("admin-command-maintenance-accepted-sessions-prefix", ": accepted sessions=") + result.clearedSessions() + adminText("admin-command-maintenance-tickets-prefix", " tickets=") + result.clearedTickets();
    }

    private String addonListMessage(List<CloudIslandsAddonSnapshot> addons) {
        if (addons.isEmpty()) {
            return adminText("admin-command-addons-empty", "Addons: empty");
        }
        int enabled = 0;
        List<String> entries = new ArrayList<>();
        for (CloudIslandsAddonSnapshot addon : addons) {
            if (addon.enabled()) {
                enabled++;
            }
            entries.add(addon.id()
                + adminText("admin-command-addons-name-prefix", " name=") + addon.displayName()
                + adminText("admin-command-addons-version-prefix", " version=") + addon.version()
                + adminText("admin-command-addons-enabled-prefix", " enabled=") + addon.enabled()
                + addonDependencySuffix(addon)
                + addonDependencyDisabledSuffix(addon)
                + addonMetadataSuffix(addon)
                + addonConfiguredFeatureSuffix(addon)
                + addonFeatureSuffix(addon));
        }
        return adminText("admin-command-addons-total-prefix", "Addons: total=") + addons.size()
            + adminText("admin-command-addons-enabled-count-prefix", " enabled=") + enabled
            + " / " + String.join(" | ", entries);
    }

    private String addonInfoMessage(CloudIslandsAddonSnapshot addon) {
        return adminText("admin-command-addon-info-prefix", "Addon: ") + addon.id()
            + adminText("admin-command-addons-name-prefix", " name=") + addon.displayName()
            + adminText("admin-command-addons-version-prefix", " version=") + addon.version()
            + adminText("admin-command-addons-enabled-prefix", " enabled=") + addon.enabled()
            + adminText("admin-command-addons-registered-prefix", " registered=") + addon.registeredAt()
            + adminText("admin-command-addons-updated-prefix", " updated=") + addon.updatedAt()
            + addonDependencySuffix(addon)
            + addonDependencyDisabledSuffix(addon)
            + addonMetadataSuffix(addon)
            + addonConfiguredFeatureSuffix(addon)
            + addonFeatureSuffix(addon);
    }

    private String addonStateSummaryMessage(AdminAddonStateSummaryView summary) {
        if (summary.addons().isEmpty()) {
            return adminText("admin-command-addons-state-empty", "Addon state: empty");
        }
        List<String> entries = new ArrayList<>();
        for (AdminAddonStateSummaryView.AddonView addon : summary.addons()) {
            if (entries.size() < 10) {
                entries.add(addon.addonId()
                    + adminText("admin-command-addons-state-global-prefix", " global=") + addon.globalKeys()
                    + adminText("admin-command-addons-state-island-prefix", " island=") + addon.islandKeys()
                    + adminText("admin-command-addons-state-total-keys-prefix", " totalKeys=") + addon.totalKeys());
            }
        }
        return adminText("admin-command-addons-state-total-prefix", "Addon state: total=") + summary.addons().size()
            + " owner=" + summary.stateOwnership()
            + " registeredRequired=" + summary.registeredAddonRequired()
            + " orphanPolicy=" + summary.orphanStatePolicy()
            + " missingPolicy=" + summary.missingAddonStatePolicy()
            + " tableKeyPrefix=" + summary.tableKeyPrefix()
            + " maxKeysPerAddon=" + summary.maxKeysPerAddon()
            + " maxValueLength=" + summary.maxValueLength()
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String addonDependencySuffix(CloudIslandsAddonSnapshot addon) {
        if (addon.featureDependencies().isEmpty()) {
            return "";
        }
        List<String> dependencies = new ArrayList<>();
        addon.featureDependencies().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> dependencies.add(entry.getKey() + ":" + entry.getValue()));
        return adminText("admin-command-addons-dependencies-prefix", " dependencies=") + String.join(",", dependencies);
    }

    private String addonDependencyDisabledSuffix(CloudIslandsAddonSnapshot addon) {
        if (!addon.enabled() || addon.featureDependencies().isEmpty()) {
            return "";
        }
        List<String> disabled = new ArrayList<>();
        addon.featureDependencies().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> {
                String feature = entry.getKey();
                String required = entry.getValue();
                if (addon.configuredFeatureEnabled(feature, true) && !addon.featureEnabled(required, true)) {
                    disabled.add(feature + "->" + required);
                }
            });
        if (disabled.isEmpty()) {
            return "";
        }
        return adminText("admin-command-addons-dependency-disabled-prefix", " dependencyDisabled=") + String.join(",", disabled);
    }

    private String addonMetadataSuffix(CloudIslandsAddonSnapshot addon) {
        if (addon.metadata().isEmpty()) {
            return "";
        }
        List<String> metadata = new ArrayList<>();
        addon.metadata().entrySet().stream()
            .filter(entry -> !entry.getKey().equals("feature-aliases"))
            .filter(entry -> !entry.getKey().equals("feature-dependencies"))
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> metadata.add(entry.getKey() + "=" + entry.getValue()));
        if (metadata.isEmpty()) {
            return "";
        }
        return adminText("admin-command-addons-metadata-prefix", " metadata=") + String.join(",", metadata);
    }

    private String addonFeatureSuffix(CloudIslandsAddonSnapshot addon) {
        if (addon.features().isEmpty()) {
            return "";
        }
        List<String> features = new ArrayList<>();
        addon.features().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> features.add(entry.getKey() + "=" + entry.getValue()));
        return adminText("admin-command-addons-effective-features-prefix", " effectiveFeatures=") + String.join(",", features);
    }

    private String addonConfiguredFeatureSuffix(CloudIslandsAddonSnapshot addon) {
        if (addon.configuredFeatures().isEmpty()) {
            return "";
        }
        List<String> features = new ArrayList<>();
        addon.configuredFeatures().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> features.add(entry.getKey() + "=" + entry.getValue()));
        return adminText("admin-command-addons-configured-features-prefix", " configuredFeatures=") + String.join(",", features);
    }

    private String metricsMessage(AdminMetricsSummaryView summary) {
        if (summary == null || summary.samples() <= 0L) {
            return adminText("admin-command-metrics-empty", "Core metrics: empty");
        }
        return adminText("admin-command-metrics-samples-prefix", "Core metrics: samples=") + summary.samples()
            + metricsFocusSuffix(summary)
            + adminText("admin-command-metrics-legacy-aliases-prefix", " ")
            + kr.lunaf.cloudislands.paper.command.SuperiorSkyblock2CommandAliasAdapter.metricsLine()
            + (summary.names().isEmpty() ? "" : " / " + String.join(", ", summary.names()));
    }

    private String metricsFocusSuffix(AdminMetricsSummaryView summary) {
        List<String> focus = new ArrayList<>();
        appendMetricFocus(focus, summary, "activeIslands", "cloudislands_node_active_islands", false);
        appendMetricFocus(focus, summary, "routeCreated", "cloudislands_route_ticket_created_total", false);
        appendMetricFocus(focus, summary, "routeConsumed", "cloudislands_route_ticket_consumed_total", false);
        appendMetricFocus(focus, summary, "routeFailed", "cloudislands_route_ticket_failed_total", false);
        appendMetricFocus(focus, summary, "jobsPending", "cloudislands_jobs_pending", false);
        appendMetricFocus(focus, summary, "jobRetries", "cloudislands_jobs_retry_total", false);
        appendMetricFocus(focus, summary, "heartbeatAge", "cloudislands_node_heartbeat_age_seconds", true);
        appendMetricFocus(focus, summary, "redisLatency", "cloudislands_redis_latency_seconds", true);
        appendMetricFocus(focus, summary, "dbQuery", "cloudislands_database_query_seconds", true);
        appendMetricFocus(focus, summary, "storageSave", "cloudislands_storage_upload_seconds", true);
        appendMetricFocus(focus, summary, "storageRestore", "cloudislands_storage_download_seconds", true);
        appendMetricFocus(focus, summary, "activation", "cloudislands_island_activation_seconds", true);
        appendMetricFocus(focus, summary, "permissionHitRatio", "cloudislands_permission_cache_hit_ratio", false);
        appendMetricFocus(focus, summary, "coreApiRejects", "cloudislands_core_security_rejects_total", false);
        return focus.isEmpty()
            ? ""
            : adminText("admin-command-metrics-focus-prefix", " focus=") + String.join(",", focus);
    }

    private void appendMetricFocus(List<String> focus, AdminMetricsSummaryView summary, String label, String metricName, boolean seconds) {
        if (!summary.hasValue(metricName)) {
            return;
        }
        double value = summary.value(metricName);
        focus.add(label + "=" + (seconds ? seconds(value) + "s" : metricValue(value)));
    }

    private String metricValue(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return seconds(value);
    }

    private String coreConfigMessage(AdminCoreConfigView body) {
        String code = body.code();
        if (!code.isBlank()) {
            return adminText("admin-command-core-config-failed-prefix", "Core config: failed code=") + code;
        }
        return adminText("admin-command-core-config-repo-prefix", "Core config: repo=") + textValue(body, "repositoryMode")
            + adminText("admin-command-core-config-jobs-prefix", " jobs=") + textValue(body, "jobQueueMode")
            + adminText("admin-command-core-config-events-prefix", " events=") + textValue(body, "eventBusMode")
            + adminText("admin-command-core-config-effective-repo-prefix", " effectiveRepo=") + textValue(body, "effectiveRepositoryMode")
            + adminText("admin-command-core-config-effective-jobs-prefix", " effectiveJobs=") + textValue(body, "effectiveJobQueueMode")
            + adminText("admin-command-core-config-storage-prefix", " storage=") + textValue(body, "storageType")
            + adminText("admin-command-core-config-island-model-prefix", " islandModel=") + textValue(body, "islandResourceModel")
            + adminText("admin-command-core-config-island-portable-prefix", " portableBundle=") + boolValue(body, "islandPortableBundle")
            + adminText("admin-command-core-config-island-pinned-prefix", " serverPinned=") + boolValue(body, "islandServerPinned")
            + adminText("admin-command-core-config-island-execution-prefix", " islandExecution=") + textValue(body, "islandExecutionModel")
            + adminText("admin-command-core-config-island-node-role-prefix", " islandNodeRole=") + textValue(body, "islandNodeRole")
            + adminText("admin-command-core-config-island-routing-prefix", " islandRouting=") + textValue(body, "islandRoutingModel")
            + " createFlow=" + textValue(body, "createIslandRequestFlow")
            + " homeFlow=" + textValue(body, "homeRequestFlow")
            + " visitFlow=" + textValue(body, "visitRequestFlow")
            + " routeUi=" + textValue(body, "routePlayerLoadingUi")
            + " routeFailureCodes=" + textValue(body, "routePlayerFailureCodes")
            + " routePublicMessages=" + textValue(body, "routePublicMessagePolicy")
            + " routeDebugReasons=" + textValue(body, "routeDebugReasonPolicy")
            + " routeTransferFailure=" + textValue(body, "routeTransferFailurePolicy")
            + " softFullRoute=" + textValue(body, "softFullRoutingPolicy")
            + " modules=" + textValue(body, "moduleLayout")
            + " dist=" + textValue(body, "distributionLayout")
            + " distTasks=" + textValue(body, "distributionTaskLayout")
            + " distGuard=" + textValue(body, "distributionNoMarkdownGuard")
            + " addonRegistry=" + textValue(body, "addonRegistryPolicy")
            + " addonStateOwner=" + textValue(body, "addonStateOwnershipPolicy")
            + " addonRemovalSafe=" + textValue(body, "addonRemovalSafetyPolicy")
            + " addonStateIsolation=" + textValue(body, "addonStateFailureIsolationPolicy")
            + " addonExtension=" + textValue(body, "addonExtensionModel")
            + " addonApiLookup=" + textValue(body, "addonApiLookupPolicy")
            + " addonApiContract=" + textValue(body, "addonApiContractVersion")
            + " addonApiContractStatus=" + textValue(body, "addonApiContractCompatibility")
            + " addonApiContractCompatible=" + textValue(body, "addonApiContractCompatible")
            + " satisMultiNodeSafe=" + boolValue(body, "satisMultiNodeSafe")
            + " satisNodeCountPolicy=" + textValue(body, "satisNodeCountPolicy")
            + " addonApiRequiredKeys=" + textValue(body, "addonApiRequiredMetadataKeys")
            + " addonApiRead=" + textValue(body, "addonApiReadPolicy")
            + " addonApiWrite=" + textValue(body, "addonApiWriteAuthority")
            + " addonApiSyncEvent=" + textValue(body, "addonApiSyncEventPolicy")
            + " addonApiStorage=" + textValue(body, "addonApiStoragePolicy")
            + " addonJavaApi=" + textValue(body, "addonJavaPluginApiPolicy")
            + " addonInternalApi=" + textValue(body, "addonInternalApiPolicy")
            + " addonEventApi=" + textValue(body, "addonEventApiPolicy")
            + " addonCoreAuth=" + textValue(body, "addonCoreAuthPolicy")
            + " addonAdminEndpoint=" + textValue(body, "addonAdminEndpointPolicy")
            + " addonNetworkExposure=" + textValue(body, "addonNetworkExposurePolicy")
            + " addonSecurityPosture=" + textValue(body, "addonSecurityPostureSummary")
            + " addonTopologyPrivacy=" + textValue(body, "addonTopologyPrivacyPolicy")
            + " addonConsistency=" + textValue(body, "addonConsistencyAuthorityPolicy")
            + " addonEvents=" + textValue(body, "addonEventDeliveryPolicy")
            + " addonEventCoverage=" + textValue(body, "addonEventCoverage")
            + " addonEventBackfill=" + textValue(body, "addonEventBackfillPolicy")
            + " gameplayParity=" + textValue(body, "gameplayParityContract")
            + " gameplayPlayerSurfaces=" + textValue(body, "gameplayParityPlayerSurfaces")
            + " gameplayAdminSurfaces=" + textValue(body, "gameplayParityAdminSurfaces")
            + " gameplayStackedBlocks=" + textValue(body, "gameplayParityStackedBlockPolicy")
            + " gameplayEffectRates=" + textValue(body, "gameplayParityEffectRatePolicy")
            + " satisPackaging=" + textValue(body, "satisPackaging")
            + " satisCoreCoupling=" + textValue(body, "satisCoreCoupling")
            + " satisAddonRemovalPolicy=" + textValue(body, "satisAddonRemovalPolicy")
            + " satisDataRetentionPolicy=" + textValue(body, "satisDataRetentionPolicy")
            + " satisCoreBootRequiresAddon=" + boolValue(body, "satisCoreBootRequiresAddon")
            + " satisCommandOwner=" + textValue(body, "satisCommandOwner")
            + " satisCrossNodeState=" + textValue(body, "satisCrossNodeStatePolicy")
            + " satisIslandMove=" + textValue(body, "satisIslandMovePolicy")
            + " satisFeatureDisable=" + textValue(body, "satisFeatureDisablePolicy")
            + " satisSuperiorSkyblock2=" + textValue(body, "satisSuperiorSkyblock2Policy")
            + " satisRecovery=" + textValue(body, "satisRecoveryPolicy")
            + " satisAddonAbsent=" + textValue(body, "satisAddonAbsentPolicy")
            + " satisDisabledRuntime=" + textValue(body, "satisDisabledRuntimePolicy")
            + " satisReinstall=" + textValue(body, "satisReinstallPolicy")
            + " satisStateAuthority=" + textValue(body, "satisStateAuthorityPolicy")
            + " satisStateStorage=" + textValue(body, "satisStateStorageConfig")
            + " satisPlayerExperience=" + textValue(body, "satisPlayerExperiencePolicy")
            + " satisFeaturePack=" + textValue(body, "satisOfficialFeaturePackPolicy")
            + " velocitySatisCommandPolicy=" + textValue(body, "velocitySatisCommandPolicy")
            + " paperSatisCommandPolicy=" + textValue(body, "paperSatisCommandPolicy")
            + " paperAgentRolePolicy=" + textValue(body, "paperAgentRolePolicy")
            + " paperLobbyRolePolicy=" + textValue(body, "paperLobbyRolePolicy")
            + " paperIslandNodeRolePolicy=" + textValue(body, "paperIslandNodeRolePolicy")
            + " velocityCommandOwner=" + textValue(body, "velocityCommandOwnershipPolicy")
            + " paperCommandFallback=" + textValue(body, "paperCommandFallbackPolicy")
            + " pluginMessaging=" + textValue(body, "pluginMessagingPolicy")
            + " pluginMessagingAllowed=" + textValue(body, "pluginMessagingAllowedUse")
            + " pluginMessagingForbidden=" + textValue(body, "pluginMessagingForbiddenUse")
            + adminText("admin-command-core-config-auth-policy-prefix", " authPolicy=") + textValue(body, "coreApiAuthPolicy")
            + adminText("admin-command-core-config-admin-policy-prefix", " adminPolicy=") + textValue(body, "adminPermissionPolicy")
            + adminText("admin-command-core-config-audit-policy-prefix", " auditPolicy=") + textValue(body, "auditLogPolicy")
            + adminText("admin-command-core-config-infra-policy-prefix", " infraPolicy=") + textValue(body, "infrastructureExposurePolicy")
            + adminText("admin-command-core-config-bind-policy-prefix", " bindPolicy=") + textValue(body, "publicBindRiskPolicy")
            + adminText("admin-command-core-config-db-type-prefix", " dbType=") + textValue(body, "configuredDatabaseType")
            + adminText("admin-command-core-config-db-type-source-prefix", " dbTypeSource=") + textValue(body, "configuredDatabaseTypeSource")
            + adminText("admin-command-core-config-db-backend-prefix", " dbBackend=") + textValue(body, "databaseBackend")
            + adminText("admin-command-core-config-jdbc-source-prefix", " jdbcSource=") + textValue(body, "jdbcUrlSource")
            + adminText("admin-command-core-config-jdbc-settings-type-prefix", " jdbcSettingsType=") + textValue(body, "effectiveJdbcSettingsType")
            + adminText("admin-command-core-config-jdbc-settings-source-prefix", " jdbcSettingsSource=") + textValue(body, "effectiveJdbcSettingsSource")
            + adminText("admin-command-core-config-jdbc-supported-prefix", " jdbcSupported=") + boolValue(body, "coreJdbcSupported")
            + adminText("admin-command-core-config-jdbc-supported-backends-prefix", " jdbcSupportedBackends=") + textValue(body, "coreJdbcSupportedBackends")
            + adminText("admin-command-core-config-setup-fallback-backends-prefix", " setupFallbackBackends=") + textValue(body, "coreSetupFallbackBackends")
            + adminText("admin-command-core-config-setup-fallback-enabled-prefix", " setupFallbackEnabled=") + boolValue(body, "coreSetupFallbackEnabled")
            + " setupFallbackSharedFirst=" + boolValue(body, "coreSetupFallbackRequireSharedBeforeLocal")
            + " setupFallbackLocalLast=" + boolValue(body, "coreSetupFallbackLocalLast")
            + " setupFallbackSafeOrder=" + textValue(body, "coreSetupFallbackProductionSafeOrder")
            + adminText("admin-command-core-config-setup-fallback-order-prefix", " setupFallbackOrder=") + textValue(body, "coreSetupFallbackOrder")
            + adminText("admin-command-core-config-setup-fallback-mode-prefix", " setupFallbackMode=") + textValue(body, "coreSetupFallbackMode")
            + " setupDbFallbackSummary=" + textValue(body, "coreSetupDatabaseFallbackSummary")
            + " setupDbProductionDurable=" + boolValue(body, "coreSetupDatabaseProductionDurable")
            + adminText("admin-command-core-config-setup-db-requested-prefix", " setupDbRequested=") + textValue(body, "coreSetupDatabaseRequestedBackend")
            + adminText("admin-command-core-config-setup-db-authority-prefix", " setupDbAuthority=") + textValue(body, "coreSetupDatabaseEffectiveAuthority")
            + " setupDbEffectiveBackend=" + textValue(body, "coreSetupDatabaseEffectiveBackend")
            + adminText("admin-command-core-config-setup-db-fallback-target-prefix", " setupDbFallbackTarget=") + textValue(body, "coreSetupDatabaseFallbackTarget")
            + adminText("admin-command-core-config-setup-db-postgresql-fallback-prefix", " setupDbPostgresqlFallback=") + boolValue(body, "coreSetupDatabasePostgresqlFallbackConfigured")
            + adminText("admin-command-core-config-setup-db-mysql-fallback-prefix", " setupDbMysqlFallback=") + boolValue(body, "coreSetupDatabaseMysqlFallbackConfigured")
            + adminText("admin-command-core-config-setup-db-mariadb-fallback-prefix", " setupDbMariadbFallback=") + boolValue(body, "coreSetupDatabaseMariadbFallbackConfigured")
            + adminText("admin-command-core-config-setup-db-core-api-fallback-prefix", " setupDbCoreApiFallback=") + boolValue(body, "coreSetupDatabaseCoreApiFallbackConfigured")
            + adminText("admin-command-core-config-setup-db-fallback-reason-prefix", " setupDbFallbackReason=") + textValue(body, "coreSetupDatabaseFallbackReason")
            + adminText("admin-command-core-config-setup-db-durable-prefix", " setupDbDurable=") + boolValue(body, "coreSetupDatabaseDurable")
            + adminText("admin-command-core-config-setup-db-operational-modes-prefix", " setupDbModes=") + textValue(body, "coreSetupDatabaseOperationalModes")
            + adminText("admin-command-core-config-setup-db-loader-prefix", " setupDbLoader=") + textValue(body, "coreSetupDatabaseConfigLoader")
            + adminText("admin-command-core-config-setup-db-paths-prefix", " setupDbPaths=") + textValue(body, "coreSetupDatabaseResolvedPathExamples")
            + adminText("admin-command-core-config-setup-db-shapes-prefix", " setupDbShapes=") + textValue(body, "coreSetupDatabaseConfigShapes")
            + adminText("admin-command-core-config-setup-db-typed-shapes-prefix", " setupDbTypedShapes=") + textValue(body, "coreSetupDatabaseTypedShapes")
            + adminText("admin-command-core-config-setup-db-typed-credentials-prefix", " setupDbTypedCredentials=") + textValue(body, "coreSetupDatabaseTypedCredentialKeys")
            + adminText("admin-command-core-config-setup-db-typed-host-mode-prefix", " setupDbTypedHostMode=") + textValue(body, "coreSetupDatabaseTypedHostMode")
            + adminText("admin-command-core-config-setup-db-typed-probe-order-prefix", " setupDbTypedProbeOrder=") + textValue(body, "coreSetupDatabaseTypedProbeOrder")
            + adminText("admin-command-core-config-setup-db-core-api-mode-prefix", " setupDbCoreApiMode=") + textValue(body, "coreSetupDatabaseCoreApiMode")
            + adminText("admin-command-core-config-setup-db-core-api-base-url-prefix", " setupDbCoreApiBaseUrl=") + textValue(body, "coreSetupDatabaseCoreApiBaseUrl")
            + adminText("admin-command-core-config-setup-db-core-api-auth-token-prefix", " setupDbCoreApiAuthToken=") + boolValue(body, "coreSetupDatabaseCoreApiAuthTokenConfigured")
            + adminText("admin-command-core-config-setup-db-core-api-admin-token-prefix", " setupDbCoreApiAdminToken=") + boolValue(body, "coreSetupDatabaseCoreApiAdminTokenConfigured")
            + adminText("admin-command-core-config-setup-db-core-api-timeout-prefix", " setupDbCoreApiTimeoutMs=") + longValue(body, "coreSetupDatabaseCoreApiTimeoutMs")
            + adminText("admin-command-core-config-setup-db-core-api-paths-prefix", " setupDbCoreApiPaths=") + textValue(body, "coreSetupDatabaseCoreApiConfigPaths")
            + adminText("admin-command-core-config-setup-db-env-prefix", " setupDbEnv=") + textValue(body, "coreSetupDatabaseEnv")
            + adminText("admin-command-core-config-setup-db-precedence-prefix", " setupDbPrecedence=") + textValue(body, "coreSetupDatabasePrecedence")
            + adminText("admin-command-core-config-setup-db-name-aliases-prefix", " setupDbNameAliases=") + textValue(body, "coreSetupDatabaseNameAliases")
            + adminText("admin-command-core-config-setup-db-jdbc-aliases-prefix", " setupDbJdbcAliases=") + textValue(body, "coreSetupDatabaseJdbcAliases")
            + adminText("admin-command-core-config-setup-db-type-inference-prefix", " setupDbTypeInference=") + textValue(body, "coreSetupDatabaseTypeInference")
            + adminText("admin-command-core-config-setup-db-auto-schema-prefix", " setupDbAutoSchema=") + boolValue(body, "coreSetupDatabaseAutoSchema")
            + adminText("admin-command-core-config-setup-db-auto-schema-policy-prefix", " setupDbAutoSchemaPolicy=") + textValue(body, "coreSetupDatabaseAutoSchemaPolicy")
            + adminText("admin-command-core-config-setup-db-auto-schema-resource-prefix", " setupDbAutoSchemaResource=") + textValue(body, "coreSetupDatabaseAutoSchemaResource")
            + adminText("admin-command-core-config-setup-db-auto-schema-history-prefix", " setupDbAutoSchemaHistory=") + textValue(body, "coreSetupDatabaseAutoSchemaHistoryTable")
            + adminText("admin-command-core-config-setup-db-auto-schema-retry-prefix", " setupDbAutoSchemaRetry=") + textValue(body, "coreSetupDatabaseAutoSchemaRetryPolicy")
            + adminText("admin-command-core-config-setup-db-auto-schema-guard-prefix", " setupDbAutoSchemaGuard=") + textValue(body, "coreSetupDatabaseAutoSchemaGuardPolicy")
            + adminText("admin-command-core-config-jdbc-fallback-prefix", " jdbcFallback=") + textValue(body, "coreJdbcFallbackReason")
            + adminText("admin-command-core-config-jdbc-fallback-active-prefix", " jdbcFallbackActive=") + boolValue(body, "coreJdbcFallbackActive")
            + adminText("admin-command-core-config-setup-fallback-effective-prefix", " setupFallbackEffective=") + boolValue(body, "coreSetupFallbackEffective")
            + adminText("admin-command-core-config-setup-fallback-safety-forced-prefix", " setupFallbackSafetyForced=") + boolValue(body, "coreSetupFallbackSafetyForced")
            + adminText("admin-command-core-config-setup-fallback-policy-prefix", " setupFallbackPolicy=") + textValue(body, "coreSetupFallbackPolicy")
            + adminText("admin-command-core-config-jdbc-fallback-status-prefix", " jdbcFallbackStatus=") + textValue(body, "coreJdbcFallbackStatus")
            + adminText("admin-command-core-config-addon-bulk-prefix", " addonBulkSave=") + boolValue(body, "addonStateBulkSaveApi")
            + adminText("admin-command-core-config-addon-bulk-global-prefix", " addonBulkGlobal=") + textValue(body, "addonStateBulkSaveGlobalEndpoint")
            + adminText("admin-command-core-config-addon-bulk-island-prefix", " addonBulkIsland=") + textValue(body, "addonStateBulkSaveIslandEndpoint")
            + adminText("admin-command-core-config-addon-table-bulk-global-prefix", " addonTableBulkGlobal=") + textValue(body, "addonStateTableKeyValueBulkSaveGlobalEndpoint")
            + adminText("admin-command-core-config-addon-table-bulk-island-prefix", " addonTableBulkIsland=") + textValue(body, "addonStateTableKeyValueBulkSaveIslandEndpoint")
            + " addonTableBulkGlobalAlias=" + textValue(body, "addonStateTableKeyValueBulkSaveGlobalAlias")
            + " addonTableBulkIslandAlias=" + textValue(body, "addonStateTableKeyValueBulkSaveIslandAlias")
            + " addonTableBulkGlobalCompat=" + textValue(body, "addonStateTableKeyValueBulkGlobalEndpoint")
            + " addonTableBulkIslandCompat=" + textValue(body, "addonStateTableKeyValueBulkIslandEndpoint")
            + " addonTableBulkGlobalMap=" + textValue(body, "addonStateTableBulkGlobalEndpoint")
            + " addonTableBulkIslandMap=" + textValue(body, "addonStateTableBulkIslandEndpoint")
            + " addonTablePrefix=" + textValue(body, "addonStateTableKeyPrefix")
            + " addonMaxKeys=" + longValue(body, "addonStateMaxKeysPerAddon")
            + " addonMaxValue=" + longValue(body, "addonStateMaxValueLength")
            + " addonGlobalCacheKey=" + textValue(body, "addonStateGlobalCacheKey")
            + " addonIslandCacheKey=" + textValue(body, "addonStateIslandCacheKey")
            + " addonCacheInvalidationApi=" + textValue(body, "addonStateCacheInvalidationApi")
            + " cacheEventFields=" + textValue(body, "cacheInvalidationEventFields")
            + " globalEventTypeKeys=" + textValue(body, "globalEventTypeKeys")
            + " globalEventRecoveryKeys=" + textValue(body, "globalEventRecoveryKeys")
            + " globalEventAddonKeys=" + textValue(body, "globalEventAddonKeys")
            + " satisCoreRequiresAddon=" + boolValue(body, "satisCoreBootRequiresAddon")
            + " satisDataRetention=" + textValue(body, "satisDataRetentionPolicy")
            + " satisCommandOwner=" + textValue(body, "satisCommandOwner")
            + adminText("admin-command-core-config-pool-prefix", " pool=") + textValue(body, "islandPool")
            + adminText("admin-command-core-config-pool-nodes-prefix", " poolNodes=") + longValue(body, "islandPoolNodeCount")
            + adminText("admin-command-core-config-pool-route-candidates-prefix", " poolRouteCandidates=") + longValue(body, "islandPoolRouteCandidateCount")
            + " poolRouteCandidateMin=" + longValue(body, "islandPoolRouteCandidateRecommendedMinimum")
            + " poolRouteCandidateMinStatus=" + textValue(body, "islandPoolRouteCandidateMinimumStatus")
            + adminText("admin-command-core-config-pool-scale-status-prefix", " poolScale=") + textValue(body, "islandPoolScaleStatus")
            + " poolScaleModel=" + textValue(body, "islandPoolScaleModel")
            + " poolElasticLimit=" + textValue(body, "islandPoolElasticLimitPolicy")
            + " poolMultiNodeReady=" + boolValue(body, "islandPoolMultiNodeReady")
            + " poolScaleGuidance=" + textValue(body, "islandPoolScaleGuidance")
            + " poolHorizontalScale=" + textValue(body, "islandPoolHorizontalScalePolicy")
            + " poolFiveSixNodes=" + textValue(body, "islandPoolFiveSixNodePolicy")
            + " poolFiveSixHealthy=" + boolValue(body, "islandPoolFiveSixNodeHealthy")
            + " placement=" + textValue(body, "islandPlacementPolicy")
            + " placementShards=" + longValue(body, "islandPlacementShardCount")
            + " placementCellsPerAxis=" + longValue(body, "islandPlacementCellsPerAxis")
            + " placementCollision=" + textValue(body, "islandPlacementCollisionPolicy")
            + " nodeHardRules=" + textValue(body, "islandNodeHardRules")
            + " nodeScoreWeights=" + textValue(body, "islandNodeScoreWeights")
            + " nodeSchema=" + textValue(body, "islandNodeSchemaColumns")
            + " existingRoutePolicy=" + textValue(body, "islandNodeExistingRoutePolicy")
            + " visitorSoftFullPolicy=" + textValue(body, "islandNodeVisitorSoftFullPolicy")
            + " routingFailureDetails=" + textValue(body, "routingFailureDetailKeys")
            + adminText("admin-command-core-config-pool-degraded-prefix", " poolDegraded=") + boolValue(body, "islandPoolDegraded")
            + " poolCandidateShortfall=" + longValue(body, "islandPoolRouteCandidateShortfall")
            + " poolCandidateBlocks=" + textValue(body, "islandPoolRouteCandidateBlockSummary")
            + " poolCandidateNodes=" + textValue(body, "islandPoolRouteCandidateNodeIds")
            + " poolBlockedNodes=" + textValue(body, "islandPoolBlockedNodeIds")
            + " poolFiveSixStatus=" + textValue(body, "islandPoolFiveSixNodeStatus")
            + adminText("admin-command-core-config-pool-duplicate-server-prefix", " poolDuplicateServers=") + longValue(body, "islandPoolDuplicateVelocityServerNameNodeCount")
            + adminText("admin-command-core-config-pool-default-identity-prefix", " poolDefaultIdentityRisk=") + longValue(body, "islandPoolDefaultNodeIdentityRiskCount")
            + adminText("admin-command-core-config-db-pool-prefix", " dbPool=") + longValue(body, "databasePoolSize")
            + adminText("admin-command-core-config-soft-full-prefix", " softFull=") + textValue(body, "softFullPolicy")
            + adminText("admin-command-core-config-hard-full-prefix", " hardFull=") + textValue(body, "hardFullPolicy")
            + adminText("admin-command-core-config-migration-prefix", " migration=") + textValue(body, "migrationPolicy")
            + adminText("admin-command-core-config-superior-migration-prefix", " superiorMigration=") + boolValue(body, "superiorSkyblock2MigrationEnabled")
            + " superiorInputOnly=" + boolValue(body, "superiorSkyblock2MigrationInputOnly")
            + " superiorRuntimeDependency=" + boolValue(body, "superiorSkyblock2RuntimeDependency")
            + " superiorRuntimePolicy=" + textValue(body, "superiorSkyblock2RuntimePolicy")
            + adminText("admin-command-core-config-ticket-ttl-prefix", " ticketTtl=") + longValue(body, "routeTicketTtlSeconds") + "s"
            + adminText("admin-command-core-config-prep-ttl-prefix", " prepTtl=") + longValue(body, "routePreparingTicketTtlSeconds") + "s"
            + adminText("admin-command-core-config-heartbeat-timeout-prefix", " heartbeatTimeout=") + longValue(body, "heartbeatTimeoutSeconds") + "s"
            + adminText("admin-command-core-config-lease-duration-prefix", " leaseDuration=") + longValue(body, "leaseDurationSeconds") + "s"
            + " redisTtl=" + textValue(body, "redisCacheTtlPolicy")
            + " redisKeys=" + textValue(body, "redisKeyPolicy")
            + " redisStreams=" + textValue(body, "redisStreamPolicy")
            + " globalEvents=" + textValue(body, "globalEventTypes")
            + " routeMetricServer=" + boolValue(body, "routeMetricsTargetServerName")
            + " routeMetricServerEvents=" + textValue(body, "routeMetricsTargetServerNameEvents")
            + " routeMetricRequestedNode=" + boolValue(body, "routeMetricsRequestedNode")
            + " routeMetricRequestedNodeEvents=" + textValue(body, "routeMetricsRequestedNodeEvents")
            + " observabilityMetrics=" + textValue(body, "observabilityRequiredMetrics")
            + " observabilityDashboard=" + textValue(body, "observabilityRequiredDashboardPanels")
            + " observabilityPolicy=" + textValue(body, "observabilityDashboardPolicy")
            + " configDoctorChecks=" + textValue(body, "configDoctorChecks")
            + " lockPolicy=" + textValue(body, "distributedLockPolicy")
            + " fencing=" + textValue(body, "fencingTokenPolicy")
            + " staleWrite=" + textValue(body, "staleWritePolicy")
            + " storageLayout=" + textValue(body, "storageLayout")
            + " storageLatest=" + textValue(body, "storageLatestPointer")
            + " storageManifest=" + textValue(body, "storageSnapshotManifest")
            + " storageBundle=" + textValue(body, "storageBundleObject")
            + " storageChecksumFile=" + textValue(body, "storageChecksumFile")
            + " storageBackup=" + textValue(body, "storageDeleteBackupPath")
            + " storageRecovery=" + textValue(body, "storageRecoveryPath")
            + " storagePortability=" + textValue(body, "storagePortabilityPolicy")
            + " storageRestoreManifestRequired=" + boolValue(body, "storageRestoreManifestRequired")
            + " storageRestoreChecksum=" + textValue(body, "storageRestoreChecksumPolicy")
            + " storageRestorePortableRequired=" + boolValue(body, "storageRestorePortableRequired")
            + " storageRestoreFormats=" + textValue(body, "storageRestoreSupportedFormats")
            + adminText("admin-command-core-config-snapshot-latest-prefix", " snapshotLatest=") + longValue(body, "snapshotKeepLatest")
            + adminText("admin-command-core-config-snapshot-retention-prefix", " snapshotRetention=") + longValue(body, "snapshotKeepHourly") + "/" + longValue(body, "snapshotKeepDaily") + "/" + longValue(body, "snapshotKeepWeekly") + "/" + longValue(body, "snapshotKeepManual")
            + adminText("admin-command-core-config-snapshot-compress-prefix", " snapshotCompress=") + boolValue(body, "snapshotCompress")
            + adminText("admin-command-core-config-snapshot-checksum-prefix", " snapshotChecksum=") + textValue(body, "snapshotChecksumAlgorithm")
            + adminText("admin-command-core-config-snapshot-triggers-prefix", " snapshotTriggers=") + textValue(body, "snapshotRequiredTriggerReasons")
            + adminText("admin-command-core-config-snapshot-trigger-policy-prefix", " snapshotTriggerPolicy=") + textValue(body, "snapshotAutomaticTriggerPolicy")
            + adminText("admin-command-core-config-snapshot-restore-prefix", " snapshotRestore=") + textValue(body, "snapshotRestorePipeline")
            + " rankingPolicy=" + textValue(body, "rankingUpdatePolicy")
            + " blockValuePolicy=" + textValue(body, "blockValuePolicy")
            + " upgradePolicy=" + textValue(body, "upgradePolicy")
            + " upgradeTypes=" + textValue(body, "upgradeTypePolicy")
            + " upgradeEconomy=" + textValue(body, "upgradeEconomyPolicy")
            + " generatorPolicy=" + textValue(body, "generatorPolicy")
            + " ss2Replacement=" + textValue(body, "superiorSkyblock2ReplacementFeatures")
            + " ss2ReplacementPolicy=" + textValue(body, "superiorSkyblock2ReplacementPolicy")
            + " ss2FeatureGate=" + textValue(body, "superiorSkyblock2ReplacementFeatureGate")
            + adminText("admin-command-core-config-mtls-prefix", " mtls=") + boolValue(body, "requireMtls")
            + adminText("admin-command-core-config-ip-allowlist-prefix", " ipAllowlist=") + boolValue(body, "ipAllowlistEnabled")
            + " securityControls=" + textValue(body, "requiredSecurityControls")
            + " pluginMessagingSecurity=" + textValue(body, "pluginMessagingSecurityPolicy");
    }

    private String addonEndpointMessage(AdminCoreConfigView body) {
        return "Addon endpoints: "
            + "bulkSave=" + boolValue(body, "addonStateBulkSaveApi")
            + " global=" + textValue(body, "addonStateBulkSaveGlobalEndpoint")
            + " island=" + textValue(body, "addonStateBulkSaveIslandEndpoint")
            + " tableGlobal=" + textValue(body, "addonStateTableKeyValueBulkSaveGlobalEndpoint")
            + " tableIsland=" + textValue(body, "addonStateTableKeyValueBulkSaveIslandEndpoint")
            + " tableGlobalAlias=" + textValue(body, "addonStateTableKeyValueBulkSaveGlobalAlias")
            + " tableIslandAlias=" + textValue(body, "addonStateTableKeyValueBulkSaveIslandAlias")
            + " tableBulkGlobal=" + textValue(body, "addonStateTableKeyValueBulkGlobalEndpoint")
            + " tableBulkIsland=" + textValue(body, "addonStateTableKeyValueBulkIslandEndpoint")
            + " tableLoadGlobal=" + textValue(body, "addonStateTableKeyValueBulkLoadGlobalEndpoint")
            + " tableLoadIsland=" + textValue(body, "addonStateTableKeyValueBulkLoadIslandEndpoint")
            + " tableMapGlobal=" + textValue(body, "addonStateTableBulkGlobalEndpoint")
            + " tableMapIsland=" + textValue(body, "addonStateTableBulkIslandEndpoint")
            + " payload=" + textValue(body, "addonStateTableKeyValueBulkSavePayload")
            + " loadPayload=" + textValue(body, "addonStateTableKeyValueBulkLoadPayload")
            + " api=" + textValue(body, "addonStateTableKeyValueBulkSaveRepositoryApi")
            + " storage=" + textValue(body, "addonStateTableKeyValueBulkSaveStorageMode")
            + " tablePrefix=" + textValue(body, "addonStateTableKeyPrefix")
            + " maxKeys=" + longValue(body, "addonStateMaxKeysPerAddon")
            + " maxValue=" + longValue(body, "addonStateMaxValueLength")
            + " globalCacheKey=" + textValue(body, "addonStateGlobalCacheKey")
            + " islandCacheKey=" + textValue(body, "addonStateIslandCacheKey")
            + " invalidationApi=" + textValue(body, "addonStateCacheInvalidationApi")
            + " cacheEventFields=" + textValue(body, "cacheInvalidationEventFields")
            + " eventTypeKeys=" + textValue(body, "globalEventTypeKeys")
            + " eventRecoveryKeys=" + textValue(body, "globalEventRecoveryKeys")
            + " eventAddonKeys=" + textValue(body, "globalEventAddonKeys")
            + " fallback=" + textValue(body, "addonStateTableKeyValueBulkSaveFallback")
            + " loadFallback=" + textValue(body, "addonStateTableKeyValueBulkLoadFallback");
    }

    private String eventListMessage(AdminEventStreamView stream) {
        if (stream.events().isEmpty()) {
            return adminText("admin-command-events-empty", "Events: empty");
        }
        List<String> entries = new ArrayList<>();
        for (AdminEventView event : stream.events().stream().limit(10).toList()) {
            String islandId = event.fields().getOrDefault("islandId", "");
            String ticketId = event.fields().getOrDefault("ticketId", "");
            String playerUuid = event.fields().getOrDefault("playerUuid", "");
            String action = event.fields().getOrDefault("action", "");
            String reason = event.fields().getOrDefault("reason", "");
            String requestedNode = event.fields().getOrDefault("requestedNode", "");
            String clearedSession = event.fields().getOrDefault("clearedSession", "");
            String clearedTicket = event.fields().getOrDefault("clearedTicket", "");
            String nodeId = event.fields().getOrDefault("nodeId", "");
            if (nodeId.isBlank()) {
                nodeId = event.fields().getOrDefault("targetNode", "");
            }
            entries.add((event.type().isBlank() ? "UNKNOWN_EVENT" : event.type())
                + (islandId.isBlank() ? "" : adminText("admin-command-event-island-prefix", " island=") + islandId)
                + (ticketId.isBlank() ? "" : adminText("admin-command-event-ticket-prefix", " ticket=") + shortId(ticketId))
                + (playerUuid.isBlank() ? "" : adminText("admin-command-event-player-prefix", " player=") + shortId(playerUuid))
                + (action.isBlank() ? "" : adminText("admin-command-event-action-prefix", " action=") + action)
                + (reason.isBlank() ? "" : adminText("admin-command-event-reason-prefix", " reason=") + reason)
                + (requestedNode.isBlank() ? "" : adminText("admin-command-event-requested-node-prefix", " requestedNode=") + requestedNode)
                + (clearedSession.isBlank() ? "" : adminText("admin-command-event-session-prefix", " session=") + clearedSession)
                + (clearedTicket.isBlank() ? "" : adminText("admin-command-event-ticket-cleared-prefix", " ticketCleared=") + clearedTicket)
                + (nodeId.isBlank() ? "" : adminText("admin-command-event-node-prefix", " node=") + nodeId)
                + (event.occurredAt().isBlank() ? "" : adminText("admin-command-event-at-prefix", " at=") + event.occurredAt()));
        }
        return entries.isEmpty() ? adminText("admin-command-events-empty", "Events: empty") : adminText("admin-command-events-prefix", "Events: ") + String.join(" | ", entries);
    }

    private String auditListMessage(List<AdminAuditEntryView> audit) {
        if (audit.isEmpty()) {
            return adminText("admin-command-audit-empty", "Audit: empty");
        }
        List<String> entries = new ArrayList<>();
        for (AdminAuditEntryView entry : audit.stream().limit(10).toList()) {
            entries.add((entry.action().isBlank() ? "UNKNOWN_ACTION" : entry.action())
                + (entry.targetType().isBlank() && entry.targetId().isBlank() ? "" : adminText("admin-command-audit-target-prefix", " target=") + entry.targetType() + ":" + entry.targetId())
                + (entry.actorType().isBlank() ? "" : adminText("admin-command-audit-actor-prefix", " actor=") + entry.actorType())
                + (entry.createdAt().isBlank() ? "" : adminText("admin-command-audit-at-prefix", " at=") + entry.createdAt()));
        }
        return entries.isEmpty() ? adminText("admin-command-audit-empty", "Audit: empty") : adminText("admin-command-audit-prefix", "Audit: ") + String.join(" | ", entries);
    }

    private String routeDebugMessage(AdminRouteDebugView debug) {
        List<String> sessionEntries = debug.sessions().stream().limit(5).map(this::routeSessionSummary).toList();
        List<String> ticketEntries = debug.tickets().stream().limit(5).map(this::ticketSummary).toList();
        return adminText("admin-command-routes-sessions-prefix", "Routes: sessions=") + debug.sessions().size()
            + (sessionEntries.isEmpty() ? "" : " [" + String.join(" | ", sessionEntries) + "]")
            + adminText("admin-command-routes-tickets-prefix", " tickets=") + debug.tickets().size()
            + (ticketEntries.isEmpty() ? "" : " [" + String.join(" | ", ticketEntries) + "]");
    }

    private String routeTicketMessage(java.util.Optional<AdminRouteTicketView> ticket) {
        if (ticket.isEmpty()) {
            return adminText("admin-command-route-ticket-not-found", "Route ticket: not found");
        }
        return adminText("admin-command-route-ticket-prefix", "Route ticket: ") + ticketSummary(ticket.get());
    }

    private String routeClearMessage(AdminRouteClearView result) {
        return adminText("admin-command-route-clear-session-prefix", "Route clear: session=") + result.clearedSession() + adminText("admin-command-route-clear-ticket-prefix", " ticket=") + result.clearedTicket() + (result.reason().isBlank() ? "" : adminText("admin-command-route-clear-reason-prefix", " reason=") + result.reason());
    }

    private String snapshotListMessage(List<CoreGuiViews.SnapshotView> snapshots) {
        if (snapshots.isEmpty()) {
            return adminText("admin-command-snapshots-empty", "Snapshots: empty");
        }
        List<String> entries = new ArrayList<>();
        for (CoreGuiViews.SnapshotView snapshot : snapshots.stream().limit(20).toList()) {
            if (snapshot.snapshotNo() > 0L) {
                entries.add("#" + snapshot.snapshotNo()
                    + (snapshot.reason().isBlank() ? "" : " " + snapshot.reason())
                    + adminText("admin-command-snapshot-size-prefix", " size=") + snapshot.sizeBytes()
                    + (snapshot.checksum().isBlank() ? "" : adminText("admin-command-snapshot-checksum-prefix", " checksum=") + shortChecksum(snapshot.checksum()))
                    + (snapshot.storagePath().isBlank() ? "" : adminText("admin-command-snapshot-path-prefix", " path=") + snapshot.storagePath())
                    + (snapshot.createdAt().isBlank() ? "" : adminText("admin-command-snapshot-at-prefix", " at=") + snapshot.createdAt()));
            }
        }
        return entries.isEmpty() ? adminText("admin-command-snapshots-empty", "Snapshots: empty") : adminText("admin-command-snapshots-prefix", "Snapshots: ") + String.join(" | ", entries);
    }

    private String routeSessionSummary(AdminRouteSessionView session) {
        return shortId(session.playerUuid())
            + adminText("admin-command-route-session-ticket-prefix", " ticket=") + shortId(session.ticketId())
            + (session.targetNode().isBlank() ? "" : adminText("admin-command-route-session-node-prefix", " node=") + session.targetNode())
            + (session.targetServerName().isBlank() ? "" : adminText("admin-command-route-session-server-prefix", " server=") + session.targetServerName())
            + (session.expiresAt().isBlank() ? "" : adminText("admin-command-route-session-expires-prefix", " expires=") + session.expiresAt());
    }

    private String ticketSummary(AdminRouteTicketView ticket) {
        String targetName = !ticket.homeName().isBlank() ? ticket.homeName() : ticket.warpName();
        return shortId(ticket.ticketId())
            + " " + (ticket.action().isBlank() ? "UNKNOWN" : ticket.action())
            + " " + (ticket.state().isBlank() ? "UNKNOWN" : ticket.state())
            + (ticket.targetType().isBlank() && targetName.isBlank() ? "" : " target=" + (ticket.targetType().isBlank() ? "-" : ticket.targetType()) + (targetName.isBlank() ? "" : ":" + targetName))
            + (ticket.islandId().isBlank() ? "" : adminText("admin-command-route-ticket-island-prefix", " island=") + shortId(ticket.islandId()))
            + (ticket.targetNode().isBlank() ? "" : adminText("admin-command-route-ticket-node-prefix", " node=") + ticket.targetNode());
    }

    private String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    private String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "";
        }
        return checksum.length() > 12 ? checksum.substring(0, 12) : checksum;
    }

    private int nodeIslandLimit(String[] args) {
        return args.length > 3 ? (int) Math.max(1L, Math.min(number(args[3], 50L), 200L)) : 50;
    }

    private String adminNodeSummaryMessage(String label, AdminNodeSummaryView summary) {
        return label + (summary.text().isBlank() ? "" : ": " + summary.text());
    }

    private String nodeInfoMessage(CoreGuiViews.NodeSummaryView node) {
        return (node.nodeId().isBlank() ? adminText("admin-command-node-default-id", "node") : node.nodeId())
            + " " + (node.state().isBlank() ? "UNKNOWN" : node.state())
            + (node.pool().isBlank() ? "" : " pool=" + node.pool())
            + adminText("admin-command-node-players-prefix", " players=") + node.players() + "/" + node.softPlayerCap() + "/" + node.hardPlayerCap()
            + adminText("admin-command-node-islands-prefix", " islands=") + node.activeIslands() + "/" + node.maxActiveIslands()
            + adminText("admin-command-node-queue-prefix", " queue=") + node.activationQueue() + "/" + node.maxActivationQueue()
            + (node.mspt().isBlank() ? "" : adminText("admin-command-node-mspt-prefix", " mspt=") + node.mspt())
            + adminText("admin-command-node-heartbeat-prefix", " heartbeatAge=") + heartbeatAge(node.secondsSinceHeartbeat()) + (node.stale() ? " stale=true" : "")
            + adminText("admin-command-node-storage-prefix", " storage=") + (node.storageAvailable() ? "OK" : "DOWN")
            + (node.storagePrimaryDegraded() ? " degraded=true" : "")
            + adminText("admin-command-node-storage-retry-prefix", " retry=") + node.storageSaveRetryQueueTotal()
            + adminText("admin-command-node-shutdown-safe-prefix", " shutdownSafe=") + node.shutdownSafe()
            + (node.allocationBlockReason().isBlank() ? "" : adminText("admin-command-node-block-prefix", " block=") + node.allocationBlockReason());
    }

    private String nodeActionSummaryMessage(String label, String requestedNodeId, AdminNodeActionView result) {
        String effectiveNodeId = result.nodeId().isBlank() ? requestedNodeId : result.nodeId();
        String status = result.accepted() ? adminText("admin-command-node-action-accepted", "accepted") : adminText("admin-command-node-action-rejected", "rejected");
        String operation = result.operation().isBlank() ? "" : " operation=" + result.operation();
        String code = result.code().isBlank() ? "" : adminText("admin-command-node-action-code-prefix", " code=") + result.code();
        return label + ": " + status + adminText("admin-command-node-action-node-prefix", " node=") + effectiveNodeId + operation + code;
    }

    private boolean boolValue(AdminCoreConfigView config, String field) {
        return config != null && config.bool(field);
    }

    private long longValue(AdminCoreConfigView config, String field) {
        return config == null ? 0L : config.number(field);
    }

    private String seconds(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String heartbeatAge(long seconds) {
        return seconds < 0L ? "unknown" : seconds + "s";
    }

    private void message(CommandSender sender, String text) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> sender.sendMessage(text));
    }

    private String adminText(String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private void usage(CommandSender sender, String label, int page) {
        List<String> labelledCommands = helpCommands().stream()
            .map(command -> command.replaceFirst("^ciadmin", label))
            .toList();
        CommandListPolicy.Page commandPage = CommandListPolicy.page(labelledCommands, page, label + " command list");
        sender.sendMessage(adminText("admin-command-list-title", "CloudIslands 관리자 명령어 목록 ") + commandPage.page() + "/" + commandPage.pages() + " commands=" + commandPage.rangeSummary() + adminText("admin-command-list-suffix", CommandListPolicy.HEADER_SUFFIX));
        for (String line : CommandListPolicy.displayLines(commandPage)) {
            sender.sendMessage(line);
        }
    }

    private boolean hasAdminAccess(CommandSender sender, String[] args) {
        if (sender.hasPermission("cloudislands.admin")) {
            return true;
        }
        String permission = adminPermission(args);
        return !permission.isBlank() && (sender.hasPermission(permission) || adminPermissionFallbacks(args).stream().anyMatch(sender::hasPermission));
    }

    private String adminPermission(String[] args) {
        if (args.length == 0) {
            return "cloudislands.admin.status";
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("help") || root.equals("commands") || root.equals("command") || root.equals("command-list") || root.equals("명령어") || root.equals("명령어목록")) {
            return "cloudislands.admin.status";
        }
        if (root.equals("template")) {
            root = "templates";
        }
        if (root.equals("migrate")) {
            return superiorSkyblock2MigrationEnabled() ? "cloudislands.admin.migrate-superiorskyblock2" : "";
        }
        if ((root.equals("migrate-superiorskyblock2") || root.equals("migrate")) && !superiorSkyblock2MigrationEnabled()) {
            return "";
        }
        if (root.equals("island") && args.length > 1 && args[1].equalsIgnoreCase("inspect")) {
            return "cloudislands.admin.island.inspect";
        }
        if (root.equals("bonus") || root.equals("addbonus") || root.equals("syncbonus")) {
            return "cloudislands.admin.upgrade-rules";
        }
        return switch (root) {
            case "status", "dashboard", "doctor", "setup", "config", "cache", "addons", "integrations", "node", "island", "player", "message", "title", "cmd", "fly", "spy", "openmenu", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "diagnostics", "support-bundle", "block-values", "upgrade-rules", "setblockamount", "seteffect", "setcropgrowth", "setmobdrops", "setspawnerrates", "setspawn", "templates", "migrate-superiorskyblock2", "reload" -> "cloudislands.admin." + root;
            default -> "";
        };
    }

    private static boolean migrationAlias(String[] args) {
        return args != null && args.length >= 2 && args[0].equalsIgnoreCase("migrate") && args[1].equalsIgnoreCase("superiorskyblock2");
    }

    private static String[] migrationArgs(String[] args) {
        if (!migrationAlias(args)) {
            return args;
        }
        String[] normalized = new String[Math.max(1, args.length - 1)];
        normalized[0] = "migrate-superiorskyblock2";
        if (args.length > 2) {
            System.arraycopy(args, 2, normalized, 1, args.length - 2);
        }
        return normalized;
    }

    private List<String> adminPermissionFallbacks(String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("inspect")) {
            return List.of("cloudislands.admin.island");
        }
        return List.of();
    }

    private boolean isHelpRequest(String[] args) {
        if (args.length == 0) {
            return false;
        }
        String first = args[0].toLowerCase(java.util.Locale.ROOT);
        if (first.equals("help") || first.equals("commands") || first.equals("command") || first.equals("command-list") || first.equals("명령어") || first.equals("명령어목록")) {
            return true;
        }
        return first.equals("command") && args.length > 1 && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"));
    }

    private int helpPage(String[] args) {
        if (args.length > 2 && isCommandListRoot(args[0]) && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return (int) number(args[2], 1L);
        }
        if (args.length > 1) {
            return (int) number(args[1], 1L);
        }
        return 1;
    }

    private boolean isCommandListRoot(String value) {
        return value.equalsIgnoreCase("command")
            || value.equalsIgnoreCase("commands")
            || value.equalsIgnoreCase("command-list")
            || value.equals("명령어")
            || value.equals("명령어목록");
    }

    private boolean gameplayModifierCommand(String value) {
        return value.equalsIgnoreCase("setblockamount")
            || value.equalsIgnoreCase("seteffect")
            || rateModifierCommand(value);
    }

    private boolean rateModifierCommand(String value) {
        return value.equalsIgnoreCase("setcropgrowth")
            || value.equalsIgnoreCase("setmobdrops")
            || value.equalsIgnoreCase("setspawnerrates");
    }

    private void auditAdminSetSpawn(CommandSender sender, SpawnUpdateResult result) {
        String actor = sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
        agent.plugin().getLogger().warning(
            "CloudIslands admin setspawn"
                + " actor=" + actor
                + " world=" + result.worldName()
                + " x=" + formatCoordinate(result.x())
                + " y=" + formatCoordinate(result.y())
                + " z=" + formatCoordinate(result.z())
                + " yaw=" + formatCoordinate(result.yaw())
                + " accepted=" + result.accepted()
        );
    }

    private void auditAdminOpenMenu(CommandSender sender, Player target, String menuId, boolean opened) {
        String actor = sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
        agent.plugin().getLogger().warning(
            "CloudIslands admin openmenu"
                + " actor=" + actor
                + " target=" + target.getUniqueId()
                + " targetName=" + target.getName()
                + " menu=" + menuId
                + " opened=" + opened
        );
    }

    private void auditAdminSpy(CommandSender sender, Player target, boolean enabled) {
        String actor = sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
        agent.plugin().getLogger().warning(
            "CloudIslands admin spy"
                + " actor=" + actor
                + " target=" + target.getUniqueId()
                + " targetName=" + target.getName()
                + " enabled=" + enabled
        );
    }

    private String gameplayModifierLimitKey(String command) {
        if (command.equalsIgnoreCase("setcropgrowth")) {
            return "RATE:CROP_GROWTH";
        }
        if (command.equalsIgnoreCase("setmobdrops")) {
            return "RATE:MOB_DROPS";
        }
        if (command.equalsIgnoreCase("setspawnerrates")) {
            return "RATE:SPAWNER_RATES";
        }
        return "RATE:UNKNOWN";
    }

    private String bonusLimitKey(String key) {
        return BONUS_LIMIT_PREFIX + normalizeGameplayKey(key);
    }

    private String adminLimitKey(String command) {
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "setbanklimit", "addbanklimit" -> "BANK";
            case "setentitylimit", "addentitylimit" -> "ENTITY";
            case "setteamlimit", "addteamlimit" -> "MEMBERS";
            case "setcooplimit", "addcooplimit" -> GameplayParityPolicy.roleLimitKey("TRUSTED");
            case "setwarpslimit", "addwarpslimit" -> "WARPS";
            case "setsize", "addsize" -> "SIZE";
            default -> "";
        };
    }

    private String gameplayModifierLabel(String command) {
        if (command.equalsIgnoreCase("setcropgrowth")) {
            return "Set crop growth";
        }
        if (command.equalsIgnoreCase("setmobdrops")) {
            return "Set mob drops";
        }
        if (command.equalsIgnoreCase("setspawnerrates")) {
            return "Set spawner rates";
        }
        return "Set gameplay modifier";
    }

    private String normalizeGameplayKey(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.:-]+", "_");
        return normalized.isBlank() ? "UNKNOWN" : normalized;
    }

    private IslandPermission islandPermission(String value) {
        try {
            return IslandPermission.valueOf(normalizeGameplayKey(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private Boolean strictBooleanArgument(String value) {
        if (value == null) {
            return null;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("allow") || value.equalsIgnoreCase("on") || value.equals("1") || value.equals("허용")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("deny") || value.equalsIgnoreCase("off") || value.equals("0") || value.equals("거부")) {
            return false;
        }
        return null;
    }

    private IslandFlag islandFlag(String value) {
        try {
            return IslandFlag.valueOf(normalizeGameplayKey(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private UUID uuid(CommandSender sender, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(adminText("admin-command-uuid-invalid", "UUID 형식이 올바르지 않습니다: ") + value);
            return null;
        }
    }

    private UUID uuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private CompletableFuture<UUID> resolveIslandUuid(CommandSender sender, String value) {
        UUID parsed = uuidOrNull(value);
        if (parsed != null) {
            return CompletableFuture.completedFuture(parsed);
        }
        return coreApiClient.adminIslands().infoByName(value).thenApply(island -> {
            UUID islandId = uuidOrNull(island.islandId());
            if (islandId == null) {
                message(sender, adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + value);
            }
            return islandId;
        });
    }

    private CompletableFuture<UUID> resolvePlayerUuid(CommandSender sender, String value) {
        return findPlayerUuid(value).thenApply(playerUuid -> {
            if (playerUuid == null) {
                message(sender, adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + value);
            }
            return playerUuid;
        });
    }

    private CompletableFuture<UUID> findPlayerUuid(String value) {
        Player online = agent.plugin().getServer().getPlayerExact(value);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        try {
            return CompletableFuture.completedFuture(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return coreApiClient.playerProfiles().findByName(value).thenApply(profile -> uuidOrNull(profile.playerUuid()));
        }
    }

    private String textValue(AdminCoreConfigView config, String field) {
        return config == null ? "" : config.text(field);
    }

    private long number(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int boundedInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private Double decimalArgument(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private boolean booleanArgument(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1") || normalized.equals("enable") || normalized.equals("enabled") || normalized.equals("켜기") || normalized.equals("활성")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0") || normalized.equals("disable") || normalized.equals("disabled") || normalized.equals("끄기") || normalized.equals("비활성")) {
            return false;
        }
        return fallback;
    }

    private boolean isBooleanArgument(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("true")
            || normalized.equals("yes")
            || normalized.equals("on")
            || normalized.equals("1")
            || normalized.equals("enable")
            || normalized.equals("enabled")
            || normalized.equals("켜기")
            || normalized.equals("활성")
            || normalized.equals("false")
            || normalized.equals("no")
            || normalized.equals("off")
            || normalized.equals("0")
            || normalized.equals("disable")
            || normalized.equals("disabled")
            || normalized.equals("끄기")
            || normalized.equals("비활성");
    }

    private boolean isSpyModeArgument(String value) {
        return value != null && (value.equalsIgnoreCase("toggle") || isBooleanArgument(value));
    }

    private boolean confirmed(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        String value = args[args.length - 1];
        return value.equalsIgnoreCase("--confirm") || value.equalsIgnoreCase("confirm") || booleanArgument(value, false);
    }

    private String joined(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private String joinedExcludingTrailingConfirm(String[] args, int start) {
        int end = args == null ? 0 : args.length;
        if (end > start) {
            String last = args[end - 1];
            if (last.equalsIgnoreCase("--confirm") || last.equalsIgnoreCase("confirm") || booleanArgument(last, false)) {
                end--;
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private TitlePayload titlePayload(String[] args, int start) {
        String title = args.length > start ? args[start] : "";
        String subtitle = args.length > start + 1 ? joined(args, start + 1) : "";
        return new TitlePayload(title, subtitle);
    }

    private List<String> matches(List<String> values, String typed) {
        String normalized = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (normalized.isBlank() || value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : agent.plugin().getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> worldNames() {
        return worldSpawnGateway().worldNames();
    }

    private AdminWorldSpawnGateway worldSpawnGateway() {
        return new AdminWorldSpawnGateway(agent.plugin());
    }

    private List<String> addonIds() {
        CloudIslandsApi api = CloudIslandsProvider.get().orElse(null);
        if (api == null) {
            return List.of();
        }
        try {
            return api.addons().list().join().stream()
                .map(CloudIslandsAddonSnapshot::id)
                .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<String> addonFeatureKeys(String addonId) {
        CloudIslandsApi api = CloudIslandsProvider.get().orElse(null);
        if (api == null) {
            return List.of();
        }
        try {
            return api.addons().get(addonId).join()
                .map(this::addonFeatureKeys)
                .filter(features -> !features.isEmpty())
                .orElse(List.of());
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<String> addonFeatureKeys(CloudIslandsAddonSnapshot addon) {
        Set<String> features = new java.util.TreeSet<>(addon.features().keySet());
        addon.featureAliases().forEach((alias, _canonical) -> features.add(alias));
        addon.featureDependencies().forEach((feature, required) -> {
            features.add(feature);
            features.add(required);
        });
        return List.copyOf(features);
    }

    private boolean addonFeatureKnown(CloudIslandsAddonSnapshot addon, String feature) {
        String requested = feature == null ? "" : feature.trim();
        if (addon.features().containsKey(requested)) {
            return true;
        }
        String canonical = addon.featureAliases().get(requested);
        return canonical != null && addon.features().containsKey(canonical)
            || addon.featureDependencies().containsKey(requested)
            || addon.featureDependencies().containsValue(requested);
    }

    private String addonFeatureMessage(CloudIslandsAddonSnapshot addon, String addonId, String feature) {
        boolean configured = addon.configuredFeatureEnabled(feature, true);
        boolean effective = addon.enabled() && addon.featureEnabled(feature, true);
        return adminText("admin-command-addons-feature-prefix", "Addon feature: ") + addonId + " " + feature
            + canonicalFeatureSuffix(addon, feature)
            + adminText("admin-command-addons-configured-prefix", " configured=") + configured
            + adminText("admin-command-addons-enabled-prefix", " enabled=") + effective
            + addonFeatureDependencySuffix(addon, feature);
    }

    private String addonFeatureDependencySuffix(CloudIslandsAddonSnapshot addon, String feature) {
        String requested = feature == null ? "" : feature.trim();
        String required = addon.featureDependencies().get(requested);
        if (required == null) {
            String canonical = addon.featureAliases().get(requested);
            required = addon.featureDependencies().get(canonical == null ? requested : canonical);
        }
        if (required == null) {
            return "";
        }
        boolean requiredEnabled = addon.featureEnabled(required, true);
        return adminText("admin-command-addons-required-prefix", " requires=") + required
            + adminText("admin-command-addons-required-enabled-prefix", " requiredEnabled=") + requiredEnabled
            + adminText("admin-command-addons-dependency-blocked-prefix", " dependencyBlocked=") + (!requiredEnabled);
    }

    private String canonicalFeatureSuffix(CloudIslandsAddonSnapshot addon, String feature) {
        String canonical = addon.featureAliases().get(feature == null ? "" : feature.trim());
        if (canonical == null || canonical.equals(feature)) {
            return "";
        }
        return " canonical=" + canonical;
    }
}
