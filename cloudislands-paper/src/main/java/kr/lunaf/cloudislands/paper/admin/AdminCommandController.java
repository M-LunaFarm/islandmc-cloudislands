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
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AdminCommandController implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = List.of("status", "cache", "node", "island", "player", "jobs", "route", "events", "audit", "block-values", "upgrade-rules", "template", "templates", "migrate-superiorskyblock2", "reload");
    private static final List<String> CACHE_COMMANDS = List.of("clear");
    private static final List<String> NODE_COMMANDS = List.of("menu", "list", "info", "islands", "drain", "undrain", "sweep", "kickall", "shutdown-safe");
    private static final List<String> ISLAND_COMMANDS = List.of("info", "where", "tp", "activate", "deactivate", "migrate", "save", "snapshot", "snapshots", "restore", "rollback", "quarantine", "repair", "delete");
    private static final List<String> PLAYER_COMMANDS = List.of("info", "setisland", "clearisland");
    private static final List<String> JOB_COMMANDS = List.of("list", "retry", "cancel", "recover");
    private static final List<String> ROUTE_COMMANDS = List.of("debug", "ticket", "clear");
    private static final List<String> BLOCK_VALUE_COMMANDS = List.of("list", "set");
    private static final List<String> TEMPLATE_COMMANDS = List.of("list", "upsert", "enable", "disable");
    private static final List<String> MIGRATION_COMMANDS = List.of("scan", "dryrun", "dry-run", "import", "verify", "rollback");
    private static final List<String> NODE_DANGER_REASONS = List.of("maintenance", "restart", "drain");
    private final CloudIslandsPaperAgent agent;
    private final CoreApiClient coreApiClient;
    private final String nodeId;
    private final int routeWaitSeconds;

    public AdminCommandController(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId) {
        this(agent, coreApiClient, nodeId, 20);
    }

    public AdminCommandController(CloudIslandsPaperAgent agent, CoreApiClient coreApiClient, String nodeId, int routeWaitSeconds) {
        this.agent = agent;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cloudislands.admin")) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("CloudIslands agent role=" + agent.role() + " node=" + nodeId);
            sender.sendMessage("CloudIslands onlinePlayers=" + agent.plugin().getServer().getOnlinePlayers().size() + " routeWaitSeconds=" + routeWaitSeconds);
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
        if (args[0].equalsIgnoreCase("events")) {
            run(sender, "Events list", coreApiClient.listEvents());
            return true;
        }
        if (args[0].equalsIgnoreCase("audit")) {
            run(sender, "Audit logs", coreApiClient.listAuditLogs());
            return true;
        }
        if (args[0].equalsIgnoreCase("block-values")) {
            return handleBlockValues(sender, args);
        }
        if (args[0].equalsIgnoreCase("upgrade-rules")) {
            run(sender, "Upgrade rules", coreApiClient.listUpgradeRules());
            return true;
        }
        if (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) {
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
        if (args.length == 2 && args[0].equalsIgnoreCase("block-values")) {
            return matches(BLOCK_VALUE_COMMANDS, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates"))) {
            return matches(TEMPLATE_COMMANDS, args[1]);
        }
        if (args.length == 5 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            return matches(List.of("true", "false"), args[4]);
        }
        if (args.length == 6 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            return matches(List.of("1.0.0", "1.21.0", "1.21.4"), args[5]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            return matches(MIGRATION_COMMANDS, args[1]);
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
            run(sender, "Node info", coreApiClient.nodeInfo(targetNode).thenApply(this::appendLevelScanSummary));
            return true;
        }
        if (args[1].equalsIgnoreCase("islands")) {
            run(sender, "Node islands", coreApiClient.nodeIslands(targetNode, nodeIslandLimit(args)).thenApply(this::nodeIslandListMessage));
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
        sender.sendMessage("사용법: /ciadmin node menu|list|info|islands|drain|undrain|sweep|kickall|shutdown-safe [node] [limit]");
        return true;
    }

    private boolean handleIsland(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법: /ciadmin island info|where|tp|activate|deactivate|migrate|save|snapshot|snapshots|restore|rollback|quarantine|repair|delete <islandUuid|islandName> [값]");
            return true;
        }
        if (args[1].equalsIgnoreCase("info")) {
            UUID lookupId = uuidOrNull(args[2]);
            if (lookupId != null) {
                run(sender, "Island info", coreApiClient.adminIslandInfo(lookupId));
            } else {
                run(sender, "Island info", coreApiClient.islandInfoByName(args[2]));
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
                sender.sendMessage("섬을 찾지 못했습니다: " + args[2]);
                return null;
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("where")) {
            run(sender, "Island where", coreApiClient.adminIslandWhere(islandId));
            return true;
        }
        if (args[1].equalsIgnoreCase("tp")) {
            if (sender instanceof Player player) {
                routeAdminTeleport(player, islandId);
            } else {
                sender.sendMessage("플레이어만 섬으로 이동할 수 있습니다.");
            }
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
        sender.sendMessage("사용법: /ciadmin island info|where|tp|activate|deactivate|migrate|save|snapshot|snapshots|restore|rollback|quarantine|repair|delete <islandUuid|islandName> [값]");
        return true;
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법: /ciadmin player info|setisland|clearisland <playerUuid|playerName> [islandUuid]");
            return true;
        }
        resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
            if (playerUuid == null) {
                return;
            }
            if (args[1].equalsIgnoreCase("info")) {
                run(sender, "Player info", coreApiClient.playerInfo(playerUuid));
                return;
            }
            if (args[1].equalsIgnoreCase("setisland")) {
                if (args.length < 4) {
                    sender.sendMessage("섬 UUID를 입력해주세요.");
                    return;
                }
                UUID islandId = uuid(sender, args[3]);
                if (islandId != null) {
                    run(sender, "Player setisland", coreApiClient.setPlayerIsland(playerUuid, islandId));
                }
                return;
            }
            if (args[1].equalsIgnoreCase("clearisland")) {
                run(sender, "Player clearisland", coreApiClient.clearPlayerIsland(playerUuid));
                return;
            }
            sender.sendMessage("사용법: /ciadmin player info|setisland|clearisland <playerUuid|playerName> [islandUuid]");
        }).exceptionally(error -> {
            sender.sendMessage("플레이어를 찾지 못했습니다: " + args[2]);
            return null;
        });
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
        if (args.length < 2) {
            sender.sendMessage("사용법: /ciadmin route debug [all|playerUuid|playerName] | ticket <ticketUuid|playerUuid|playerName> | clear <playerUuid|playerName> [ticketUuid]");
            return true;
        }
        if (args[1].equalsIgnoreCase("debug")) {
            if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
                run(sender, "Route debug", coreApiClient.debugRoutes(new UUID(0L, 0L)));
                return true;
            }
            resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
                if (playerUuid == null) {
                    return;
                }
                run(sender, "Route debug", coreApiClient.debugRoutes(playerUuid));
            }).exceptionally(error -> {
                sender.sendMessage("플레이어를 찾지 못했습니다: " + args[2]);
                return null;
            });
            return true;
        }
        if (args[1].equalsIgnoreCase("ticket")) {
            if (args.length < 3) {
                sender.sendMessage("티켓 UUID, 플레이어 UUID 또는 플레이어 이름을 입력해주세요.");
                return true;
            }
            UUID ticketId = uuidOrNull(args[2]);
            if (ticketId != null) {
                run(sender, "Route ticket", coreApiClient.routeTicket(ticketId));
            } else {
                resolvePlayerUuid(sender, args[2]).thenAccept(playerUuid -> {
                    if (playerUuid == null) {
                        return;
                    }
                    run(sender, "Route ticket", coreApiClient.routeTicketForPlayer(playerUuid));
                }).exceptionally(error -> {
                    sender.sendMessage("플레이어를 찾지 못했습니다: " + args[2]);
                    return null;
                });
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            if (args.length < 3) {
                sender.sendMessage("플레이어 이름 또는 UUID를 입력해주세요.");
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
                run(sender, "Route clear", coreApiClient.clearRoute(playerUuid, ticketId));
            }).exceptionally(error -> {
                sender.sendMessage("플레이어를 찾지 못했습니다: " + args[2]);
                return null;
            });
            return true;
        }
        sender.sendMessage("사용법: /ciadmin route debug [all|playerUuid|playerName] | ticket <ticketUuid|playerUuid|playerName> | clear <playerUuid|playerName> [ticketUuid]");
        return true;
    }

    private boolean handleBlockValues(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Block values", coreApiClient.listBlockValues());
            return true;
        }
        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 6) {
                sender.sendMessage("사용법: /ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>");
                return true;
            }
            UUID actorUuid = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
            run(sender, "Block value set", coreApiClient.setBlockValueResult(actorUuid, args[2], args[3], number(args[4], 0L), number(args[5], 0L)));
            return true;
        }
        sender.sendMessage("사용법: /ciadmin block-values list|set");
        return true;
    }

    private boolean handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            run(sender, "Template list", coreApiClient.listTemplates());
            return true;
        }
        if (args[1].equalsIgnoreCase("upsert")) {
            if (args.length < 4) {
                sender.sendMessage("사용법: /ciadmin template|templates upsert <id> <name> [enabled] [minNodeVersion]");
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
        sender.sendMessage("사용법: /ciadmin template|templates list|upsert|enable|disable");
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

    private void routeAdminTeleport(Player player, UUID islandId) {
        coreApiClient.adminIslandTeleport(player.getUniqueId(), islandId)
            .thenAccept(ticket -> routeTicket(player, ticket, "관리자 섬 이동에 실패했습니다.", 0))
            .exceptionally(error -> {
                message(player, routeFailureMessage(error, "관리자 섬 이동에 실패했습니다."));
                return null;
            });
    }

    private String routeFailureMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CoreApiException coreError) {
                return switch (coreError.code()) {
                    case "ISLAND_LOADING_FAILED" -> "섬을 아직 이동할 수 있는 상태가 아닙니다.";
                    case "ISLAND_NOT_FOUND" -> "해당 섬을 찾을 수 없습니다.";
                    default -> fallback;
                };
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
            message(player, failureMessage);
            return null;
        }), CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }

    private void publishAndConnect(Player player, RouteTicket ticket, String failureMessage) {
        coreApiClient.publishRouteSession(ticket)
            .thenRun(() -> connectWithTicket(player, ticket.payload().getOrDefault("targetServerName", ticket.targetNode())))
            .exceptionally(error -> {
                message(player, failureMessage);
                return null;
            });
    }

    private void connectWithTicket(Player player, String targetServerName) {
        agent.plugin().getServer().getScheduler().runTask(agent.plugin(), () -> {
            if (targetServerName == null || targetServerName.isBlank()) {
                player.sendMessage("섬 이동 경로를 찾을 수 없습니다.");
                return;
            }
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeUTF("Connect");
                output.writeUTF(targetServerName);
                player.sendPluginMessage(agent.plugin(), "BungeeCord", bytes.toByteArray());
                player.sendMessage("섬으로 이동합니다.");
            } catch (IOException exception) {
                player.sendMessage("섬 이동 요청을 만들 수 없습니다.");
            }
        });
    }

    private void run(CommandSender sender, String action, CompletableFuture<String> future) {
        future.thenAccept(body -> message(sender, action + " 완료" + (body == null || body.isBlank() ? "" : ": " + body)))
            .exceptionally(error -> {
                message(sender, action + " 실패");
                return null;
        });
    }

    private String appendLevelScanSummary(String body) {
        String summary = levelScanSummary(body);
        if (summary.isBlank()) {
            return body;
        }
        return (body == null || body.isBlank() ? "" : body + " | ") + summary;
    }

    private String levelScanSummary(String body) {
        String scan = objectValue(body, "levelScan");
        if (scan.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add("레벨 스캔=" + (boolValue(scan, "running") ? "실행 중" : "대기"));
        String lastIsland = textValue(scan, "lastIsland");
        if (!lastIsland.isBlank()) {
            parts.add("마지막 섬=" + lastIsland);
        }
        long startedAt = longValue(scan, "startedAt");
        if (startedAt > 0L) {
            parts.add("시작=" + startedAt);
        }
        long finishedAt = longValue(scan, "finishedAt");
        if (finishedAt > 0L) {
            parts.add("완료=" + finishedAt);
        }
        long failedAt = longValue(scan, "failedAt");
        if (failedAt > 0L) {
            parts.add("실패=" + failedAt);
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
        StringBuilder builder = new StringBuilder("노드 섬 현황");
        if (!id.isBlank()) {
            builder.append(" ").append(id);
        }
        builder.append(": 활성 섬 ").append(active);
        if (max > 0L) {
            builder.append('/').append(max);
        }
        if (!state.isBlank()) {
            builder.append(", 상태=").append(state);
        }
        if (!server.isBlank()) {
            builder.append(", 서버=").append(server);
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
            return "노드 섬 현황" + (nodeId.isBlank() ? "" : " " + nodeId) + ": 활성 섬 없음";
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
        return "노드 섬 현황" + (nodeId.isBlank() ? "" : " " + nodeId) + ": " + (entries.isEmpty() ? "활성 섬 없음" : String.join(", ", entries));
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

    private void message(CommandSender sender, String text) {
        agent.plugin().getServer().getScheduler().runTask(agent.plugin(), () -> sender.sendMessage(text));
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage("사용법: /" + label + " status, cache clear, node menu, node list, node islands <node>, island tp <uuid>, player info <uuid>, jobs list, route debug [all|player], route ticket <ticket|player>, route clear <player> [ticket], events, audit, block-values list, upgrade-rules, template|templates list|upsert|enable|disable, migrate-superiorskyblock2 scan|dryrun|import|verify|rollback, reload");
    }

    private UUID uuid(CommandSender sender, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("UUID 형식이 올바르지 않습니다: " + value);
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
                sender.sendMessage("섬을 찾지 못했습니다: " + value);
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
                    sender.sendMessage("플레이어를 찾지 못했습니다: " + value);
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
