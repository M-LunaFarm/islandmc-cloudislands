package kr.lunaf.cloudislands.paper.admin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
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
import kr.lunaf.cloudislands.coreclient.BlockValueActionView;
import kr.lunaf.cloudislands.coreclient.BlockValueView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.IslandLifecycleActionView;
import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView;
import kr.lunaf.cloudislands.coreclient.JobActionView;
import kr.lunaf.cloudislands.coreclient.JobRecoveryView;
import kr.lunaf.cloudislands.coreclient.JobView;
import kr.lunaf.cloudislands.coreclient.PlayerProfileView;
import kr.lunaf.cloudislands.coreclient.ProgressionRankingEntryView;
import kr.lunaf.cloudislands.coreclient.TemplateView;
import kr.lunaf.cloudislands.coreclient.UpgradeRuleView;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class AdminCommandBackend implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = AdminCommandCatalog.ROOT_COMMANDS;
    private static final List<String> CONFIG_COMMANDS = AdminCommandCatalog.CONFIG_COMMANDS;
    private static final List<String> CACHE_COMMANDS = AdminCommandCatalog.CACHE_COMMANDS;
    private static final List<String> ADDON_COMMANDS = AdminCommandCatalog.ADDON_COMMANDS;
    private static final List<String> ADDON_FEATURES = AdminCommandCatalog.ADDON_FEATURES;
    private static final List<String> NODE_COMMANDS = AdminCommandCatalog.NODE_COMMANDS;
    private static final List<String> ISLAND_COMMANDS = AdminCommandCatalog.ISLAND_COMMANDS;
    private static final List<String> PLAYER_COMMANDS = AdminCommandCatalog.PLAYER_COMMANDS;
    private static final List<String> JOB_COMMANDS = AdminCommandCatalog.JOB_COMMANDS;
    private static final List<String> ROUTE_COMMANDS = AdminCommandCatalog.ROUTE_COMMANDS;
    private static final List<String> DIAGNOSTICS_COMMANDS = AdminCommandCatalog.DIAGNOSTICS_COMMANDS;
    private static final List<String> RANKING_COMMANDS = AdminCommandCatalog.RANKING_COMMANDS;
    private static final List<String> BLOCK_VALUE_COMMANDS = AdminCommandCatalog.BLOCK_VALUE_COMMANDS;
    private static final List<String> BLOCK_VALUE_MATERIALS = AdminCommandCatalog.BLOCK_VALUE_MATERIALS;
    private static final List<String> TEMPLATE_COMMANDS = AdminCommandCatalog.TEMPLATE_COMMANDS;
    private static final List<String> MIGRATION_COMMANDS = AdminCommandCatalog.MIGRATION_COMMANDS;
    private static final List<String> NODE_DANGER_REASONS = AdminCommandCatalog.NODE_DANGER_REASONS;
    private static final List<String> HELP_COMMANDS = AdminCommandCatalog.HELP_COMMANDS;
    private static final List<String> MIGRATION_HELP_COMMANDS = AdminCommandCatalog.MIGRATION_HELP_COMMANDS;
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
            this::sendCommandUsage
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
        if (args[0].equalsIgnoreCase("doctor")) {
            return handleDoctor(sender);
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
            sender.sendMessage(integrationStatusMessage());
            return true;
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
            run(sender, "Events list", coreApiClient.adminEvents().list(100).thenApply(this::eventListMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("audit")) {
            run(sender, "Audit logs", coreApiClient.adminAudit().list(100).thenApply(this::auditListMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("metrics")) {
            run(sender, "Core metrics", coreApiClient.adminMetrics().summary().thenApply(this::metricsMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("config")) {
            return configHandler.handle(sender, args);
        }
        if (args[0].equalsIgnoreCase("storage")) {
            run(sender, "Storage status", coreApiClient.adminStorage().status().thenApply(this::storageStatusMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("diagnostics")) {
            return handleDiagnostics(sender, args);
        }
        if (args[0].equalsIgnoreCase("block-values")) {
            return handleBlockValues(sender, args);
        }
        if (args[0].equalsIgnoreCase("upgrade-rules")) {
            run(sender, "Upgrade rules", coreApiClient.progression().upgradeRules().thenApply(this::upgradeRulesMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) {
            return handleTemplate(sender, args);
        }
        if (args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            return migrationHandler.handle(sender, args);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return matches(CONFIG_COMMANDS, args[1]);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            return matches(PLAYER_COMMANDS, args[1]);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("diagnostics")) {
            return matches(DIAGNOSTICS_COMMANDS, args[1]);
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
        if (args.length == 4 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            return matches(List.of("1.0", "10.0", "100.0"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            return matches(List.of("1", "10", "100"), args[4]);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            return matches(List.of("0", "64", "256"), args[5]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates"))) {
            return matches(TEMPLATE_COMMANDS, args[1]);
        }
        if (args.length == 5 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            return matches(List.of("true", "false", "enabled", "disabled", "enable", "disable", "on", "off", "활성", "비활성"), args[4]);
        }
        if (args.length == 6 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            return matches(List.of("1.0.0", "1.21.0", "1.21.4"), args[5]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            if (!superiorSkyblock2MigrationEnabled()) {
                return List.of();
            }
            return matches(MIGRATION_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            if (!superiorSkyblock2MigrationEnabled()) {
                return List.of();
            }
            return matches(List.of("plugins/SuperiorSkyblock2"), args[2]);
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
        if (args.length == 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("ticket")) {
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
            configHandler.reloadRuntimeConfig();
            if (args.length > 2) {
                run(sender, "Addon reload", api.addons().refresh(args[2]).thenApply(addon -> addon.map(this::addonInfoMessage).orElse(adminText("admin-command-addons-not-found", "Addon: not found ") + args[2])));
            } else {
                run(sender, "Addons reload", api.addons().refreshAll().thenApply(this::addonListMessage));
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
        CompletableFuture<DiagnosticSection> configValidation = CompletableFuture.completedFuture(new DiagnosticSection(configHandler.validationDiagnosticSection()));
        CompletableFuture<DiagnosticSection> effectiveConfig = CompletableFuture.completedFuture(new DiagnosticSection(configHandler.effectiveConfigDiagnosticSection()));
        run(sender, "Diagnostics export", CompletableFuture.allOf(config, metrics, storage, nodes, heartbeatLag, jobs, routes, audit, configValidation, effectiveConfig)
            .thenApply(_ignored -> writeDiagnostics(List.of(config.join(), metrics.join(), storage.join(), nodes.join(), heartbeatLag.join(), jobs.join(), routes.join(), audit.join(), configValidation.join(), effectiveConfig.join(), runtimeCompatibilityDiagnosticSection(), integrationsDiagnosticSection()))));
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

    private String writeDiagnostics(List<DiagnosticSection> sections) {
        try {
            Path directory = agent.plugin().getDataFolder().toPath().resolve("diagnostics");
            Files.createDirectories(directory);
            String timestamp = Instant.now().toString().replace(':', '-');
            Path report = directory.resolve("cloudislands-diagnostics-" + timestamp + ".txt");
            StringBuilder builder = new StringBuilder();
            builder.append("CloudIslands diagnostics export\n");
            builder.append("generatedAt=").append(Instant.now()).append('\n');
            builder.append("nodeId=").append(nodeId).append('\n');
            builder.append("agentRole=").append(agent.role()).append('\n');
            builder.append("pluginVersion=").append(agent.plugin().getPluginMeta().getVersion()).append('\n');
            builder.append("onlinePlayers=").append(agent.plugin().getServer().getOnlinePlayers().size()).append("\n\n");
            for (DiagnosticSection section : sections) {
                builder.append(section.content()).append('\n');
            }
            Files.writeString(report, builder.toString());
            return adminText("admin-command-diagnostics-exported-prefix", "Diagnostics exported: ") + report;
        } catch (IOException exception) {
            return adminText("admin-command-diagnostics-export-failed", "Diagnostics export failed: ") + exception.getMessage();
        }
    }

    private String heartbeatLagDiagnosticBody(AdminNodeSummaryView nodes) {
        return "nodeCount=" + nodes.nodeCount() + '\n'
            + "routeCandidateCount=" + nodes.routeCandidateCount() + '\n'
            + "staleNodeCount=" + nodes.staleNodeCount() + '\n'
            + "heartbeatTimeoutSeconds=" + nodes.heartbeatTimeoutSeconds() + '\n';
    }

    private static String redactDiagnostic(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .replaceAll("(?i)(token|secret|password|authorization|accessKey|secretKey)\\\"?\\s*[:=]\\s*\\\"?[^,\\n\\r\\\"]+", "$1=***")
            .replaceAll("ghp_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+", "***");
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

    private boolean handleDoctor(CommandSender sender) {
        CompletableFuture<CharSequence> metrics = doctorPart("metrics", coreApiClient.adminMetrics().summary().thenApply(this::metricsMessage));
        CompletableFuture<CharSequence> storage = doctorPart("storage", coreApiClient.adminStorage().status().thenApply(this::storageStatusMessage));
        CompletableFuture<CharSequence> nodes = doctorPart("nodes", coreApiClient.adminNodes().listNodesSummary().thenApply(summary ->
            adminNodeSummaryMessage("Nodes", summary) + " / " + heartbeatLagDiagnosticBody(summary).replace('\n', ' ').trim()));
        CompletableFuture<CharSequence> jobs = doctorPart("jobs", coreApiClient.jobs().list().thenApply(this::jobListMessage));
        CompletableFuture<CharSequence> routes = doctorPart("route-debug", coreApiClient.adminRoutes().debug(new UUID(0L, 0L)).thenApply(this::routeDebugMessage));
        CompletableFuture<CharSequence> integrations = CompletableFuture.completedFuture("integrations=" + integrationStatusMessage());
        run(sender, "Doctor", CompletableFuture.allOf(metrics, storage, nodes, jobs, routes, integrations)
            .thenApply(_ignored -> doctorMessage(List.of(metrics.join(), storage.join(), nodes.join(), jobs.join(), routes.join(), integrations.join()))));
        return true;
    }

    private CompletableFuture<CharSequence> doctorPart(String label, CompletableFuture<? extends CharSequence> future) {
        return future.handle((body, error) -> {
            if (error != null) {
                return label + "=ERROR(" + error.getClass().getSimpleName() + ")";
            }
            String text = body == null ? "" : body.toString().replace('\n', ' ').trim();
            return label + "=" + (text.isBlank() ? "empty" : text);
        });
    }

    private CharSequence doctorMessage(List<CharSequence> parts) {
        List<String> renderedParts = parts.stream().map(CharSequence::toString).toList();
        return "Doctor: role=" + agent.role()
            + " node=" + nodeId
            + " online=" + agent.plugin().getServer().getOnlinePlayers().size()
            + " routeWaitSeconds=" + routeWaitSeconds
            + (renderedParts.isEmpty() ? "" : " | " + String.join(" | ", renderedParts));
    }

    private boolean handleNode(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) {
                coreApiClient.adminNodes().nodeInfo(nodeId)
                    .thenAccept(summary -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> AdminNodeMenu.open(player, nodeId, summary, messagesFor(player))))
                    .exceptionally(error -> {
                        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> AdminNodeMenu.open(player, nodeId, messagesFor(player)));
                        return null;
                    });
            } else {
                sender.sendMessage(adminText("admin-command-node-menu-player-only", "플레이어만 노드 관리 메뉴를 열 수 있습니다."));
            }
            return true;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
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

    private MessageRenderer messagesFor(Player player) {
        return messages == null || player == null ? messages : messages.forLocale(player.getLocale());
    }

    private boolean handleIsland(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendIslandCommandUsage(sender);
            return true;
        }
        if (args[1].equalsIgnoreCase("info")) {
            UUID lookupId = uuidOrNull(args[2]);
            if (lookupId != null) {
                run(sender, "Island info", coreApiClient.adminIslands().info(lookupId).thenApply(this::islandInfoMessage));
            } else {
                run(sender, "Island info", coreApiClient.adminIslands().infoByName(args[2]).thenApply(this::islandInfoMessage));
            }
            return true;
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
                sender.sendMessage(adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + args[2]);
                return null;
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("where")) {
            run(sender, "Island where", coreApiClient.adminIslands().runtime(islandId).thenApply(this::runtimeInfoMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("visitor-stats") || args[1].equalsIgnoreCase("visitors")) {
            run(sender, "Island visitor stats", coreApiClient.visitorStats().stats(islandId, 10).thenApply(this::visitorStatsMessage));
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
        if (args[1].equalsIgnoreCase("restore") || args[1].equalsIgnoreCase("rollback")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-snapshot-required", "스냅샷 번호를 입력해주세요."));
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
        if (args[1].equalsIgnoreCase("repair")) {
            run(sender, "Island repair", coreApiClient.lifecycle().repairIsland(islandId, args.length > 3 ? joined(args, 3) : "admin").thenApply(action -> islandLifecycleActionMessage("Island repair", islandId, action)));
            return true;
        }
        if (args[1].equalsIgnoreCase("delete")) {
            run(sender, "Island delete", coreApiClient.lifecycle().adminDeleteIsland(islandId).thenApply(action -> islandLifecycleActionMessage("Island delete", islandId, action)));
            return true;
        }
        sendIslandCommandUsage(sender);
        return true;
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendCommandUsage(sender, List.of(
                "/ciadmin player info <playerUuid|playerName>",
                "/ciadmin player setisland <playerUuid|playerName> <islandUuid>",
                "/ciadmin player clearisland <playerUuid|playerName>"
            ));
            return true;
        }
        resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
            if (playerUuid == null) {
                return;
            }
            if (args[1].equalsIgnoreCase("info")) {
                run(sender, "Player info", coreApiClient.playerProfiles().profile(playerUuid).thenApply(this::playerInfoMessage));
                return;
            }
            if (args[1].equalsIgnoreCase("setisland")) {
                if (args.length < 4) {
                    sender.sendMessage(adminText("admin-command-island-uuid-required", "섬 UUID를 입력해주세요."));
                    return;
                }
                UUID islandId = uuid(sender, args[3]);
                if (islandId != null) {
                    run(sender, "Player setisland", coreApiClient.playerProfileCommands().setPrimaryIsland(playerUuid, islandId).thenApply(profile -> playerActionMessage("Player setisland", profile)));
                }
                return;
            }
            if (args[1].equalsIgnoreCase("clearisland")) {
                run(sender, "Player clearisland", coreApiClient.playerProfileCommands().clearPrimaryIsland(playerUuid).thenApply(profile -> playerActionMessage("Player clearisland", profile)));
                return;
            }
            sendCommandUsage(sender, List.of(
                "/ciadmin player info <playerUuid|playerName>",
                "/ciadmin player setisland <playerUuid|playerName> <islandUuid>",
                "/ciadmin player clearisland <playerUuid|playerName>"
            ));
        }).exceptionally(error -> {
            sender.sendMessage(adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[2]);
            return null;
        });
        return true;
    }

    private boolean handleJobs(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
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

    private boolean handleRoute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendRouteCommandUsage(sender);
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
                sender.sendMessage(adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[2]);
                return null;
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("ticket")) {
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
                    sender.sendMessage(adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[2]);
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
                sender.sendMessage(adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + args[2]);
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
        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 6) {
                sender.sendMessage(adminText("admin-command-block-values-set-usage", "사용법: /ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>"));
                return true;
            }
            UUID actorUuid = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
            run(sender, "Block value set", coreApiClient.blockValueCommands().set(actorUuid, args[2], args[3], number(args[4], 0L), number(args[5], 0L)).thenApply(result -> blockValueActionMessage("Block value set", args[2], result)));
            return true;
        }
        sendCommandUsage(sender, List.of(
            "/ciadmin block-values list",
            "/ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>"
        ));
        return true;
    }

    private boolean handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Template list", coreApiClient.templates().list().thenApply(this::templateListMessage));
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
        if (args[1].equalsIgnoreCase("enable")) {
            run(sender, "Template enable", coreApiClient.templateCommands().enable(args[2]).thenApply(template -> templateActionMessage("Template enable", args[2], template)));
            return true;
        }
        if (args[1].equalsIgnoreCase("disable")) {
            run(sender, "Template disable", coreApiClient.templateCommands().disable(args[2]).thenApply(template -> templateActionMessage("Template disable", args[2], template)));
            return true;
        }
        sendCommandUsage(sender, List.of(
            "/ciadmin templates list",
            "/ciadmin templates upsert <id> <name> [enabled|disabled] [minNodeVersion]",
            "/ciadmin templates enable <id>",
            "/ciadmin templates disable <id>"
        ));
        return true;
    }

    private boolean superiorSkyblock2MigrationEnabled() {
        return superiorSkyblock2MigrationEnabled;
    }

    private void sendIslandCommandUsage(CommandSender sender) {
        sendCommandUsage(sender, List.of(
            "/ciadmin island info <islandUuid|islandName>",
            "/ciadmin island where <islandUuid|islandName>",
            "/ciadmin island visitor-stats <islandUuid|islandName>",
            "/ciadmin island tp <islandUuid|islandName>",
            "/ciadmin island activate <islandUuid|islandName>",
            "/ciadmin island deactivate <islandUuid|islandName>",
            "/ciadmin island migrate <islandUuid|islandName> <node>",
            "/ciadmin island save <islandUuid|islandName> [reason]",
            "/ciadmin island snapshot <islandUuid|islandName> [reason]",
            "/ciadmin island snapshots <islandUuid|islandName> [limit]",
            "/ciadmin island restore <islandUuid|islandName> <snapshotNo>",
            "/ciadmin island rollback <islandUuid|islandName> <snapshotNo>",
            "/ciadmin island quarantine <islandUuid|islandName> [reason]",
            "/ciadmin island repair <islandUuid|islandName> [reason]",
            "/ciadmin island delete <islandUuid|islandName>"
        ));
    }

    private void sendRouteCommandUsage(CommandSender sender) {
        sendCommandUsage(sender, List.of(
            "/ciadmin route debug [all|playerUuid|playerName]",
            "/ciadmin route ticket <ticketUuid|playerUuid|playerName>",
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
        if (!superiorSkyblock2MigrationEnabled()) {
            return HELP_COMMANDS;
        }
        List<String> commands = new ArrayList<>(HELP_COMMANDS);
        commands.addAll(MIGRATION_HELP_COMMANDS);
        return commands;
    }

    private void routeAdminTeleport(Player player, UUID islandId) {
        coreApiClient.adminIslandTeleport(player.getUniqueId(), islandId)
            .thenAccept(ticket -> routeTicket(player, ticket, adminText("admin-command-route-failed", "관리자 섬 이동에 실패했습니다."), 0))
            .exceptionally(error -> {
                message(player, routeFailureMessage(error, adminText("admin-command-route-failed", "관리자 섬 이동에 실패했습니다.")));
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

    private void routeTicket(Player player, RouteTicket ticket, String failureMessage, int attempt) {
        if (ticket.state().name().equals("READY")) {
            publishAndConnect(player, ticket, failureMessage);
            return;
        }
        if (attempt >= routeWaitSeconds) {
            message(player, failureMessage);
            return;
        }
        CompletableFuture.runAsync(() -> coreApiClient.routingCommands().routeTicketStatus(ticket).thenAccept(status -> {
            if (status.isPresent()) {
                routeTicket(player, status.get(), failureMessage, attempt + 1);
            } else {
                message(player, failureMessage);
            }
        }).exceptionally(error -> {
            message(player, routeFailureMessage(error, failureMessage));
            return null;
        }), CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }

    private void publishAndConnect(Player player, RouteTicket ticket, String failureMessage) {
        coreApiClient.routingCommands().publishRouteSession(ticket)
            .thenRun(() -> connectWithTicket(player, ticket, ticket.payload().getOrDefault("targetServerName", ticket.targetNode())))
            .exceptionally(error -> {
                clearFailedRoute(ticket, "SESSION_PUBLISH_FAILED");
                message(player, routeFailureMessage(error, failureMessage));
                return null;
            });
    }

    private void connectWithTicket(Player player, RouteTicket ticket, String targetServerName) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(agent.plugin(), () -> {
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
        });
    }

    private void clearFailedRoute(RouteTicket ticket) {
        clearFailedRoute(ticket, "PLUGIN_MESSAGE_FAILED");
    }

    private void clearFailedRoute(RouteTicket ticket, String reason) {
        coreApiClient.routingCommands().clearRoute(ticket, reason).exceptionally(error -> null);
    }

    private void run(CommandSender sender, String action, CompletableFuture<? extends CharSequence> future) {
        future.thenAccept(body -> {
                String text = body == null ? "" : body.toString();
                message(sender, action + adminText("admin-command-action-complete", " 완료") + (text.isBlank() ? "" : ": " + text));
            })
            .exceptionally(error -> {
                message(sender, action + adminText("admin-command-action-failed", " 실패"));
                return null;
        });
    }

    private record DiagnosticSection(String content) {
        private DiagnosticSection {
            content = content == null ? "" : content;
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

    private String visitorStatsMessage(IslandVisitorStatsView stats) {
        List<String> recent = stats.recentVisitors().stream()
            .limit(5)
            .map(visitor -> shortId(visitor.visitorUuid()) + (visitor.lastVisitedAt().isBlank() ? "" : "@" + visitor.lastVisitedAt()))
            .toList();
        return adminText("admin-command-visitor-stats-prefix", "Visitor stats: island=") + shortId(stats.islandId())
            + adminText("admin-command-visitor-stats-total-prefix", " total=") + stats.totalVisits()
            + adminText("admin-command-visitor-stats-unique-prefix", " unique=") + stats.uniqueVisitors()
            + (recent.isEmpty() ? "" : adminText("admin-command-visitor-stats-recent-prefix", " recent=") + String.join(",", recent));
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
            + (islandId.isBlank() ? adminText("admin-command-player-info-island-none", " island=none") : adminText("admin-command-player-info-island-prefix", " island=") + shortId(islandId));
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

    private String blockValueActionMessage(String label, String targetId, BlockValueActionView result) {
        if (!result.accepted()) {
            return label + ": " + adminText("admin-command-action-result-rejected", "rejected")
                + adminText("admin-command-action-result-target-prefix", " target=") + compactTarget(targetId)
                + (result.code().isBlank() ? "" : adminText("admin-command-action-result-code-prefix", " code=") + result.code());
        }
        String resolvedTarget = result.materialKey().isBlank() ? targetId : result.materialKey();
        return label + adminText("admin-command-action-result-accepted-target-prefix", ": accepted target=") + shortId(resolvedTarget);
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
                    + (minNodeVersion.isBlank() ? "" : adminText("admin-command-template-min-prefix", " min=") + minNodeVersion));
            }
        }
        return adminText("admin-command-templates-total-prefix", "Templates: total=") + templates.size() + adminText("admin-command-templates-enabled-prefix", " enabled=") + enabled + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String templateActionMessage(String label, String targetId, TemplateView template) {
        String resolvedTarget = template.id().isBlank() ? targetId : template.id();
        return label + adminText("admin-command-action-result-accepted-target-prefix", ": accepted target=") + shortId(resolvedTarget);
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
            + (summary.names().isEmpty() ? "" : " / " + String.join(", ", summary.names()));
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
            + (node.mspt().isBlank() ? "" : adminText("admin-command-node-mspt-prefix", " mspt=") + node.mspt());
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
        return !permission.isBlank() && sender.hasPermission(permission);
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
        if (root.equals("migrate-superiorskyblock2") && !superiorSkyblock2MigrationEnabled()) {
            return "";
        }
        return switch (root) {
            case "status", "doctor", "config", "cache", "addons", "integrations", "node", "island", "player", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "diagnostics", "block-values", "upgrade-rules", "templates", "migrate-superiorskyblock2", "reload" -> "cloudislands.admin." + root;
            default -> "";
        };
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
                sender.sendMessage(adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + value);
            }
            return islandId;
        });
    }

    private CompletableFuture<UUID> resolvePlayerUuid(CommandSender sender, String value) {
        Player online = agent.plugin().getServer().getPlayerExact(value);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        try {
            return CompletableFuture.completedFuture(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return coreApiClient.playerProfiles().findByName(value).thenApply(profile -> {
                UUID playerUuid = uuidOrNull(profile.playerUuid());
                if (playerUuid == null) {
                    sender.sendMessage(adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + value);
                }
                return playerUuid;
            });
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
