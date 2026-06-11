package kr.lunaf.cloudislands.paper.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AdminCommandController implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = List.of("status", "cache", "node", "island", "player", "jobs", "route", "template", "migrate-superiorskyblock2", "reload");
    private static final List<String> CACHE_COMMANDS = List.of("clear");
    private static final List<String> NODE_COMMANDS = List.of("menu", "list", "info", "drain", "undrain", "sweep", "kickall", "shutdown-safe");
    private static final List<String> ISLAND_COMMANDS = List.of("info", "where", "activate", "deactivate", "migrate", "save", "snapshot", "snapshots", "restore", "rollback", "quarantine", "repair", "delete");
    private static final List<String> PLAYER_COMMANDS = List.of("info", "setisland", "clearisland");
    private static final List<String> JOB_COMMANDS = List.of("list", "retry", "cancel", "recover");
    private static final List<String> ROUTE_COMMANDS = List.of("debug", "ticket", "clear");
    private static final List<String> TEMPLATE_COMMANDS = List.of("list", "upsert", "enable", "disable");
    private static final List<String> MIGRATION_COMMANDS = List.of("scan", "dryrun", "dry-run", "import", "verify", "rollback");
    private final CloudIslandsPaperAgent agent;
    private final CoreApiClient coreApiClient;
    private final String nodeId;

    public AdminCommandController(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId) {
        this.agent = agent;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cloudislands.admin")) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("CloudIslands agent role=" + agent.role() + " node=" + nodeId);
            return true;
        }
        if (args[0].equalsIgnoreCase("cache") && args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            agent.permissionCache().invalidateAll();
            run(sender, "CloudIslands local cache cleared. Core cache clear", coreApiClient.clearCache());
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            run(sender, "Core reload", coreApiClient.reload());
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
        if (args[0].equalsIgnoreCase("template")) {
            return handleTemplate(sender, args);
        }
        if (args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            return handleSuperiorSkyblock2Migration(sender, args);
        }
        usage(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("cloudislands.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return matches(ROOT_COMMANDS, args[0]);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("route")) {
            return matches(ROUTE_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("template")) {
            return matches(TEMPLATE_COMMANDS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            return matches(MIGRATION_COMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("node")) {
            return matches(List.of(nodeId), args[2]);
        }
        return List.of();
    }

    private boolean handleNode(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) {
                AdminNodeMenu.open(player, nodeId);
            } else {
                sender.sendMessage("플레이어만 노드 관리 메뉴를 열 수 있습니다.");
            }
            return true;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Node list", coreApiClient.listNodes());
            return true;
        }
        String targetNode = args.length > 2 ? args[2] : nodeId;
        if (args[1].equalsIgnoreCase("info")) {
            run(sender, "Node info", coreApiClient.nodeInfo(targetNode));
            return true;
        }
        if (args[1].equalsIgnoreCase("drain")) {
            run(sender, "Node drain", coreApiClient.drainNode(targetNode));
            return true;
        }
        if (args[1].equalsIgnoreCase("undrain")) {
            run(sender, "Node undrain", coreApiClient.undrainNode(targetNode));
            return true;
        }
        if (args[1].equalsIgnoreCase("sweep")) {
            run(sender, "Node sweep", coreApiClient.sweepNode(targetNode));
            return true;
        }
        if (args[1].equalsIgnoreCase("kickall")) {
            run(sender, "Node kickall", coreApiClient.kickAllNode(targetNode, args.length > 3 ? joined(args, 3) : "admin"));
            return true;
        }
        if (args[1].equalsIgnoreCase("shutdown-safe")) {
            run(sender, "Node shutdown-safe", coreApiClient.shutdownNodeSafely(targetNode, args.length > 3 ? joined(args, 3) : "admin"));
            return true;
        }
        sender.sendMessage("사용법: /ciadmin node list|info|drain|undrain|sweep|kickall|shutdown-safe [node]");
        return true;
    }

    private boolean handleIsland(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법: /ciadmin island info|where|activate|deactivate|migrate|save|snapshot|snapshots|restore|rollback|quarantine|repair|delete <islandUuid> [값]");
            return true;
        }
        UUID islandId = uuid(sender, args[2]);
        if (islandId == null) {
            return true;
        }
        if (args[1].equalsIgnoreCase("info")) {
            run(sender, "Island info", coreApiClient.adminIslandInfo(islandId));
            return true;
        }
        if (args[1].equalsIgnoreCase("where")) {
            run(sender, "Island where", coreApiClient.adminIslandWhere(islandId));
            return true;
        }
        if (args[1].equalsIgnoreCase("activate")) {
            run(sender, "Island activate", coreApiClient.activateIsland(islandId));
            return true;
        }
        if (args[1].equalsIgnoreCase("deactivate")) {
            run(sender, "Island deactivate", coreApiClient.deactivateIsland(islandId));
            return true;
        }
        if (args[1].equalsIgnoreCase("migrate")) {
            if (args.length < 4) {
                sender.sendMessage("대상 노드를 입력해주세요.");
                return true;
            }
            run(sender, "Island migrate", coreApiClient.migrateIsland(islandId, args[3]));
            return true;
        }
        if (args[1].equalsIgnoreCase("save") || args[1].equalsIgnoreCase("snapshot")) {
            String reason = args.length > 3 ? joined(args, 3) : "ADMIN_MANUAL";
            run(sender, "Island snapshot", coreApiClient.requestIslandSnapshotResult(islandId, reason));
            return true;
        }
        if (args[1].equalsIgnoreCase("snapshots")) {
            int limit = args.length > 3 ? (int) number(args[3], 20L) : 20;
            run(sender, "Island snapshots", coreApiClient.listIslandSnapshots(islandId, Math.max(1, Math.min(limit, 50))));
            return true;
        }
        if (args[1].equalsIgnoreCase("restore") || args[1].equalsIgnoreCase("rollback")) {
            if (args.length < 4) {
                sender.sendMessage("스냅샷 번호를 입력해주세요.");
                return true;
            }
            long snapshotNo = number(args[3], 0L);
            if (snapshotNo <= 0L) {
                sender.sendMessage("스냅샷 번호가 올바르지 않습니다: " + args[3]);
                return true;
            }
            run(sender, "Island restore", coreApiClient.restoreIslandSnapshotResult(islandId, snapshotNo));
            return true;
        }
        if (args[1].equalsIgnoreCase("quarantine")) {
            run(sender, "Island quarantine", coreApiClient.quarantineIsland(islandId, args.length > 3 ? joined(args, 3) : "admin"));
            return true;
        }
        if (args[1].equalsIgnoreCase("repair")) {
            run(sender, "Island repair", coreApiClient.repairIsland(islandId, args.length > 3 ? joined(args, 3) : "admin"));
            return true;
        }
        if (args[1].equalsIgnoreCase("delete")) {
            run(sender, "Island delete", coreApiClient.adminDeleteIsland(islandId));
            return true;
        }
        sender.sendMessage("사용법: /ciadmin island info|where|activate|deactivate|migrate|save|snapshot|snapshots|restore|rollback|quarantine|repair|delete <islandUuid> [값]");
        return true;
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법: /ciadmin player info|setisland|clearisland <playerUuid> [islandUuid]");
            return true;
        }
        UUID playerUuid = uuid(sender, args[2]);
        if (playerUuid == null) {
            return true;
        }
        if (args[1].equalsIgnoreCase("info")) {
            run(sender, "Player info", coreApiClient.playerInfo(playerUuid));
            return true;
        }
        if (args[1].equalsIgnoreCase("setisland")) {
            if (args.length < 4) {
                sender.sendMessage("섬 UUID를 입력해주세요.");
                return true;
            }
            UUID islandId = uuid(sender, args[3]);
            if (islandId != null) {
                run(sender, "Player setisland", coreApiClient.setPlayerIsland(playerUuid, islandId));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("clearisland")) {
            run(sender, "Player clearisland", coreApiClient.clearPlayerIsland(playerUuid));
            return true;
        }
        sender.sendMessage("사용법: /ciadmin player info|setisland|clearisland <playerUuid> [islandUuid]");
        return true;
    }

    private boolean handleJobs(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Jobs list", coreApiClient.listJobs());
            return true;
        }
        if (args[1].equalsIgnoreCase("recover")) {
            long minIdleMillis = args.length > 2 ? number(args[2], 60000L) : 60000L;
            int maxJobs = args.length > 3 ? (int) number(args[3], 20L) : 20;
            run(sender, "Jobs recover", coreApiClient.recoverJobs(nodeId, minIdleMillis, maxJobs));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("작업 ID를 입력해주세요.");
            return true;
        }
        UUID jobId = uuid(sender, args[2]);
        if (jobId == null) {
            return true;
        }
        if (args[1].equalsIgnoreCase("retry")) {
            run(sender, "Job retry", coreApiClient.retryJob(jobId));
            return true;
        }
        if (args[1].equalsIgnoreCase("cancel")) {
            run(sender, "Job cancel", coreApiClient.cancelJob(jobId));
            return true;
        }
        sender.sendMessage("사용법: /ciadmin jobs list|retry|cancel|recover");
        return true;
    }

    private boolean handleRoute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법: /ciadmin route debug <playerUuid> | ticket <ticketUuid> | clear <playerUuid> <ticketUuid>");
            return true;
        }
        if (args[1].equalsIgnoreCase("debug")) {
            UUID playerUuid = uuid(sender, args[2]);
            if (playerUuid != null) {
                run(sender, "Route debug", coreApiClient.debugRoutes(playerUuid));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("ticket")) {
            UUID ticketId = uuid(sender, args[2]);
            if (ticketId != null) {
                run(sender, "Route ticket", coreApiClient.routeTicket(ticketId));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            if (args.length < 4) {
                sender.sendMessage("플레이어 UUID와 티켓 UUID를 입력해주세요.");
                return true;
            }
            UUID playerUuid = uuid(sender, args[2]);
            UUID ticketId = uuid(sender, args[3]);
            if (playerUuid != null && ticketId != null) {
                run(sender, "Route clear", coreApiClient.clearRoute(playerUuid, ticketId));
            }
            return true;
        }
        sender.sendMessage("사용법: /ciadmin route debug <playerUuid> | ticket <ticketUuid> | clear <playerUuid> <ticketUuid>");
        return true;
    }

    private boolean handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Template list", coreApiClient.listTemplates());
            return true;
        }
        if (args[1].equalsIgnoreCase("upsert")) {
            if (args.length < 4) {
                sender.sendMessage("사용법: /ciadmin template upsert <id> <name> [enabled] [minNodeVersion]");
                return true;
            }
            boolean enabled = args.length < 5 || Boolean.parseBoolean(args[4]);
            String minNodeVersion = args.length > 5 ? args[5] : "";
            run(sender, "Template upsert", coreApiClient.upsertTemplate(args[2], args[3], enabled, minNodeVersion));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("템플릿 ID를 입력해주세요.");
            return true;
        }
        if (args[1].equalsIgnoreCase("enable")) {
            run(sender, "Template enable", coreApiClient.enableTemplate(args[2]));
            return true;
        }
        if (args[1].equalsIgnoreCase("disable")) {
            run(sender, "Template disable", coreApiClient.disableTemplate(args[2]));
            return true;
        }
        sender.sendMessage("사용법: /ciadmin template list|upsert|enable|disable");
        return true;
    }

    private boolean handleSuperiorSkyblock2Migration(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1] : "scan";
        if (!MIGRATION_COMMANDS.contains(action.toLowerCase(Locale.ROOT))) {
            sender.sendMessage("사용법: /ciadmin migrate-superiorskyblock2 scan [path] | dryrun | import | verify | rollback");
            return true;
        }
        String path = args.length > 2 ? joined(args, 2) : "plugins/SuperiorSkyblock2";
        run(sender, "SuperiorSkyblock2 migration " + action, coreApiClient.migrateSuperiorSkyblock2(action, path));
        return true;
    }

    private void run(CommandSender sender, String action, CompletableFuture<String> future) {
        future.thenAccept(body -> message(sender, action + " 완료" + (body == null || body.isBlank() ? "" : ": " + body)))
            .exceptionally(error -> {
                message(sender, action + " 실패");
                return null;
            });
    }

    private void message(CommandSender sender, String text) {
        agent.plugin().getServer().getScheduler().runTask(agent.plugin(), () -> sender.sendMessage(text));
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage("사용법: /" + label + " status, cache clear, node list, island info <uuid>, player info <uuid>, jobs list, route debug <uuid>, template list, migrate-superiorskyblock2 scan, reload");
    }

    private UUID uuid(CommandSender sender, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("UUID 형식이 올바르지 않습니다: " + value);
            return null;
        }
    }

    private long number(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
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
}
