package kr.lunaf.cloudislands.paper.admin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AdminCommandController implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = List.of("help", "commands", "command", "command-list", "명령어", "명령어목록", "status", "config", "cache", "addons", "node", "island", "player", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "block-values", "upgrade-rules", "template", "templates", "migrate-superiorskyblock2", "reload");
    private static final List<String> CACHE_COMMANDS = List.of("clear");
    private static final List<String> ADDON_COMMANDS = List.of("list", "info", "feature", "enable", "disable", "reload", "state", "state-summary", "endpoints");
    private static final List<String> ADDON_FEATURES = List.of("commands", "machines", "storage", "factories", "generators", "upgrades", "missions", "menus", "gui", "lifecycle", "resource-nodes", "market", "contracts", "research", "maintenance", "placeholders", "migration", "addon-state", "route-events");
    private static final List<String> NODE_COMMANDS = List.of("menu", "list", "info", "islands", "drain", "undrain", "sweep", "kickall", "shutdown-safe");
    private static final List<String> ISLAND_COMMANDS = List.of("info", "where", "tp", "activate", "deactivate", "migrate", "save", "snapshot", "snapshots", "restore", "rollback", "quarantine", "repair", "delete");
    private static final List<String> PLAYER_COMMANDS = List.of("info", "setisland", "clearisland");
    private static final List<String> JOB_COMMANDS = List.of("list", "retry", "cancel", "recover");
    private static final List<String> ROUTE_COMMANDS = List.of("debug", "ticket", "clear");
    private static final List<String> RANKING_COMMANDS = List.of("level", "worth");
    private static final List<String> BLOCK_VALUE_COMMANDS = List.of("list", "set");
    private static final List<String> BLOCK_VALUE_MATERIALS = List.of("minecraft:stone", "minecraft:diamond_block", "minecraft:emerald_block", "minecraft:spawner");
    private static final List<String> TEMPLATE_COMMANDS = List.of("list", "upsert", "enable", "disable");
    private static final List<String> MIGRATION_COMMANDS = List.of("scan", "status", "dryrun", "dry-run", "extract", "extract-worlds", "world-extract", "import", "verify", "verify-no-legacy-provider", "rollback");
    private static final List<String> FORBIDDEN_LEGACY_SKYBLOCK_PROVIDERS = List.of("SuperiorSkyblock2", "BentoBox", "ASkyBlock", "uSkyBlock", "IridiumSkyblock");
    private static final List<String> NODE_DANGER_REASONS = List.of("maintenance", "restart", "drain");
    private static final List<String> HELP_COMMANDS = List.of(
        "ciadmin status",
        "ciadmin config",
        "ciadmin help [page]",
        "ciadmin command list [page]",
        "ciadmin cache clear",
        "ciadmin addons list",
        "ciadmin addons info <addonId>",
        "ciadmin addons feature <addonId> <feature>",
        "ciadmin addons feature <addonId> <feature> <true|false>",
        "ciadmin addons enable <addonId>",
        "ciadmin addons disable <addonId>",
        "ciadmin addons reload [addonId]",
        "ciadmin addons state",
        "ciadmin addons state-summary",
        "ciadmin addons endpoints",
        "ciadmin node menu",
        "ciadmin node list",
        "ciadmin node info <node>",
        "ciadmin node islands <node> [limit]",
        "ciadmin node drain <node>",
        "ciadmin node undrain <node>",
        "ciadmin node sweep [node]",
        "ciadmin node kickall <node> [reason]",
        "ciadmin node shutdown-safe <node> [reason]",
        "ciadmin island info <island>",
        "ciadmin island where <island>",
        "ciadmin island tp <island>",
        "ciadmin island activate <island>",
        "ciadmin island deactivate <island>",
        "ciadmin island migrate <island> <node>",
        "ciadmin island save <island>",
        "ciadmin island snapshot <island> [reason]",
        "ciadmin island snapshots <island>",
        "ciadmin island restore <island> <snapshot>",
        "ciadmin island rollback <island> <snapshot>",
        "ciadmin island quarantine <island> [reason]",
        "ciadmin island repair <island> [reason]",
        "ciadmin island delete <island>",
        "ciadmin player info <player>",
        "ciadmin player setisland <player> <islandUuid>",
        "ciadmin player clearisland <player>",
        "ciadmin jobs list",
        "ciadmin jobs retry <jobId>",
        "ciadmin jobs cancel <jobId>",
        "ciadmin jobs recover [nodeId] [minIdleMillis] [maxJobs]",
        "ciadmin route debug [all|player]",
        "ciadmin route ticket <ticket|player>",
        "ciadmin route clear <player> [ticket]",
        "ciadmin rankings level [limit]",
        "ciadmin rankings worth [limit]",
        "ciadmin events",
        "ciadmin audit",
        "ciadmin metrics",
        "ciadmin storage",
        "ciadmin block-values list",
        "ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>",
        "ciadmin upgrade-rules",
        "ciadmin template list",
        "ciadmin template upsert <id> <name> [enabled|disabled] [minNodeVersion]",
        "ciadmin template enable <id>",
        "ciadmin template disable <id>",
        "ciadmin templates list",
        "ciadmin templates upsert <id> <name> [enabled|disabled] [minNodeVersion]",
        "ciadmin templates enable <id>",
        "ciadmin templates disable <id>",
        "ciadmin reload"
    );
    private static final List<String> MIGRATION_HELP_COMMANDS = List.of(
        "ciadmin migrate-superiorskyblock2 scan [path]",
        "ciadmin migrate-superiorskyblock2 status",
        "ciadmin migrate-superiorskyblock2 dryrun [path]",
        "ciadmin migrate-superiorskyblock2 dry-run [path]",
        "ciadmin migrate-superiorskyblock2 extract [outputPath]",
        "ciadmin migrate-superiorskyblock2 import <approvalToken>",
        "ciadmin migrate-superiorskyblock2 verify [path]",
        "ciadmin migrate-superiorskyblock2 verify-no-legacy-provider",
        "ciadmin migrate-superiorskyblock2 rollback"
    );
    private final CloudIslandsPaperAgent agent;
    private final CoreApiClient coreApiClient;
    private final String nodeId;
    private final int routeWaitSeconds;
    private final LocalCacheManager localCaches;
    private final MessageRenderer messages;

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
        this.agent = agent;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
        this.localCaches = localCaches;
        this.messages = messages;
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
            run(sender, "CloudIslands local cache cleared. Core cache clear", coreApiClient.clearCache().thenApply(body -> maintenanceMessage("Cache clear", body)));
            return true;
        }
        if (args[0].equalsIgnoreCase("addons")) {
            return handleAddons(sender, args);
        }
        if (args[0].equalsIgnoreCase("reload")) {
            run(sender, "Core reload", coreApiClient.reload().thenApply(body -> maintenanceMessage("Core reload", body)));
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
            run(sender, "Events list", coreApiClient.listEvents().thenApply(this::eventListMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("audit")) {
            run(sender, "Audit logs", coreApiClient.listAuditLogs().thenApply(this::auditListMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("metrics")) {
            run(sender, "Core metrics", coreApiClient.metrics().thenApply(this::metricsMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("config")) {
            run(sender, "Core config", coreApiClient.coreConfig().thenApply(this::coreConfigMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("storage")) {
            run(sender, "Storage status", coreApiClient.storageStatus().thenApply(this::storageStatusMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("block-values")) {
            return handleBlockValues(sender, args);
        }
        if (args[0].equalsIgnoreCase("upgrade-rules")) {
            run(sender, "Upgrade rules", coreApiClient.listUpgradeRules().thenApply(this::upgradeRulesMessage));
            return true;
        }
        if (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) {
            return handleTemplate(sender, args);
        }
        if (args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            return handleSuperiorSkyblock2Migration(sender, args);
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
            return matches(List.of("1", "2", "3", "4", "5"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cache")) {
            return matches(CACHE_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addons")) {
            return matches(ADDON_COMMANDS, args[1]);
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

    private boolean handleAddons(CommandSender sender, String[] args) {
        if (args.length > 1 && (args[1].equalsIgnoreCase("state") || args[1].equalsIgnoreCase("state-summary"))) {
            run(sender, "Addon state summary", coreApiClient.addonStateSummary().thenApply(this::addonStateSummaryMessage));
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("endpoints")) {
            run(sender, "Addon endpoints", coreApiClient.coreConfig().thenApply(this::addonEndpointMessage));
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
            agent.plugin().reloadConfig();
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

    private boolean handleNode(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) {
                coreApiClient.nodeInfo(nodeId)
                    .thenAccept(body -> agent.plugin().getServer().getScheduler().runTask(agent.plugin(), () -> AdminNodeMenu.open(player, nodeId, body, messages)))
                    .exceptionally(error -> {
                        agent.plugin().getServer().getScheduler().runTask(agent.plugin(), () -> AdminNodeMenu.open(player, nodeId, messages));
                        return null;
                    });
            } else {
                sender.sendMessage(adminText("admin-command-node-menu-player-only", "플레이어만 노드 관리 메뉴를 열 수 있습니다."));
            }
            return true;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Node list", coreApiClient.listNodes().thenApply(this::nodeListSummaryMessage));
            return true;
        }
        String targetNode = args.length > 2 ? args[2] : nodeId;
        if (args[1].equalsIgnoreCase("info")) {
            run(sender, "Node info", coreApiClient.nodeInfo(targetNode).thenApply(this::appendLevelScanSummary));
            return true;
        }
        if (args[1].equalsIgnoreCase("islands")) {
            run(sender, "Node islands", coreApiClient.nodeIslands(targetNode, nodeIslandLimit(args)).thenApply(this::nodeIslandListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("drain")) {
            run(sender, "Node drain", coreApiClient.drainNode(targetNode).thenApply(body -> nodeActionSummaryMessage("Node drain", targetNode, body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("undrain")) {
            run(sender, "Node undrain", coreApiClient.undrainNode(targetNode).thenApply(body -> nodeActionSummaryMessage("Node undrain", targetNode, body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("sweep")) {
            run(sender, "Node sweep", coreApiClient.sweepNode(targetNode).thenApply(this::nodeSweepMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("kickall")) {
            run(sender, "Node kickall", coreApiClient.kickAllNode(targetNode, args.length > 3 ? joined(args, 3) : "admin").thenApply(body -> nodeActionSummaryMessage("Node kickall", targetNode, body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("shutdown-safe")) {
            run(sender, "Node shutdown-safe", coreApiClient.shutdownNodeSafely(targetNode, args.length > 3 ? joined(args, 3) : "admin").thenApply(body -> nodeActionSummaryMessage("Node shutdown-safe", targetNode, body)));
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

    private boolean handleIsland(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendIslandCommandUsage(sender);
            return true;
        }
        if (args[1].equalsIgnoreCase("info")) {
            UUID lookupId = uuidOrNull(args[2]);
            if (lookupId != null) {
                run(sender, "Island info", coreApiClient.adminIslandInfo(lookupId).thenApply(this::islandInfoMessage));
            } else {
                run(sender, "Island info", coreApiClient.islandInfoByName(args[2]).thenApply(this::islandInfoMessage));
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
                agent.plugin().getServer().getScheduler().runTask(agent.plugin(), () -> handleIsland(sender, resolvedArgs));
            }).exceptionally(error -> {
                sender.sendMessage(adminText("admin-command-island-not-found", "섬을 찾지 못했습니다: ") + args[2]);
                return null;
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("where")) {
            run(sender, "Island where", coreApiClient.adminIslandWhere(islandId).thenApply(this::runtimeInfoMessage));
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
            run(sender, "Island activate", coreApiClient.activateIsland(islandId).thenApply(body -> actionResultMessage("Island activate", islandId.toString(), body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("deactivate")) {
            run(sender, "Island deactivate", coreApiClient.deactivateIsland(islandId).thenApply(body -> actionResultMessage("Island deactivate", islandId.toString(), body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("migrate")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-target-node-required", "대상 노드를 입력해주세요."));
                return true;
            }
            run(sender, "Island migrate", coreApiClient.migrateIsland(islandId, args[3]).thenApply(body -> actionResultMessage("Island migrate", islandId.toString(), body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("save")) {
            String reason = args.length > 3 ? joined(args, 3) : "ADMIN_SAVE";
            run(sender, "Island save", coreApiClient.requestIslandSaveResult(islandId, reason).thenApply(body -> actionResultMessage("Island save", islandId.toString(), body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("snapshot")) {
            String reason = args.length > 3 ? joined(args, 3) : "ADMIN_MANUAL";
            run(sender, "Island snapshot", coreApiClient.requestIslandSnapshotResult(islandId, reason).thenApply(body -> actionResultMessage("Island snapshot", islandId.toString(), body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("snapshots")) {
            int limit = args.length > 3 ? (int) number(args[3], 20L) : 20;
            run(sender, "Island snapshots", coreApiClient.listIslandSnapshots(islandId, Math.max(1, Math.min(limit, 50))).thenApply(this::snapshotListMessage));
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
                run(sender, "Island rollback", coreApiClient.rollbackIslandSnapshotResult(islandId, snapshotNo).thenApply(body -> actionResultMessage("Island rollback", islandId.toString(), body)));
            } else {
                run(sender, "Island restore", coreApiClient.restoreIslandSnapshotResult(islandId, snapshotNo).thenApply(body -> actionResultMessage("Island restore", islandId.toString(), body)));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("quarantine")) {
            run(sender, "Island quarantine", coreApiClient.quarantineIsland(islandId, args.length > 3 ? joined(args, 3) : "admin").thenApply(body -> actionResultMessage("Island quarantine", islandId.toString(), body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("repair")) {
            run(sender, "Island repair", coreApiClient.repairIsland(islandId, args.length > 3 ? joined(args, 3) : "admin").thenApply(body -> actionResultMessage("Island repair", islandId.toString(), body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("delete")) {
            run(sender, "Island delete", coreApiClient.adminDeleteIsland(islandId).thenApply(body -> actionResultMessage("Island delete", islandId.toString(), body)));
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
                run(sender, "Player info", coreApiClient.playerInfo(playerUuid).thenApply(this::playerInfoMessage));
                return;
            }
            if (args[1].equalsIgnoreCase("setisland")) {
                if (args.length < 4) {
                    sender.sendMessage(adminText("admin-command-island-uuid-required", "섬 UUID를 입력해주세요."));
                    return;
                }
                UUID islandId = uuid(sender, args[3]);
                if (islandId != null) {
                    run(sender, "Player setisland", coreApiClient.setPlayerIsland(playerUuid, islandId).thenApply(body -> actionResultMessage("Player setisland", playerUuid.toString(), body)));
                }
                return;
            }
            if (args[1].equalsIgnoreCase("clearisland")) {
                run(sender, "Player clearisland", coreApiClient.clearPlayerIsland(playerUuid).thenApply(body -> actionResultMessage("Player clearisland", playerUuid.toString(), body)));
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
            run(sender, "Jobs list", coreApiClient.listJobs().thenApply(this::jobListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("recover")) {
            String recoverNodeId = args.length > 2 ? args[2] : nodeId;
            long minIdleMillis = args.length > 3 ? number(args[3], 60000L) : 60000L;
            int maxJobs = args.length > 4 ? (int) number(args[4], 20L) : 20;
            run(sender, "Jobs recover", coreApiClient.recoverJobs(recoverNodeId, minIdleMillis, maxJobs).thenApply(body -> jobActionMessage("recover", body)));
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
            run(sender, "Job retry", coreApiClient.retryJob(jobId).thenApply(body -> jobActionMessage("retry", body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("cancel")) {
            run(sender, "Job cancel", coreApiClient.cancelJob(jobId).thenApply(body -> jobActionMessage("cancel", body)));
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
            run(sender, "Level rankings", coreApiClient.topIslandsByLevel(limit).thenApply(body -> rankingListMessage("Level rankings", body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("worth") || args[1].equalsIgnoreCase("value")) {
            int limit = args.length > 2 ? (int) number(args[2], 10L) : 10;
            run(sender, "Worth rankings", coreApiClient.topIslandsByWorth(limit).thenApply(body -> rankingListMessage("Worth rankings", body)));
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
                run(sender, "Route debug", coreApiClient.debugRoutes(new UUID(0L, 0L)).thenApply(this::routeDebugMessage));
                return true;
            }
            resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
                if (playerUuid == null) {
                    return;
                }
                run(sender, "Route debug", coreApiClient.debugRoutes(playerUuid).thenApply(this::routeDebugMessage));
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
                run(sender, "Route ticket", coreApiClient.routeTicket(ticketId).thenApply(this::routeTicketMessage));
            } else {
                resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
                    if (playerUuid == null) {
                        return;
                    }
                    run(sender, "Route ticket", coreApiClient.routeTicketForPlayer(playerUuid).thenApply(this::routeTicketMessage));
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
                run(sender, "Route clear", coreApiClient.clearRoute(playerUuid, ticketId).thenApply(this::routeClearMessage));
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
            run(sender, "Block values", coreApiClient.listBlockValues().thenApply(this::blockValueListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 6) {
                sender.sendMessage(adminText("admin-command-block-values-set-usage", "사용법: /ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>"));
                return true;
            }
            UUID actorUuid = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
            run(sender, "Block value set", coreApiClient.setBlockValueResult(actorUuid, args[2], args[3], number(args[4], 0L), number(args[5], 0L)).thenApply(body -> actionResultMessage("Block value set", args[2], body)));
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
            run(sender, "Template list", coreApiClient.listTemplates().thenApply(this::templateListMessage));
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
            run(sender, "Template upsert", coreApiClient.upsertTemplate(args[2], args[3], enabled, minNodeVersion).thenApply(body -> actionResultMessage("Template upsert", args[2], body)));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(adminText("admin-command-template-id-required", "템플릿 ID를 입력해주세요."));
            return true;
        }
        if (args[1].equalsIgnoreCase("enable")) {
            run(sender, "Template enable", coreApiClient.enableTemplate(args[2]).thenApply(body -> actionResultMessage("Template enable", args[2], body)));
            return true;
        }
        if (args[1].equalsIgnoreCase("disable")) {
            run(sender, "Template disable", coreApiClient.disableTemplate(args[2]).thenApply(body -> actionResultMessage("Template disable", args[2], body)));
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

    private boolean handleSuperiorSkyblock2Migration(CommandSender sender, String[] args) {
        if (!superiorSkyblock2MigrationEnabled()) {
            sender.sendMessage(adminText("admin-command-migration-disabled", "SuperiorSkyblock2 migration is disabled by config."));
            return true;
        }
        String action = args.length > 1 ? args[1] : "scan";
        if (!MIGRATION_COMMANDS.contains(action.toLowerCase(Locale.ROOT))) {
            sendCommandUsage(sender, List.of(
                "/ciadmin migrate-superiorskyblock2 scan [path]",
                "/ciadmin migrate-superiorskyblock2 status",
                "/ciadmin migrate-superiorskyblock2 dryrun [path]",
                "/ciadmin migrate-superiorskyblock2 extract [path]",
                "/ciadmin migrate-superiorskyblock2 import <approvalToken>",
                "/ciadmin migrate-superiorskyblock2 verify [path]",
                "/ciadmin migrate-superiorskyblock2 verify-no-legacy-provider",
                "/ciadmin migrate-superiorskyblock2 rollback"
            ));
            return true;
        }
        if (action.equalsIgnoreCase("verify-no-legacy-provider")) {
            sender.sendMessage(legacyProviderRuntimeMessage());
            return true;
        }
        if (action.equalsIgnoreCase("import") && args.length < 3) {
            sender.sendMessage(adminText("admin-command-migration-import-usage", "사용법: /ciadmin migrate-superiorskyblock2 import <approvalToken>"));
            return true;
        }
        String path = args.length > 2 ? joined(args, 2) : "plugins/SuperiorSkyblock2";
        run(sender, "SuperiorSkyblock2 migration " + action, coreApiClient.migrateSuperiorSkyblock2(action, path).thenApply(this::migrationMessage));
        return true;
    }

    private boolean superiorSkyblock2MigrationEnabled() {
        boolean enabled = agent.getConfig().getBoolean("migration.superiorskyblock2.enabled", true);
        if (agent.getConfig().contains("migration.superiorskyblock2-enabled")) {
            enabled = enabled && agent.getConfig().getBoolean("migration.superiorskyblock2-enabled", true);
        }
        return enabled;
    }

    private String legacyProviderRuntimeMessage() {
        List<String> loadedProviders = FORBIDDEN_LEGACY_SKYBLOCK_PROVIDERS.stream()
            .map(provider -> {
                org.bukkit.plugin.Plugin plugin = agent.plugin().getServer().getPluginManager().getPlugin(provider);
                return plugin == null ? "" : provider + "(enabled=" + plugin.isEnabled() + ")";
            })
            .filter(value -> !value.isBlank())
            .toList();
        if (loadedProviders.isEmpty()) {
            return adminText("admin-command-migration-no-legacy-provider", "Legacy skyblock providers: none. Migration input only policy is clean for ")
                + String.join(",", FORBIDDEN_LEGACY_SKYBLOCK_PROVIDERS);
        }
        return adminText("admin-command-migration-legacy-provider-detected", "Legacy skyblock providers detected: ")
            + String.join(",", loadedProviders)
            + adminText("admin-command-migration-legacy-provider-policy", ". CloudIslands must not use them as runtime island providers.");
    }

    private void sendIslandCommandUsage(CommandSender sender) {
        sendCommandUsage(sender, List.of(
            "/ciadmin island info <islandUuid|islandName>",
            "/ciadmin island where <islandUuid|islandName>",
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
            .map(AdminCommandController::usageCommandName)
            .toList();
        CommandListPolicy.Page commandPage = CommandListPolicy.page(commandNames, 1, "ciadmin command list");
        String title = adminText("admin-command-subcommand-list-title", "CloudIslands 관리자 명령어 목록");
        sender.sendMessage(title.replace(CommandListPolicy.HEADER_SUFFIX, "").trim() + " " + commandPage.page() + "/" + commandPage.pages() + " commands=" + commandPage.rangeSummary() + CommandListPolicy.HEADER_SUFFIX);
        for (String command : commandPage.entries()) {
            sender.sendMessage(CommandListPolicy.ENTRY_PREFIX + command);
        }
        if (commandPage.previousCommand() != null && !commandPage.previousCommand().isBlank()) {
            sender.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.previousCommand());
        }
        if (commandPage.nextCommand() != null && !commandPage.nextCommand().isBlank()) {
            sender.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.nextCommand());
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

    private String migrationMessage(String body) {
        if (body == null || body.isBlank()) {
            return adminText("admin-command-migration-no-response", "Migration: no response");
        }
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            if (code.equals("MIGRATION_DISABLED")) {
                return adminText("admin-command-migration-disabled", "SuperiorSkyblock2 migration is disabled by config.");
            }
            String message = textValue(body, "message");
            return adminText("admin-command-migration-failed-prefix", "Migration: failed code=") + code
                + (message.isBlank() ? "" : adminText("admin-command-migration-message-prefix", " message=") + message);
        }
        String state = textValue(body, "state");
        String path = textValue(body, "path");
        String manifestPath = textValue(body, "manifestPath");
        String reportPath = textValue(body, "reportPath");
        String approvalToken = textValue(body, "approvalToken");
        String issues = arrayValue(body, "issues");
        long manifests = longValue(body, "manifests");
        if (manifests == 0L && body.contains("\"scanManifests\"")) {
            manifests = longValue(body, "scanManifests");
        }
        long importedIslands = longValue(body, "importedIslands");
        long removedIslands = longValue(body, "removedIslands");
        StringBuilder builder = new StringBuilder(adminText("admin-command-migration-state-prefix", "Migration: state="))
            .append(state.isBlank() ? "UNKNOWN" : state)
            .append(adminText("admin-command-migration-manifests-prefix", " manifests="))
            .append(manifests);
        if (!path.isBlank()) {
            builder.append(adminText("admin-command-migration-path-prefix", " path=")).append(path);
        }
        if (!manifestPath.isBlank()) {
            builder.append(adminText("admin-command-migration-manifest-prefix", " manifest=")).append(manifestPath);
        }
        if (!reportPath.isBlank()) {
            builder.append(adminText("admin-command-migration-report-prefix", " report=")).append(reportPath);
        }
        if (!approvalToken.isBlank()) {
            builder.append(adminText("admin-command-migration-approval-prefix", " approval=")).append(approvalToken);
        }
        if (body.contains("\"sourcePlugin\"")) {
            builder.append(adminText("admin-command-migration-source-prefix", " source=")).append(textValue(body, "sourcePlugin"));
        }
        if (body.contains("\"migrationInputOnly\"")) {
            builder.append(adminText("admin-command-migration-input-only-prefix", " inputOnly=")).append(boolValue(body, "migrationInputOnly"));
        }
        if (body.contains("\"runtimeDependency\"")) {
            builder.append(adminText("admin-command-migration-runtime-dependency-prefix", " runtimeDependency=")).append(boolValue(body, "runtimeDependency"));
        }
        String migrationPipeline = textValue(body, "migrationPipeline");
        if (!migrationPipeline.isBlank()) {
            builder.append(adminText("admin-command-migration-pipeline-prefix", " pipeline=")).append(migrationPipeline);
        }
        String checksumPolicy = textValue(body, "migrationChecksumPolicy");
        if (!checksumPolicy.isBlank()) {
            builder.append(adminText("admin-command-migration-checksum-policy-prefix", " checksumPolicy=")).append(checksumPolicy);
        }
        String activationTestPolicy = textValue(body, "migrationActivationTestPolicy");
        if (!activationTestPolicy.isBlank()) {
            builder.append(adminText("admin-command-migration-activation-policy-prefix", " activationPolicy=")).append(activationTestPolicy);
        }
        String targetRuntime = textValue(body, "targetRuntime");
        if (!targetRuntime.isBlank()) {
            builder.append(adminText("admin-command-migration-target-runtime-prefix", " targetRuntime=")).append(targetRuntime);
        }
        if (body.contains("\"canImport\"")) {
            builder.append(adminText("admin-command-migration-can-import-prefix", " canImport=")).append(boolValue(body, "canImport"));
        }
        if (body.contains("\"planManifests\"")) {
            builder.append(adminText("admin-command-migration-plan-manifests-prefix", " planManifests=")).append(longValue(body, "planManifests"));
        }
        if (body.contains("\"rollbackPlanAvailable\"")) {
            builder.append(adminText("admin-command-migration-rollback-plan-prefix", " rollbackPlan=")).append(boolValue(body, "rollbackPlanAvailable"));
        }
        if (body.contains("\"manifestStatus\"")) {
            builder.append(adminText("admin-command-migration-manifest-status-prefix", " manifestStatus=")).append(textValue(body, "manifestStatus"))
                .append(adminText("admin-command-migration-conflict-status-prefix", " conflictStatus=")).append(textValue(body, "conflictStatus"))
                .append(adminText("admin-command-migration-conflicts-prefix", " conflicts=")).append(longValue(body, "conflictIssues"));
        }
        if (body.contains("\"imported\"")) {
            builder.append(adminText("admin-command-migration-imported-prefix", " imported=")).append(boolValue(body, "imported"))
                .append(adminText("admin-command-migration-islands-prefix", " islands="))
                .append(importedIslands);
        }
        if (body.contains("\"passed\"")) {
            builder.append(adminText("admin-command-migration-passed-prefix", " passed=")).append(boolValue(body, "passed"))
                .append(adminText("admin-command-migration-expected-prefix", " expected="))
                .append(longValue(body, "expected"));
        }
        if (body.contains("\"activationTested\"")) {
            builder.append(adminText("admin-command-migration-activation-tested-prefix", " activationTested=")).append(longValue(body, "activationTested"))
                .append(adminText("admin-command-migration-activation-passed-prefix", " activationPassed="))
                .append(longValue(body, "activationTestPassed"));
        }
        if (body.contains("\"rolledBack\"")) {
            builder.append(adminText("admin-command-migration-rolled-back-prefix", " rolledBack=")).append(boolValue(body, "rolledBack"))
                .append(adminText("admin-command-migration-removed-prefix", " removed="))
                .append(removedIslands);
        }
        if (body.contains("\"extractedBundles\"")) {
            builder.append(adminText("admin-command-migration-extracted-prefix", " extracted="))
                .append(longValue(body, "extractedBundles"))
                .append(adminText("admin-command-migration-files-prefix", " files="))
                .append(longValue(body, "extractedFiles"))
                .append(adminText("admin-command-migration-bytes-prefix", " bytes="))
                .append(longValue(body, "extractedBytes"));
        }
        if (body.contains("\"members\"")) {
            builder.append(adminText("admin-command-migration-members-prefix", " members=")).append(longValue(body, "members"))
                .append(adminText("admin-command-migration-member-roles-prefix", " roles=")).append(longValue(body, "memberRoles"))
                .append(adminText("admin-command-migration-bans-prefix", " bans=")).append(longValue(body, "bannedVisitors"))
                .append(adminText("admin-command-migration-homes-prefix", " homes=")).append(longValue(body, "homes"))
                .append(adminText("admin-command-migration-warps-prefix", " warps=")).append(longValue(body, "warps"))
                .append(adminText("admin-command-migration-locations-prefix", " locations=")).append(longValue(body, "islandLocations"))
                .append(adminText("admin-command-migration-source-worlds-prefix", " sourceWorlds=")).append(longValue(body, "sourceWorlds"))
                .append(adminText("admin-command-migration-sizes-prefix", " sizes=")).append(longValue(body, "islandSizes"))
                .append(adminText("admin-command-migration-levels-prefix", " levels=")).append(longValue(body, "levels"))
                .append(adminText("admin-command-migration-worth-prefix", " worth=")).append(longValue(body, "worthValues"))
                .append(adminText("admin-command-migration-biomes-prefix", " biomes=")).append(longValue(body, "biomes"))
                .append(adminText("admin-command-migration-bank-prefix", " bank=")).append(longValue(body, "bankBalances"))
                .append(adminText("admin-command-migration-flags-prefix", " flags=")).append(longValue(body, "flags"))
                .append(adminText("admin-command-migration-perms-prefix", " perms=")).append(longValue(body, "permissions"))
                .append(adminText("admin-command-migration-upgrades-prefix", " upgrades=")).append(longValue(body, "upgrades"))
                .append(adminText("admin-command-migration-limits-prefix", " limits=")).append(longValue(body, "limits"))
                .append(adminText("admin-command-migration-missions-prefix", " missions=")).append(longValue(body, "completedMissions"))
                .append(adminText("admin-command-migration-block-values-prefix", " blockValues=")).append(longValue(body, "blockValues"))
                .append(adminText("admin-command-migration-block-counts-prefix", " blockCounts=")).append(longValue(body, "blockCounts"));
        }
        if (body.contains("\"blockingIssues\"")) {
            builder.append(adminText("admin-command-migration-blocking-prefix", " blocking=")).append(longValue(body, "blockingIssues"))
                .append(adminText("admin-command-migration-warnings-prefix", " warnings=")).append(longValue(body, "warningIssues"));
        }
        builder.append(migrationIssuesSuffix(issues));
        return builder.toString();
    }

    private String migrationIssuesSuffix(String issues) {
        if (issues.isBlank()) {
            return adminText("admin-command-issues-zero", " issues=0");
        }
        int total = 0;
        int blocking = 0;
        List<String> samples = new ArrayList<>();
        int index = 0;
        while (index < issues.length()) {
            int objectStart = issues.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(issues, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = issues.substring(objectStart, objectEnd + 1);
            total++;
            boolean blocked = boolValue(object, "blocking");
            if (blocked) {
                blocking++;
            }
            if (samples.size() < 5) {
                String issueCode = textValue(object, "code");
                samples.add((issueCode.isBlank() ? "UNKNOWN" : issueCode) + (blocked ? "(blocking)" : ""));
            }
            index = objectEnd + 1;
        }
        return adminText("admin-command-issues-total-prefix", " issues=") + total
            + adminText("admin-command-issues-blocking-prefix", " blocking=") + blocking
            + (samples.isEmpty() ? "" : " [" + String.join(", ", samples) + "]");
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
        CompletableFuture.runAsync(() -> coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
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
        coreApiClient.publishRouteSession(ticket)
            .thenRun(() -> connectWithTicket(player, ticket, ticket.payload().getOrDefault("targetServerName", ticket.targetNode())))
            .exceptionally(error -> {
                clearFailedRoute(ticket, "SESSION_PUBLISH_FAILED");
                message(player, routeFailureMessage(error, failureMessage));
                return null;
            });
    }

    private void connectWithTicket(Player player, RouteTicket ticket, String targetServerName) {
        agent.plugin().getServer().getScheduler().runTask(agent.plugin(), () -> {
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
        coreApiClient.clearRoute(ticket.playerUuid(), ticket.ticketId(), reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason).exceptionally(error -> null);
    }

    private void run(CommandSender sender, String action, CompletableFuture<String> future) {
        future.thenAccept(body -> message(sender, action + adminText("admin-command-action-complete", " 완료") + (body == null || body.isBlank() ? "" : ": " + body)))
            .exceptionally(error -> {
                message(sender, action + adminText("admin-command-action-failed", " 실패"));
                return null;
        });
    }

    private String appendLevelScanSummary(String body) {
        List<String> summaries = new ArrayList<>();
        String activation = activationAllocationSummary(body);
        if (!activation.isBlank()) {
            summaries.add(activation);
        }
        String levelScan = levelScanSummary(body);
        if (!levelScan.isBlank()) {
            summaries.add(levelScan);
        }
        if (summaries.isEmpty()) {
            return body;
        }
        return (body == null || body.isBlank() ? "" : body + " | ") + String.join(" | ", summaries);
    }

    private String activationAllocationSummary(String body) {
        if (body == null || body.isBlank() || !body.contains("\"eligibleForNewActivation\"")) {
            return "";
        }
        boolean eligible = boolValue(body, "eligibleForNewActivation");
        String reason = textValue(body, "allocationBlockReason");
        return adminText("admin-command-activation-allocation-label", "활성화 배정=") + (eligible ? adminText("admin-command-activation-eligible", "가능") : adminText("admin-command-activation-blocked-prefix", "차단(") + (reason.isBlank() ? "UNKNOWN" : reason) + ")");
    }

    private String levelScanSummary(String body) {
        String scan = objectValue(body, "levelScan");
        if (scan.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add(adminText("admin-command-level-scan-label", "레벨 스캔=") + (boolValue(scan, "running") ? adminText("admin-command-level-scan-running", "실행 중") : adminText("admin-command-level-scan-idle", "대기")));
        String lastIsland = textValue(scan, "lastIsland");
        if (!lastIsland.isBlank()) {
            parts.add(adminText("admin-command-level-scan-last-island", "마지막 섬=") + lastIsland);
        }
        long startedAt = longValue(scan, "startedAt");
        if (startedAt > 0L) {
            parts.add(adminText("admin-command-level-scan-started", "시작=") + startedAt);
        }
        long finishedAt = longValue(scan, "finishedAt");
        if (finishedAt > 0L) {
            parts.add(adminText("admin-command-level-scan-finished", "완료=") + finishedAt);
        }
        long failedAt = longValue(scan, "failedAt");
        if (failedAt > 0L) {
            parts.add(adminText("admin-command-level-scan-failed", "실패=") + failedAt);
        }
        return String.join(", ", parts);
    }

    private String nodeIslandSummary(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String id = textValue(body, "id");
        String server = textValue(body, "server");
        String state = textValue(body, "state");
        long active = longValue(body, "activeIslands");
        long max = longValue(body, "maxActiveIslands");
        StringBuilder builder = new StringBuilder(adminText("admin-command-node-island-status-title", "노드 섬 현황"));
        if (!id.isBlank()) {
            builder.append(" ").append(id);
        }
        builder.append(adminText("admin-command-node-island-active-prefix", ": 활성 섬 ")).append(active);
        if (max > 0L) {
            builder.append('/').append(max);
        }
        if (!state.isBlank()) {
            builder.append(adminText("admin-command-node-island-state-prefix", ", 상태=")).append(state);
        }
        if (!server.isBlank()) {
            builder.append(adminText("admin-command-node-island-server-prefix", ", 서버=")).append(server);
        }
        return builder.toString();
    }

    private String nodeIslandListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String nodeId = textValue(body, "nodeId");
        long count = longValue(body, "count");
        String islands = arrayValue(body, "islands");
        if (islands.isBlank() || count == 0L) {
            return adminText("admin-command-node-island-status-title", "노드 섬 현황") + (nodeId.isBlank() ? "" : " " + nodeId) + adminText("admin-command-node-island-none-suffix", ": 활성 섬 없음");
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < islands.length()) {
            int objectStart = islands.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(islands, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = islands.substring(objectStart, objectEnd + 1);
            String islandId = textValue(object, "islandId");
            if (!islandId.isBlank()) {
                entries.add(islandId + "(" + nodeIslandRuntimeSuffix(object) + ")");
            }
            index = objectEnd + 1;
        }
        return adminText("admin-command-node-island-status-title", "노드 섬 현황") + (nodeId.isBlank() ? "" : " " + nodeId) + ": " + (entries.isEmpty() ? adminText("admin-command-node-island-none", "활성 섬 없음") : String.join(", ", entries));
    }

    private String storageStatusMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        if (nodes.isBlank()) {
            return adminText("admin-command-storage-no-node", "Storage status: registered node 없음");
        }
        List<String> entries = new ArrayList<>();
        int unavailable = 0;
        int index = 0;
        while (index < nodes.length()) {
            int objectStart = nodes.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(nodes, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = nodes.substring(objectStart, objectEnd + 1);
            String nodeId = textValue(object, "nodeId");
            boolean available = boolValue(object, "storageAvailable");
            if (!nodeId.isBlank()) {
                entries.add(nodeId + "=" + (available ? "OK" : "DOWN") + storageMetricSuffix(object));
                if (!available) {
                    unavailable++;
                }
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty()
            ? adminText("admin-command-storage-no-node", "Storage status: registered node 없음")
            : adminText("admin-command-storage-status-prefix", "Storage status: ") + String.join(", ", entries) + adminText("admin-command-storage-unavailable-prefix", " / unavailable=") + unavailable;
    }

    private String storageMetricSuffix(String nodeObject) {
        String storage = objectValue(nodeObject, "storage");
        if (storage.isBlank()) {
            return "";
        }
        long failures = longValue(storage, "healthCheckFailures")
            + longValue(storage, "uploadFailures")
            + longValue(storage, "downloadFailures")
            + longValue(storage, "operationFailures");
        boolean primaryDegraded = boolValue(storage, "primaryDegraded");
        return adminText("admin-command-storage-metric-failures-prefix", "(failures=") + failures
            + ", primaryDegraded=" + primaryDegraded
            + adminText("admin-command-storage-metric-up-prefix", ", up=") + seconds(doubleValue(storage, "uploadSeconds")) + "s"
            + adminText("admin-command-storage-metric-down-prefix", ", down=") + seconds(doubleValue(storage, "downloadSeconds")) + "s"
            + adminText("admin-command-storage-bundle-policy-prefix", ", bundle=") + "portable"
            + adminText("admin-command-storage-manifest-policy-prefix", ", manifest=") + "manifest.json+checksums.sha256"
            + adminText("admin-command-storage-restore-policy-prefix", ", restore=") + "verify-manifest-checksum)";
    }

    private String nodeSweepMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        long recoveryRequired = longValue(body, "recoveryRequired");
        List<String> swept = new ArrayList<>();
        int index = 0;
        while (index < nodes.length()) {
            int valueStart = nodes.indexOf('"', index);
            if (valueStart < 0) {
                break;
            }
            int valueEnd = nodes.indexOf('"', valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            swept.add(nodes.substring(valueStart + 1, valueEnd));
            index = valueEnd + 1;
        }
        return adminText("admin-command-node-sweep-nodes-prefix", "Node sweep: nodes=") + (swept.isEmpty() ? adminText("admin-command-none", "none") : String.join(",", swept)) + adminText("admin-command-node-sweep-recovery-prefix", " recoveryRequired=") + recoveryRequired;
    }

    private String jobListMessage(String body) {
        String jobs = arrayValue(body, "jobs");
        if (jobs.isBlank()) {
            return adminText("admin-command-jobs-empty", "Jobs: empty");
        }
        int pending = 0;
        int claimed = 0;
        int failed = 0;
        int done = 0;
        int other = 0;
        int total = 0;
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < jobs.length()) {
            int objectStart = jobs.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(jobs, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = jobs.substring(objectStart, objectEnd + 1);
            String state = textValue(object, "state");
            total++;
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
                entries.add(jobSummary(object));
            }
            index = objectEnd + 1;
        }
        return adminText("admin-command-jobs-total-prefix", "Jobs: total=") + total
            + adminText("admin-command-jobs-pending-prefix", " pending=") + pending
            + adminText("admin-command-jobs-claimed-prefix", " claimed=") + claimed
            + adminText("admin-command-jobs-failed-prefix", " failed=") + failed
            + adminText("admin-command-jobs-done-prefix", " done=") + done
            + adminText("admin-command-jobs-other-prefix", " other=") + other
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String jobSummary(String object) {
        String id = textValue(object, "id");
        String type = textValue(object, "type");
        String state = textValue(object, "state");
        String targetNode = textValue(object, "targetNode");
        long attempts = longValue(object, "attempts");
        String error = textValue(object, "error");
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

    private String jobActionMessage(String action, String body) {
        if (body == null || body.isBlank()) {
            return adminText("admin-command-job-prefix", "Job ") + action + adminText("admin-command-job-no-response", ": no response");
        }
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return adminText("admin-command-job-prefix", "Job ") + action + adminText("admin-command-job-failed-code-prefix", ": failed code=") + code;
        }
        if (body.contains("\"recovered\"")) {
            String recoveredText = textValue(body, "recovered");
            long recoveredNumber = longValue(body, "recovered");
            return adminText("admin-command-job-recover-prefix", "Job recover: recovered=") + (recoveredText.isBlank() ? Long.toString(recoveredNumber) : recoveredText);
        }
        return adminText("admin-command-job-prefix", "Job ") + action + ": " + (boolValue(body, "ok") ? adminText("admin-command-job-accepted", "accepted") : adminText("admin-command-job-not-applied", "not applied"));
    }

    private String actionResultMessage(String label, String targetId, String body) {
        if (body == null || body.isBlank()) {
            return label + adminText("admin-command-action-result-accepted-target-prefix", ": accepted target=") + shortId(targetId);
        }
        String code = textValue(body, "code");
        boolean accepted = body.contains("\"accepted\"") ? boolValue(body, "accepted") : !body.contains("\"accepted\":false");
        StringBuilder builder = new StringBuilder(label)
            .append(": ")
            .append(accepted ? adminText("admin-command-action-result-accepted", "accepted") : adminText("admin-command-action-result-rejected", "rejected"))
            .append(adminText("admin-command-action-result-target-prefix", " target="))
            .append(compactTarget(targetId));
        if (!code.isBlank()) {
            builder.append(adminText("admin-command-action-result-code-prefix", " code=")).append(code);
            String detail = adminCodeDetail(code);
            if (!detail.isBlank()) {
                builder.append(adminText("admin-command-action-result-detail-prefix", " detail=")).append(detail);
            }
        }
        String islandId = textValue(body, "islandId");
        if (!islandId.isBlank() && !islandId.equals(targetId)) {
            builder.append(adminText("admin-command-action-result-island-prefix", " island=")).append(shortId(islandId));
        }
        String materialKey = textValue(body, "materialKey");
        if (!materialKey.isBlank()) {
            builder.append(adminText("admin-command-action-result-material-prefix", " material=")).append(materialKey);
        }
        String worth = textValue(body, "worth");
        if (!worth.isBlank()) {
            builder.append(adminText("admin-command-action-result-worth-prefix", " worth=")).append(worth);
        }
        if (body.contains("\"snapshotNo\"")) {
            builder.append(adminText("admin-command-action-result-snapshot-prefix", " snapshot=")).append(longValue(body, "snapshotNo"));
        }
        String storagePath = textValue(body, "storagePath");
        if (!storagePath.isBlank()) {
            builder.append(adminText("admin-command-action-result-storage-path-prefix", " storagePath=")).append(storagePath);
        }
        if (body.contains("\"restoreManifestRequired\"")) {
            builder.append(adminText("admin-command-action-result-restore-manifest-prefix", " restoreManifest=")).append(boolValue(body, "restoreManifestRequired"));
        }
        String restoreChecksumPolicy = textValue(body, "restoreChecksumPolicy");
        if (!restoreChecksumPolicy.isBlank()) {
            builder.append(adminText("admin-command-action-result-restore-checksum-prefix", " restoreChecksum=")).append(restoreChecksumPolicy);
        }
        if (body.contains("\"restorePortableRequired\"")) {
            builder.append(adminText("admin-command-action-result-restore-portable-prefix", " restorePortable=")).append(boolValue(body, "restorePortableRequired"));
        }
        String restoreSupportedFormats = textValue(body, "restoreSupportedFormats");
        if (!restoreSupportedFormats.isBlank()) {
            builder.append(adminText("admin-command-action-result-restore-formats-prefix", " restoreFormats=")).append(restoreSupportedFormats);
        }
        return builder.toString();
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

    private String islandInfoMessage(String body) {
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return adminText("admin-command-island-info-failed-prefix", "Island: failed code=") + code;
        }
        String islandId = textValue(body, "islandId");
        String ownerUuid = textValue(body, "ownerUuid");
        String name = textValue(body, "name");
        String state = textValue(body, "state");
        return adminText("admin-command-island-info-id-prefix", "Island: id=") + shortId(islandId)
            + adminText("admin-command-island-info-owner-prefix", " owner=") + shortId(ownerUuid)
            + (name.isBlank() ? "" : adminText("admin-command-island-info-name-prefix", " name=") + name)
            + adminText("admin-command-island-info-state-prefix", " state=") + (state.isBlank() ? "UNKNOWN" : state)
            + adminText("admin-command-island-info-size-prefix", " size=") + longValue(body, "size")
            + adminText("admin-command-island-info-level-prefix", " level=") + longValue(body, "level")
            + adminText("admin-command-island-info-worth-prefix", " worth=") + textValue(body, "worth")
            + adminText("admin-command-island-info-public-prefix", " public=") + boolValue(body, "publicAccess");
    }

    private String runtimeInfoMessage(String body) {
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return adminText("admin-command-runtime-failed-prefix", "Island runtime: failed code=") + code;
        }
        String islandId = textValue(body, "islandId");
        String state = textValue(body, "state");
        String activeNode = textValue(body, "activeNode");
        String activeWorld = textValue(body, "activeWorld");
        return adminText("admin-command-runtime-island-prefix", "Island runtime: island=") + shortId(islandId)
            + adminText("admin-command-runtime-state-prefix", " state=") + (state.isBlank() ? "UNKNOWN" : state)
            + (activeNode.isBlank() ? "" : adminText("admin-command-runtime-node-prefix", " node=") + activeNode)
            + (activeWorld.isBlank() ? "" : adminText("admin-command-runtime-world-prefix", " world=") + activeWorld)
            + (body.contains("\"cellX\":null") || body.contains("\"cellZ\":null") ? "" : adminText("admin-command-runtime-cell-prefix", " cell=") + longValue(body, "cellX") + "," + longValue(body, "cellZ"))
            + adminText("admin-command-runtime-fence-prefix", " fence=") + longValue(body, "fencingToken");
    }

    private String playerInfoMessage(String body) {
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return adminText("admin-command-player-info-failed-prefix", "Player: failed code=") + code;
        }
        String playerUuid = textValue(body, "playerUuid");
        String lastName = textValue(body, "lastName");
        String islandId = textValue(body, "primaryIslandId");
        return adminText("admin-command-player-info-uuid-prefix", "Player: uuid=") + shortId(playerUuid)
            + (lastName.isBlank() ? "" : adminText("admin-command-player-info-name-prefix", " name=") + lastName)
            + (islandId.isBlank() ? adminText("admin-command-player-info-island-none", " island=none") : adminText("admin-command-player-info-island-prefix", " island=") + shortId(islandId));
    }

    private String rankingListMessage(String label, String body) {
        String rankings = arrayValue(body, "rankings");
        if (rankings.isBlank()) {
            return label + adminText("admin-command-ranking-empty-suffix", ": empty");
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < rankings.length()) {
            int objectStart = rankings.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(rankings, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = rankings.substring(objectStart, objectEnd + 1);
                entries.add("#" + total
                    + " " + shortId(textValue(object, "islandId"))
                    + adminText("admin-command-ranking-level-prefix", " level=") + longValue(object, "level")
                    + adminText("admin-command-ranking-worth-prefix", " worth=") + textValue(object, "worth"));
            }
            index = objectEnd + 1;
        }
        return label + adminText("admin-command-ranking-total-prefix", ": total=") + total + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String blockValueListMessage(String body) {
        String values = arrayValue(body, "values");
        if (values.isBlank()) {
            return adminText("admin-command-block-values-empty", "Block values: empty");
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < values.length()) {
            int objectStart = values.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(values, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = values.substring(objectStart, objectEnd + 1);
                entries.add(textValue(object, "materialKey")
                    + adminText("admin-command-block-values-worth-prefix", " worth=") + textValue(object, "worth")
                    + adminText("admin-command-block-values-level-prefix", " level=") + longValue(object, "levelPoints")
                    + adminText("admin-command-block-values-limit-prefix", " limit=") + longValue(object, "limit"));
            }
            index = objectEnd + 1;
        }
        return adminText("admin-command-block-values-total-prefix", "Block values: total=") + total + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String templateListMessage(String body) {
        String templates = arrayValue(body, "templates");
        if (templates.isBlank()) {
            return adminText("admin-command-templates-empty", "Templates: empty");
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int enabled = 0;
        int index = 0;
        while (index < templates.length()) {
            int objectStart = templates.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(templates, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = templates.substring(objectStart, objectEnd + 1);
            total++;
            if (boolValue(object, "enabled")) {
                enabled++;
            }
            if (entries.size() < 10) {
                String minNodeVersion = textValue(object, "minNodeVersion");
                entries.add(textValue(object, "id")
                    + " " + (boolValue(object, "enabled") ? adminText("admin-command-template-enabled", "enabled") : adminText("admin-command-template-disabled", "disabled"))
                    + (minNodeVersion.isBlank() ? "" : adminText("admin-command-template-min-prefix", " min=") + minNodeVersion));
            }
            index = objectEnd + 1;
        }
        return adminText("admin-command-templates-total-prefix", "Templates: total=") + total + adminText("admin-command-templates-enabled-prefix", " enabled=") + enabled + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String upgradeRulesMessage(String body) {
        String rules = arrayValue(body, "rules");
        if (rules.isBlank()) {
            return adminText("admin-command-upgrade-rules-empty", "Upgrade rules: empty");
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < rules.length()) {
            int objectStart = rules.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(rules, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = rules.substring(objectStart, objectEnd + 1);
                entries.add(textValue(object, "upgradeKey")
                    + adminText("admin-command-upgrade-rules-type-prefix", " type=") + textValue(object, "type")
                    + adminText("admin-command-upgrade-rules-max-prefix", " max=") + longValue(object, "maxLevel")
                    + adminText("admin-command-upgrade-rules-base-prefix", " base=") + textValue(object, "baseCost"));
            }
            index = objectEnd + 1;
        }
        return adminText("admin-command-upgrade-rules-total-prefix", "Upgrade rules: total=") + total + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String maintenanceMessage(String label, String body) {
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return label + adminText("admin-command-maintenance-failed-code-prefix", ": failed code=") + code;
        }
        return label + adminText("admin-command-maintenance-accepted-sessions-prefix", ": accepted sessions=") + longValue(body, "clearedSessions") + adminText("admin-command-maintenance-tickets-prefix", " tickets=") + longValue(body, "clearedTickets");
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

    private String addonStateSummaryMessage(String body) {
        String addons = arrayValue(body, "addons");
        if (addons.isBlank()) {
            return adminText("admin-command-addons-state-empty", "Addon state: empty");
        }
        List<String> entries = new ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < addons.length()) {
            int objectStart = addons.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(addons, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = addons.substring(objectStart, objectEnd + 1);
                entries.add(textValue(object, "addonId")
                    + adminText("admin-command-addons-state-global-prefix", " global=") + longValue(object, "globalKeys")
                    + adminText("admin-command-addons-state-island-prefix", " island=") + longValue(object, "islandKeys")
                    + adminText("admin-command-addons-state-total-keys-prefix", " totalKeys=") + longValue(object, "totalKeys"));
            }
            index = objectEnd + 1;
        }
        return adminText("admin-command-addons-state-total-prefix", "Addon state: total=") + total
            + " owner=" + textValue(body, "stateOwnership")
            + " registeredRequired=" + boolValue(body, "registeredAddonRequired")
            + " orphanPolicy=" + textValue(body, "orphanStatePolicy")
            + " missingPolicy=" + textValue(body, "missingAddonStatePolicy")
            + " tableKeyPrefix=" + textValue(body, "tableKeyPrefix")
            + " maxKeysPerAddon=" + longValue(body, "maxKeysPerAddon")
            + " maxValueLength=" + longValue(body, "maxValueLength")
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

    private String metricsMessage(String body) {
        if (body == null || body.isBlank()) {
            return adminText("admin-command-metrics-empty", "Core metrics: empty");
        }
        int samples = 0;
        List<String> names = new ArrayList<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            samples++;
            if (names.size() < 6) {
                int brace = trimmed.indexOf('{');
                int space = trimmed.indexOf(' ');
                int end = brace > 0 ? brace : space > 0 ? space : trimmed.length();
                String name = trimmed.substring(0, end);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return adminText("admin-command-metrics-samples-prefix", "Core metrics: samples=") + samples + (names.isEmpty() ? "" : " / " + String.join(", ", names));
    }

    private String coreConfigMessage(String body) {
        String code = textValue(body, "code");
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
            + " generatorPolicy=" + textValue(body, "generatorPolicy")
            + " ss2Replacement=" + textValue(body, "superiorSkyblock2ReplacementFeatures")
            + " ss2ReplacementPolicy=" + textValue(body, "superiorSkyblock2ReplacementPolicy")
            + " ss2FeatureGate=" + textValue(body, "superiorSkyblock2ReplacementFeatureGate")
            + adminText("admin-command-core-config-mtls-prefix", " mtls=") + boolValue(body, "requireMtls")
            + adminText("admin-command-core-config-ip-allowlist-prefix", " ipAllowlist=") + boolValue(body, "ipAllowlistEnabled")
            + " securityControls=" + textValue(body, "requiredSecurityControls")
            + " pluginMessagingSecurity=" + textValue(body, "pluginMessagingSecurityPolicy");
    }

    private String addonEndpointMessage(String body) {
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

    private String eventListMessage(String body) {
        String events = arrayValue(body, "events");
        if (events.isBlank()) {
            return adminText("admin-command-events-empty", "Events: empty");
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < events.length() && entries.size() < 10) {
            int objectStart = events.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(events, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = events.substring(objectStart, objectEnd + 1);
            String type = textValue(object, "type");
            String occurredAt = textValue(object, "occurredAt");
            String fields = objectValue(object, "fields");
            String islandId = textValue(fields, "islandId");
            String ticketId = textValue(fields, "ticketId");
            String playerUuid = textValue(fields, "playerUuid");
            String action = textValue(fields, "action");
            String reason = textValue(fields, "reason");
            String requestedNode = textValue(fields, "requestedNode");
            String clearedSession = textValue(fields, "clearedSession");
            String clearedTicket = textValue(fields, "clearedTicket");
            String nodeId = textValue(fields, "nodeId");
            if (nodeId.isBlank()) {
                nodeId = textValue(fields, "targetNode");
            }
            entries.add((type.isBlank() ? "UNKNOWN_EVENT" : type)
                + (islandId.isBlank() ? "" : adminText("admin-command-event-island-prefix", " island=") + islandId)
                + (ticketId.isBlank() ? "" : adminText("admin-command-event-ticket-prefix", " ticket=") + shortId(ticketId))
                + (playerUuid.isBlank() ? "" : adminText("admin-command-event-player-prefix", " player=") + shortId(playerUuid))
                + (action.isBlank() ? "" : adminText("admin-command-event-action-prefix", " action=") + action)
                + (reason.isBlank() ? "" : adminText("admin-command-event-reason-prefix", " reason=") + reason)
                + (requestedNode.isBlank() ? "" : adminText("admin-command-event-requested-node-prefix", " requestedNode=") + requestedNode)
                + (clearedSession.isBlank() ? "" : adminText("admin-command-event-session-prefix", " session=") + clearedSession)
                + (clearedTicket.isBlank() ? "" : adminText("admin-command-event-ticket-cleared-prefix", " ticketCleared=") + clearedTicket)
                + (nodeId.isBlank() ? "" : adminText("admin-command-event-node-prefix", " node=") + nodeId)
                + (occurredAt.isBlank() ? "" : adminText("admin-command-event-at-prefix", " at=") + occurredAt));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? adminText("admin-command-events-empty", "Events: empty") : adminText("admin-command-events-prefix", "Events: ") + String.join(" | ", entries);
    }

    private String auditListMessage(String body) {
        String audit = arrayValue(body, "audit");
        if (audit.isBlank()) {
            return adminText("admin-command-audit-empty", "Audit: empty");
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < audit.length() && entries.size() < 10) {
            int objectStart = audit.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(audit, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = audit.substring(objectStart, objectEnd + 1);
            String action = textValue(object, "action");
            String actorType = textValue(object, "actorType");
            String targetType = textValue(object, "targetType");
            String targetId = textValue(object, "targetId");
            String createdAt = textValue(object, "createdAt");
            entries.add((action.isBlank() ? "UNKNOWN_ACTION" : action)
                + (targetType.isBlank() && targetId.isBlank() ? "" : adminText("admin-command-audit-target-prefix", " target=") + targetType + ":" + targetId)
                + (actorType.isBlank() ? "" : adminText("admin-command-audit-actor-prefix", " actor=") + actorType)
                + (createdAt.isBlank() ? "" : adminText("admin-command-audit-at-prefix", " at=") + createdAt));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? adminText("admin-command-audit-empty", "Audit: empty") : adminText("admin-command-audit-prefix", "Audit: ") + String.join(" | ", entries);
    }

    private String routeDebugMessage(String body) {
        String sessions = arrayValue(body, "sessions");
        String tickets = arrayValue(body, "tickets");
        List<String> sessionEntries = new ArrayList<>();
        List<String> ticketEntries = new ArrayList<>();
        collectSessionSummaries(sessions, sessionEntries, 5);
        collectTicketSummaries(tickets, ticketEntries, 5);
        return adminText("admin-command-routes-sessions-prefix", "Routes: sessions=") + countObjects(sessions)
            + (sessionEntries.isEmpty() ? "" : " [" + String.join(" | ", sessionEntries) + "]")
            + adminText("admin-command-routes-tickets-prefix", " tickets=") + countObjects(tickets)
            + (ticketEntries.isEmpty() ? "" : " [" + String.join(" | ", ticketEntries) + "]");
    }

    private String routeTicketMessage(String body) {
        if (body == null || body.isBlank()) {
            return adminText("admin-command-route-ticket-not-found", "Route ticket: not found");
        }
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return adminText("admin-command-route-ticket-failed-prefix", "Route ticket: failed code=") + code;
        }
        return adminText("admin-command-route-ticket-prefix", "Route ticket: ") + ticketSummary(body);
    }

    private String routeClearMessage(String body) {
        if (body == null || body.isBlank()) {
            return adminText("admin-command-route-clear-no-response", "Route clear: no response");
        }
        String reason = textValue(body, "reason");
        return adminText("admin-command-route-clear-session-prefix", "Route clear: session=") + boolValue(body, "clearedSession") + adminText("admin-command-route-clear-ticket-prefix", " ticket=") + boolValue(body, "clearedTicket") + (reason.isBlank() ? "" : adminText("admin-command-route-clear-reason-prefix", " reason=") + reason);
    }

    private String snapshotListMessage(String body) {
        String snapshots = arrayValue(body, "snapshots");
        if (snapshots.isBlank()) {
            return adminText("admin-command-snapshots-empty", "Snapshots: empty");
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < snapshots.length() && entries.size() < 20) {
            int objectStart = snapshots.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(snapshots, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = snapshots.substring(objectStart, objectEnd + 1);
            long snapshotNo = longValue(object, "snapshotNo");
            if (snapshotNo > 0L) {
                String reason = textValue(object, "reason");
                long sizeBytes = longValue(object, "sizeBytes");
                String createdAt = textValue(object, "createdAt");
                String checksum = textValue(object, "checksum");
                String storagePath = textValue(object, "storagePath");
                entries.add("#" + snapshotNo
                    + (reason.isBlank() ? "" : " " + reason)
                    + adminText("admin-command-snapshot-size-prefix", " size=") + sizeBytes
                    + (checksum.isBlank() ? "" : adminText("admin-command-snapshot-checksum-prefix", " checksum=") + shortChecksum(checksum))
                    + (storagePath.isBlank() ? "" : adminText("admin-command-snapshot-path-prefix", " path=") + storagePath)
                    + (createdAt.isBlank() ? "" : adminText("admin-command-snapshot-at-prefix", " at=") + createdAt));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? adminText("admin-command-snapshots-empty", "Snapshots: empty") : adminText("admin-command-snapshots-prefix", "Snapshots: ") + String.join(" | ", entries);
    }

    private void collectSessionSummaries(String sessions, List<String> entries, int limit) {
        int index = 0;
        while (index < sessions.length() && entries.size() < limit) {
            int objectStart = sessions.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(sessions, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = sessions.substring(objectStart, objectEnd + 1);
            String playerUuid = textValue(object, "playerUuid");
            String ticketId = textValue(object, "ticketId");
            String nodeId = textValue(object, "targetNode");
            String serverName = textValue(object, "targetServerName");
            String expiresAt = textValue(object, "expiresAt");
            entries.add(shortId(playerUuid)
                + adminText("admin-command-route-session-ticket-prefix", " ticket=") + shortId(ticketId)
                + (nodeId.isBlank() ? "" : adminText("admin-command-route-session-node-prefix", " node=") + nodeId)
                + (serverName.isBlank() ? "" : adminText("admin-command-route-session-server-prefix", " server=") + serverName)
                + (expiresAt.isBlank() ? "" : adminText("admin-command-route-session-expires-prefix", " expires=") + expiresAt));
            index = objectEnd + 1;
        }
    }

    private void collectTicketSummaries(String tickets, List<String> entries, int limit) {
        int index = 0;
        while (index < tickets.length() && entries.size() < limit) {
            int objectStart = tickets.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(tickets, objectStart);
            if (objectEnd < 0) {
                break;
            }
            entries.add(ticketSummary(tickets.substring(objectStart, objectEnd + 1)));
            index = objectEnd + 1;
        }
    }

    private String ticketSummary(String object) {
        String ticketId = textValue(object, "ticketId");
        String action = textValue(object, "action");
        String state = textValue(object, "state");
        String islandId = textValue(object, "islandId");
        String nodeId = textValue(object, "targetNode");
        String targetType = textValue(object, "targetType");
        String homeName = textValue(object, "homeName");
        String warpName = textValue(object, "warpName");
        String targetName = !homeName.isBlank() ? homeName : warpName;
        return shortId(ticketId)
            + " " + (action.isBlank() ? "UNKNOWN" : action)
            + " " + (state.isBlank() ? "UNKNOWN" : state)
            + (targetType.isBlank() && targetName.isBlank() ? "" : " target=" + (targetType.isBlank() ? "-" : targetType) + (targetName.isBlank() ? "" : ":" + targetName))
            + (islandId.isBlank() ? "" : adminText("admin-command-route-ticket-island-prefix", " island=") + shortId(islandId))
            + (nodeId.isBlank() ? "" : adminText("admin-command-route-ticket-node-prefix", " node=") + nodeId);
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

    private int countObjects(String array) {
        int count = 0;
        int index = 0;
        while (index < array.length()) {
            int objectStart = array.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(array, objectStart);
            if (objectEnd < 0) {
                break;
            }
            count++;
            index = objectEnd + 1;
        }
        return count;
    }

    private String nodeIslandRuntimeSuffix(String object) {
        List<String> parts = new ArrayList<>();
        String state = textValue(object, "state");
        if (!state.isBlank()) {
            parts.add(state);
        }
        String world = textValue(object, "activeWorld");
        if (!world.isBlank()) {
            parts.add(adminText("admin-command-runtime-world-compact-prefix", "world=") + world);
        }
        if (!object.contains("\"cellX\":null") && !object.contains("\"cellZ\":null")) {
            parts.add(adminText("admin-command-runtime-cell-compact-prefix", "cell=") + longValue(object, "cellX") + "," + longValue(object, "cellZ"));
        }
        return String.join(" ", parts);
    }

    private int nodeIslandLimit(String[] args) {
        return args.length > 3 ? (int) Math.max(1L, Math.min(number(args[3], 50L), 200L)) : 50;
    }

    private String nodeListSummaryMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        if (nodes.isBlank()) {
            return adminText("admin-command-nodes-empty", "Nodes: empty");
        }
        int total = 0;
        int starting = 0;
        int warming = 0;
        int ready = 0;
        int softFull = 0;
        int hardFull = 0;
        int draining = 0;
        int shuttingDown = 0;
        int down = 0;
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < nodes.length()) {
            int objectStart = nodes.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(nodes, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = nodes.substring(objectStart, objectEnd + 1);
            String state = textValue(object, "state");
            total++;
            if (state.equalsIgnoreCase("STARTING")) {
                starting++;
            } else if (state.equalsIgnoreCase("WARMING")) {
                warming++;
            } else if (state.equalsIgnoreCase("READY")) {
                ready++;
            } else if (state.equalsIgnoreCase("SOFT_FULL")) {
                softFull++;
            } else if (state.equalsIgnoreCase("HARD_FULL")) {
                hardFull++;
            } else if (state.equalsIgnoreCase("DRAINING")) {
                draining++;
            } else if (state.equalsIgnoreCase("SHUTTING_DOWN")) {
                shuttingDown++;
            } else if (state.equalsIgnoreCase("DOWN")) {
                down++;
            }
            if (entries.size() < 10) {
                entries.add(nodeSummary(object));
            }
            index = objectEnd + 1;
        }
        return adminText("admin-command-nodes-total-prefix", "Nodes: total=") + total
            + adminText("admin-command-nodes-starting-prefix", " starting=") + starting
            + adminText("admin-command-nodes-warming-prefix", " warming=") + warming
            + adminText("admin-command-nodes-ready-prefix", " ready=") + ready
            + adminText("admin-command-nodes-soft-full-prefix", " softFull=") + softFull
            + adminText("admin-command-nodes-hard-full-prefix", " hardFull=") + hardFull
            + adminText("admin-command-nodes-draining-prefix", " draining=") + draining
            + adminText("admin-command-nodes-shutting-down-prefix", " shuttingDown=") + shuttingDown
            + adminText("admin-command-nodes-down-prefix", " down=") + down
            + poolSummarySuffix(body)
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String poolSummarySuffix(String body) {
        String pools = arrayValue(body, "pools");
        if (pools.isBlank()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < pools.length()) {
            int objectStart = pools.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(pools, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = pools.substring(objectStart, objectEnd + 1);
            String pool = textValue(object, "pool");
            entries.add((pool.isBlank() ? "island" : pool)
                + " nodes=" + longValue(object, "healthyNodeCount") + "/" + longValue(object, "nodeCount")
                + " players=" + longValue(object, "players") + "/" + longValue(object, "softPlayerCap") + "/" + longValue(object, "hardPlayerCap")
                + " reserved=" + longValue(object, "reservedSlots")
                + " islands=" + longValue(object, "activeIslands") + "/" + longValue(object, "maxActiveIslands")
                + " queue=" + longValue(object, "activationQueue") + "/" + longValue(object, "maxActivationQueue"));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "" : " / pools: " + String.join(" | ", entries);
    }

    private String nodeSummary(String object) {
        String id = textValue(object, "id");
        String state = textValue(object, "state");
        long players = longValue(object, "players");
        long softCap = longValue(object, "softPlayerCap");
        long hardCap = longValue(object, "hardPlayerCap");
        long reservedSlots = longValue(object, "reservedSlots");
        long activeIslands = longValue(object, "activeIslands");
        long maxActiveIslands = longValue(object, "maxActiveIslands");
        long activationQueue = longValue(object, "activationQueue");
        long maxActivationQueue = longValue(object, "maxActivationQueue");
        boolean activationEligible = boolValue(object, "eligibleForNewActivation");
        String allocationBlockReason = textValue(object, "allocationBlockReason");
        return (id.isBlank() ? adminText("admin-command-node-default-id", "node") : id)
            + " " + (state.isBlank() ? "UNKNOWN" : state)
            + adminText("admin-command-node-players-prefix", " players=") + players + "/" + softCap + "/" + hardCap + adminText("admin-command-node-reserved-prefix", " reserved=") + reservedSlots
            + adminText("admin-command-node-islands-prefix", " islands=") + activeIslands + "/" + maxActiveIslands
            + adminText("admin-command-node-queue-prefix", " queue=") + activationQueue + "/" + maxActivationQueue
            + adminText("admin-command-node-mspt-prefix", " mspt=") + seconds(doubleValue(object, "mspt"))
            + adminText("admin-command-node-score-prefix", " score=") + seconds(doubleValue(object, "score"))
            + scoreParts(object)
            + adminText("admin-command-node-activation-prefix", " activation=") + (activationEligible ? adminText("admin-command-node-ok", "ok") : adminText("admin-command-node-blocked-prefix", "blocked:") + (allocationBlockReason.isBlank() ? "UNKNOWN" : allocationBlockReason))
            + adminText("admin-command-node-storage-prefix", " storage=") + (boolValue(object, "storageAvailable") ? adminText("admin-command-node-ok", "ok") : adminText("admin-command-node-down", "down"));
    }

    private String scoreParts(String nodeObject) {
        String breakdown = objectValue(nodeObject, "scoreBreakdown");
        if (breakdown.isBlank()) {
            return "";
        }
        return adminText("admin-command-score-parts-prefix", " parts=")
            + "p:" + scoreTerm(breakdown, "player")
            + adminText("admin-command-score-active-prefix", ",a:") + scoreTerm(breakdown, "activeIsland")
            + adminText("admin-command-score-mspt-prefix", ",m:") + scoreTerm(breakdown, "mspt")
            + adminText("admin-command-score-queue-prefix", ",q:") + scoreTerm(breakdown, "activationQueue")
            + adminText("admin-command-score-chunk-prefix", ",chunk:") + scoreTerm(breakdown, "chunkLoad")
            + adminText("admin-command-score-memory-prefix", ",mem:") + scoreTerm(breakdown, "memory")
            + adminText("admin-command-score-failure-prefix", ",fail:") + scoreTerm(breakdown, "recentFailure");
    }

    private String scoreTerm(String breakdown, String keyPrefix) {
        String pressureKey = keyPrefix + "Pressure";
        if (keyPrefix.equals("recentFailure")) {
            double pressure = scorePartValue(breakdown, pressureKey, "recentFailurePenalty");
            return seconds(pressure) + "x" + seconds(doubleValue(breakdown, keyPrefix + "Weight")) + "=" + seconds(doubleValue(breakdown, keyPrefix + "Contribution"));
        }
        return seconds(doubleValue(breakdown, pressureKey))
            + "x" + seconds(doubleValue(breakdown, keyPrefix + "Weight"))
            + "=" + seconds(doubleValue(breakdown, keyPrefix + "Contribution"));
    }

    private double scorePartValue(String breakdown, String primaryKey, String fallbackKey) {
        double primary = doubleValue(breakdown, primaryKey);
        if (primary != 0.0D || breakdown.contains("\"" + primaryKey + "\"")) {
            return primary;
        }
        return doubleValue(breakdown, fallbackKey);
    }

    private String nodeActionSummaryMessage(String label, String nodeId, String body) {
        if (body == null || body.isBlank()) {
            return label + adminText("admin-command-node-action-accepted-node-prefix", ": accepted node=") + nodeId;
        }
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return label + ": " + (boolValue(body, "accepted") ? adminText("admin-command-node-action-accepted", "accepted") : adminText("admin-command-node-action-rejected", "rejected")) + adminText("admin-command-node-action-node-prefix", " node=") + nodeId + adminText("admin-command-node-action-code-prefix", " code=") + code;
        }
        return label + ": " + (boolValue(body, "accepted") ? adminText("admin-command-node-action-accepted", "accepted") : adminText("admin-command-node-action-requested", "requested")) + adminText("admin-command-node-action-node-prefix", " node=") + nodeId;
    }

    private String arrayValue(String body, String field) {
        String needle = "\"" + field + "\":[";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length() - 1;
        int depth = 0;
        for (int i = start; i < body.length(); i++) {
            char current = body.charAt(i);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private int matchingObjectEnd(String value, int objectStart) {
        int depth = 0;
        for (int i = objectStart; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String objectValue(String body, String field) {
        String needle = "\"" + field + "\":{";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length() - 1;
        int depth = 0;
        for (int i = start; i < body.length(); i++) {
            char current = body.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private boolean boolValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return false;
        }
        start += needle.length();
        while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
            start++;
        }
        return body.startsWith("true", start);
    }

    private long longValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && (body.charAt(end) == '-' || Character.isDigit(body.charAt(end)))) {
            end++;
        }
        if (end == start) {
            return 0L;
        }
        try {
            return Long.parseLong(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private double doubleValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0.0D;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && (body.charAt(end) == '-' || body.charAt(end) == '+' || body.charAt(end) == '.' || Character.isDigit(body.charAt(end)))) {
            end++;
        }
        try {
            return Double.parseDouble(body.substring(start, end));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private String seconds(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private void message(CommandSender sender, String text) {
        agent.plugin().getServer().getScheduler().runTask(agent.plugin(), () -> sender.sendMessage(text));
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
        for (String command : commandPage.entries()) {
            sender.sendMessage(CommandListPolicy.ENTRY_PREFIX + command);
        }
        if (commandPage.previousCommand() != null) {
            sender.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.previousCommand());
        }
        if (commandPage.nextCommand() != null) {
            sender.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.nextCommand());
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
            case "status", "config", "cache", "addons", "node", "island", "player", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "block-values", "upgrade-rules", "templates", "migrate-superiorskyblock2", "reload" -> "cloudislands.admin." + root;
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
        return coreApiClient.islandInfoByName(value).thenApply(body -> {
            UUID islandId = uuidValue(body, "islandId");
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
            return coreApiClient.playerInfoByName(value).thenApply(body -> {
                UUID playerUuid = uuidValue(body, "playerUuid");
                if (playerUuid == null) {
                    sender.sendMessage(adminText("admin-command-player-not-found", "플레이어를 찾지 못했습니다: ") + value);
                }
                return playerUuid;
            });
        }
    }

    private UUID uuidValue(String body, String field) {
        String value = textValue(body, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String textValue(String body, String field) {
        String needle = "\"" + field + "\":\"";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
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
