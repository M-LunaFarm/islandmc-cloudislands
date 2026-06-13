package kr.lunaf.cloudislands.paper.admin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AdminCommandController implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = List.of("help", "commands", "command", "command-list", "명령어", "명령어목록", "status", "config", "cache", "node", "island", "player", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "block-values", "upgrade-rules", "template", "templates", "migrate-superiorskyblock2", "reload");
    private static final List<String> CACHE_COMMANDS = List.of("clear");
    private static final List<String> NODE_COMMANDS = List.of("menu", "list", "info", "islands", "drain", "undrain", "sweep", "kickall", "shutdown-safe");
    private static final List<String> ISLAND_COMMANDS = List.of("info", "where", "tp", "activate", "deactivate", "migrate", "save", "snapshot", "snapshots", "restore", "rollback", "quarantine", "repair", "delete");
    private static final List<String> PLAYER_COMMANDS = List.of("info", "setisland", "clearisland");
    private static final List<String> JOB_COMMANDS = List.of("list", "retry", "cancel", "recover");
    private static final List<String> ROUTE_COMMANDS = List.of("debug", "ticket", "clear");
    private static final List<String> RANKING_COMMANDS = List.of("level", "worth");
    private static final List<String> BLOCK_VALUE_COMMANDS = List.of("list", "set");
    private static final List<String> BLOCK_VALUE_MATERIALS = List.of("minecraft:stone", "minecraft:diamond_block", "minecraft:emerald_block", "minecraft:spawner");
    private static final List<String> TEMPLATE_COMMANDS = List.of("list", "upsert", "enable", "disable");
    private static final List<String> MIGRATION_COMMANDS = List.of("scan", "dryrun", "dry-run", "extract", "extract-worlds", "world-extract", "import", "verify", "rollback");
    private static final List<String> NODE_DANGER_REASONS = List.of("maintenance", "restart", "drain");
    private static final List<String> HELP_COMMANDS = List.of(
        "ciadmin status",
        "ciadmin config",
        "ciadmin help [page]",
        "ciadmin command list [page]",
        "ciadmin cache clear",
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
        "ciadmin migrate-superiorskyblock2 scan [path]",
        "ciadmin migrate-superiorskyblock2 dryrun [path]",
        "ciadmin migrate-superiorskyblock2 dry-run [path]",
        "ciadmin migrate-superiorskyblock2 extract [outputPath]",
        "ciadmin migrate-superiorskyblock2 import <approvalToken>",
        "ciadmin migrate-superiorskyblock2 verify [path]",
        "ciadmin migrate-superiorskyblock2 rollback [path]",
        "ciadmin reload"
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
            return matches(ROOT_COMMANDS, args[0]);
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
            return matches(MIGRATION_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
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

    private boolean handleNode(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) {
                AdminNodeMenu.open(player, nodeId, messages);
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
        sender.sendMessage(adminText("admin-command-node-usage", "사용법: /ciadmin node menu|list|info|islands|drain|undrain|sweep|kickall|shutdown-safe [node] [limit]"));
        return true;
    }

    private boolean handleIsland(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(adminText("admin-command-island-usage", "사용법: /ciadmin island info|where|tp|activate|deactivate|migrate|save|snapshot|snapshots|restore|rollback|quarantine|repair|delete <islandUuid|islandName> [값]"));
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
        if (args[1].equalsIgnoreCase("save") || args[1].equalsIgnoreCase("snapshot")) {
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
            run(sender, "Island restore", coreApiClient.restoreIslandSnapshotResult(islandId, snapshotNo).thenApply(body -> actionResultMessage("Island restore", islandId.toString(), body)));
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
        sender.sendMessage(adminText("admin-command-island-usage", "사용법: /ciadmin island info|where|tp|activate|deactivate|migrate|save|snapshot|snapshots|restore|rollback|quarantine|repair|delete <islandUuid|islandName> [값]"));
        return true;
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(adminText("admin-command-player-usage", "사용법: /ciadmin player info|setisland|clearisland <playerUuid|playerName> [islandUuid]"));
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
            sender.sendMessage(adminText("admin-command-player-usage", "사용법: /ciadmin player info|setisland|clearisland <playerUuid|playerName> [islandUuid]"));
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
        sender.sendMessage(adminText("admin-command-jobs-usage", "사용법: /ciadmin jobs list|retry <jobId>|cancel <jobId>|recover [nodeId] [minIdleMillis] [maxJobs]"));
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
        sender.sendMessage(adminText("admin-command-rankings-usage", "사용법: /ciadmin rankings level|worth [limit]"));
        return true;
    }

    private boolean handleRoute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(adminText("admin-command-route-usage", "사용법: /ciadmin route debug [all|playerUuid|playerName] | ticket <ticketUuid|playerUuid|playerName> | clear <playerUuid|playerName> [ticketUuid]"));
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
        sender.sendMessage(adminText("admin-command-route-usage", "사용법: /ciadmin route debug [all|playerUuid|playerName] | ticket <ticketUuid|playerUuid|playerName> | clear <playerUuid|playerName> [ticketUuid]"));
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
        sender.sendMessage(adminText("admin-command-block-values-usage", "사용법: /ciadmin block-values list|set <materialKey> <worth> <levelPoints> <limit>"));
        return true;
    }

    private boolean handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Template list", coreApiClient.listTemplates().thenApply(this::templateListMessage));
            return true;
        }
        if (args[1].equalsIgnoreCase("upsert")) {
            if (args.length < 4) {
                sender.sendMessage(adminText("admin-command-template-upsert-usage", "사용법: /ciadmin template|templates upsert <id> <name> [enabled|disabled] [minNodeVersion]"));
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
        sender.sendMessage(adminText("admin-command-template-usage", "사용법: /ciadmin template|templates list|upsert|enable|disable"));
        return true;
    }

    private boolean handleSuperiorSkyblock2Migration(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1] : "scan";
        if (!MIGRATION_COMMANDS.contains(action.toLowerCase(Locale.ROOT))) {
            sender.sendMessage(adminText("admin-command-migration-usage", "사용법: /ciadmin migrate-superiorskyblock2 scan|dryrun|dry-run|extract|import|verify|rollback [path]"));
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

    private String migrationMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Migration: no response";
        }
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return "Migration: failed code=" + code;
        }
        String state = textValue(body, "state");
        String path = textValue(body, "path");
        String manifestPath = textValue(body, "manifestPath");
        String reportPath = textValue(body, "reportPath");
        String approvalToken = textValue(body, "approvalToken");
        String issues = arrayValue(body, "issues");
        long manifests = longValue(body, "manifests");
        long importedIslands = longValue(body, "importedIslands");
        long removedIslands = longValue(body, "removedIslands");
        StringBuilder builder = new StringBuilder("Migration: state=")
            .append(state.isBlank() ? "UNKNOWN" : state)
            .append(" manifests=")
            .append(manifests);
        if (!path.isBlank()) {
            builder.append(" path=").append(path);
        }
        if (!manifestPath.isBlank()) {
            builder.append(" manifest=").append(manifestPath);
        }
        if (!reportPath.isBlank()) {
            builder.append(" report=").append(reportPath);
        }
        if (!approvalToken.isBlank()) {
            builder.append(" approval=").append(approvalToken);
        }
        if (body.contains("\"canImport\"")) {
            builder.append(" canImport=").append(boolValue(body, "canImport"));
        }
        if (body.contains("\"imported\"")) {
            builder.append(" imported=").append(boolValue(body, "imported"))
                .append(" islands=")
                .append(importedIslands);
        }
        if (body.contains("\"passed\"")) {
            builder.append(" passed=").append(boolValue(body, "passed"))
                .append(" expected=")
                .append(longValue(body, "expected"));
        }
        if (body.contains("\"rolledBack\"")) {
            builder.append(" rolledBack=").append(boolValue(body, "rolledBack"))
                .append(" removed=")
                .append(removedIslands);
        }
        if (body.contains("\"extractedBundles\"")) {
            builder.append(" extracted=")
                .append(longValue(body, "extractedBundles"))
                .append(" files=")
                .append(longValue(body, "extractedFiles"))
                .append(" bytes=")
                .append(longValue(body, "extractedBytes"));
        }
        if (body.contains("\"members\"")) {
            builder.append(" members=").append(longValue(body, "members"))
                .append(" homes=").append(longValue(body, "homes"))
                .append(" warps=").append(longValue(body, "warps"))
                .append(" perms=").append(longValue(body, "permissions"));
        }
        if (body.contains("\"blockingIssues\"")) {
            builder.append(" blocking=").append(longValue(body, "blockingIssues"))
                .append(" warnings=").append(longValue(body, "warningIssues"));
        }
        builder.append(migrationIssuesSuffix(issues));
        return builder.toString();
    }

    private String migrationIssuesSuffix(String issues) {
        if (issues.isBlank()) {
            return " issues=0";
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
        return " issues=" + total
            + " blocking=" + blocking
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
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeUTF("Connect");
                output.writeUTF(targetServerName);
                player.sendPluginMessage(agent.plugin(), "BungeeCord", bytes.toByteArray());
                player.sendMessage(adminText("admin-command-route-connecting", "섬으로 이동합니다."));
            } catch (IOException exception) {
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
        return "(failures=" + failures
            + ", up=" + seconds(doubleValue(storage, "uploadSeconds")) + "s"
            + ", down=" + seconds(doubleValue(storage, "downloadSeconds")) + "s)";
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
        return "Node sweep: nodes=" + (swept.isEmpty() ? "none" : String.join(",", swept)) + " recoveryRequired=" + recoveryRequired;
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
            return label + ": accepted target=" + shortId(targetId);
        }
        String code = textValue(body, "code");
        boolean accepted = body.contains("\"accepted\"") ? boolValue(body, "accepted") : !body.contains("\"accepted\":false");
        StringBuilder builder = new StringBuilder(label)
            .append(": ")
            .append(accepted ? "accepted" : "rejected")
            .append(" target=")
            .append(compactTarget(targetId));
        if (!code.isBlank()) {
            builder.append(" code=").append(code);
            String detail = adminCodeDetail(code);
            if (!detail.isBlank()) {
                builder.append(" detail=").append(detail);
            }
        }
        String islandId = textValue(body, "islandId");
        if (!islandId.isBlank() && !islandId.equals(targetId)) {
            builder.append(" island=").append(shortId(islandId));
        }
        String materialKey = textValue(body, "materialKey");
        if (!materialKey.isBlank()) {
            builder.append(" material=").append(materialKey);
        }
        String worth = textValue(body, "worth");
        if (!worth.isBlank()) {
            builder.append(" worth=").append(worth);
        }
        if (body.contains("\"snapshotNo\"")) {
            builder.append(" snapshot=").append(longValue(body, "snapshotNo"));
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
            + adminText("admin-command-core-config-storage-prefix", " storage=") + textValue(body, "storageType")
            + " pool=" + textValue(body, "islandPool")
            + " dbPool=" + longValue(body, "databasePoolSize")
            + " softFull=" + textValue(body, "softFullPolicy")
            + " hardFull=" + textValue(body, "hardFullPolicy")
            + " migration=" + textValue(body, "migrationPolicy")
            + " ticketTtl=" + longValue(body, "routeTicketTtlSeconds") + "s"
            + " prepTtl=" + longValue(body, "routePreparingTicketTtlSeconds") + "s"
            + " mtls=" + boolValue(body, "requireMtls")
            + " ipAllowlist=" + boolValue(body, "ipAllowlistEnabled");
    }

    private String eventListMessage(String body) {
        String events = arrayValue(body, "events");
        if (events.isBlank()) {
            return "Events: empty";
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
                + (islandId.isBlank() ? "" : " island=" + islandId)
                + (ticketId.isBlank() ? "" : " ticket=" + shortId(ticketId))
                + (playerUuid.isBlank() ? "" : " player=" + shortId(playerUuid))
                + (action.isBlank() ? "" : " action=" + action)
                + (reason.isBlank() ? "" : " reason=" + reason)
                + (requestedNode.isBlank() ? "" : " requestedNode=" + requestedNode)
                + (clearedSession.isBlank() ? "" : " session=" + clearedSession)
                + (clearedTicket.isBlank() ? "" : " ticketCleared=" + clearedTicket)
                + (nodeId.isBlank() ? "" : " node=" + nodeId)
                + (occurredAt.isBlank() ? "" : " at=" + occurredAt));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "Events: empty" : "Events: " + String.join(" | ", entries);
    }

    private String auditListMessage(String body) {
        String audit = arrayValue(body, "audit");
        if (audit.isBlank()) {
            return "Audit: empty";
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
                + (targetType.isBlank() && targetId.isBlank() ? "" : " target=" + targetType + ":" + targetId)
                + (actorType.isBlank() ? "" : " actor=" + actorType)
                + (createdAt.isBlank() ? "" : " at=" + createdAt));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "Audit: empty" : "Audit: " + String.join(" | ", entries);
    }

    private String routeDebugMessage(String body) {
        String sessions = arrayValue(body, "sessions");
        String tickets = arrayValue(body, "tickets");
        List<String> sessionEntries = new ArrayList<>();
        List<String> ticketEntries = new ArrayList<>();
        collectSessionSummaries(sessions, sessionEntries, 5);
        collectTicketSummaries(tickets, ticketEntries, 5);
        return "Routes: sessions=" + countObjects(sessions)
            + (sessionEntries.isEmpty() ? "" : " [" + String.join(" | ", sessionEntries) + "]")
            + " tickets=" + countObjects(tickets)
            + (ticketEntries.isEmpty() ? "" : " [" + String.join(" | ", ticketEntries) + "]");
    }

    private String routeTicketMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Route ticket: not found";
        }
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return "Route ticket: failed code=" + code;
        }
        return "Route ticket: " + ticketSummary(body);
    }

    private String routeClearMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Route clear: no response";
        }
        String reason = textValue(body, "reason");
        return "Route clear: session=" + boolValue(body, "clearedSession") + " ticket=" + boolValue(body, "clearedTicket") + (reason.isBlank() ? "" : " reason=" + reason);
    }

    private String snapshotListMessage(String body) {
        String snapshots = arrayValue(body, "snapshots");
        if (snapshots.isBlank()) {
            return "Snapshots: empty";
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
                entries.add("#" + snapshotNo
                    + (reason.isBlank() ? "" : " " + reason)
                    + " size=" + sizeBytes
                    + (createdAt.isBlank() ? "" : " at=" + createdAt));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "Snapshots: empty" : "Snapshots: " + String.join(" | ", entries);
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
                + " ticket=" + shortId(ticketId)
                + (nodeId.isBlank() ? "" : " node=" + nodeId)
                + (serverName.isBlank() ? "" : " server=" + serverName)
                + (expiresAt.isBlank() ? "" : " expires=" + expiresAt));
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
        return shortId(ticketId)
            + " " + (action.isBlank() ? "UNKNOWN" : action)
            + " " + (state.isBlank() ? "UNKNOWN" : state)
            + (islandId.isBlank() ? "" : " island=" + shortId(islandId))
            + (nodeId.isBlank() ? "" : " node=" + nodeId);
    }

    private String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
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
            parts.add("world=" + world);
        }
        if (!object.contains("\"cellX\":null") && !object.contains("\"cellZ\":null")) {
            parts.add("cell=" + longValue(object, "cellX") + "," + longValue(object, "cellZ"));
        }
        return String.join(" ", parts);
    }

    private int nodeIslandLimit(String[] args) {
        return args.length > 3 ? (int) Math.max(1L, Math.min(number(args[3], 50L), 200L)) : 50;
    }

    private String nodeListSummaryMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        if (nodes.isBlank()) {
            return "Nodes: empty";
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
        return "Nodes: total=" + total
            + " starting=" + starting
            + " warming=" + warming
            + " ready=" + ready
            + " softFull=" + softFull
            + " hardFull=" + hardFull
            + " draining=" + draining
            + " shuttingDown=" + shuttingDown
            + " down=" + down
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
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
        return (id.isBlank() ? "node" : id)
            + " " + (state.isBlank() ? "UNKNOWN" : state)
            + " players=" + players + "/" + softCap + "/" + hardCap + " reserved=" + reservedSlots
            + " islands=" + activeIslands + "/" + maxActiveIslands
            + " queue=" + activationQueue + "/" + maxActivationQueue
            + " mspt=" + seconds(doubleValue(object, "mspt"))
            + " score=" + seconds(doubleValue(object, "score"))
            + scoreParts(object)
            + " activation=" + (activationEligible ? "ok" : "blocked:" + (allocationBlockReason.isBlank() ? "UNKNOWN" : allocationBlockReason))
            + " storage=" + (boolValue(object, "storageAvailable") ? "ok" : "down");
    }

    private String scoreParts(String nodeObject) {
        String breakdown = objectValue(nodeObject, "scoreBreakdown");
        if (breakdown.isBlank()) {
            return "";
        }
        return " parts=p:" + seconds(doubleValue(breakdown, "playerPressure"))
            + ",a:" + seconds(doubleValue(breakdown, "activeIslandPressure"))
            + ",m:" + seconds(doubleValue(breakdown, "msptPressure"))
            + ",q:" + seconds(doubleValue(breakdown, "activationQueuePressure"))
            + ",mem:" + seconds(doubleValue(breakdown, "memoryPressure"))
            + ",fail:" + seconds(doubleValue(breakdown, "recentFailurePenalty"));
    }

    private String nodeActionSummaryMessage(String label, String nodeId, String body) {
        if (body == null || body.isBlank()) {
            return label + ": accepted node=" + nodeId;
        }
        String code = textValue(body, "code");
        if (!code.isBlank()) {
            return label + ": " + (boolValue(body, "accepted") ? "accepted" : "rejected") + " node=" + nodeId + " code=" + code;
        }
        return label + ": " + (boolValue(body, "accepted") ? "accepted" : "requested") + " node=" + nodeId;
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
        int pageSize = 12;
        int maxPage = Math.max(1, (HELP_COMMANDS.size() + pageSize - 1) / pageSize);
        int safePage = Math.max(1, Math.min(page, maxPage));
        int from = (safePage - 1) * pageSize;
        int to = Math.min(HELP_COMMANDS.size(), from + pageSize);
        sender.sendMessage(adminText("admin-command-list-title", "CloudIslands 관리자 명령어 목록 ") + safePage + "/" + maxPage + adminText("admin-command-list-suffix", " - 1 line > 1 command"));
        for (String command : HELP_COMMANDS.subList(from, to)) {
            sender.sendMessage("> /" + command.replaceFirst("^ciadmin", label));
        }
        if (safePage < maxPage) {
            sender.sendMessage("> /" + label + " command list " + (safePage + 1));
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
        return switch (root) {
            case "status", "config", "cache", "node", "island", "player", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "block-values", "upgrade-rules", "templates", "migrate-superiorskyblock2", "reload" -> "cloudislands.admin." + root;
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
        if (args.length > 2 && args[0].equalsIgnoreCase("command") && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return (int) number(args[2], 1L);
        }
        if (args.length > 1) {
            return (int) number(args[1], 1L);
        }
        return 1;
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
}
