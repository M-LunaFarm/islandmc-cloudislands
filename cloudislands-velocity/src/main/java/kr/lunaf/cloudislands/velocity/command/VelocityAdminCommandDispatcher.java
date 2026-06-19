package kr.lunaf.cloudislands.velocity.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.UUID;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;
import net.kyori.adventure.text.Component;

final class VelocityAdminCommandDispatcher extends VelocityCommandSupport {
    VelocityAdminCommandDispatcher(ProxyServer proxy, VelocityRoutingController routingController, VelocityConfig config) {
        super(proxy, routingController, config);
    }

    public void dispatchAdmin(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            player.sendMessage(Component.text(routingController.statusSummary()));
            return;
        }
        if (isCommandListRequest(args)) {
            sendCommandList(player, "CloudIslands 관리자 명령어 목록", adminCommands(), commandListPage(args), "ciadmin command list");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("info")) {
            routingController.adminIslandInfoTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("where")) {
            routingController.adminIslandWhereTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("tp")) {
            player.sendActionBar(Component.text("섬으로 이동하는 중입니다."));
            routingController.adminTeleportIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("save")) {
            routingController.snapshotTarget(player, args[2], "ADMIN_SAVE");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("snapshot")) {
            routingController.snapshotTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "MANUAL");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("snapshots")) {
            routingController.listSnapshotsTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("rollback")) {
            routingController.restoreTarget(player, args[2], parseLongOrZero(args[3]));
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("restore")) {
            routingController.restoreTarget(player, args[2], parseLongOrZero(args[3]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("delete")) {
            routingController.adminDeleteIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("repair")) {
            routingController.repairIslandTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("activate")) {
            routingController.activateIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("deactivate")) {
            routingController.deactivateIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("migrate")) {
            routingController.migrateIslandTarget(player, args[2], args[3]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("quarantine")) {
            routingController.quarantineIslandTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin");
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("debug")) {
            if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
                routingController.debugRoutes(player, new UUID(0L, 0L));
            } else {
                routingController.debugRoutesTarget(player, args[2]);
            }
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("ticket")) {
            routingController.routeTicketTarget(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("clear")) {
            UUID ticketId = args.length > 3 ? parseUuidOrNil(args[3]) : new UUID(0L, 0L);
            routingController.clearRouteTarget(player, args.length > 2 ? args[2] : "", ticketId);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("rankings")) {
            String type = args.length > 1 ? args[1] : "level";
            int limit = args.length > 2 ? (int) parseLongOrZero(args[2]) : 10;
            if (type.equalsIgnoreCase("worth") || type.equalsIgnoreCase("value")) {
                routingController.showWorthRanking(player, limit);
            } else {
                routingController.showLevelRanking(player, limit);
            }
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("cache") && args[1].equalsIgnoreCase("clear")) {
            routingController.clearCache(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("events")) {
            routingController.listEvents(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("audit")) {
            routingController.listAuditLogs(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("metrics")) {
            routingController.metrics(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
            routingController.coreConfig(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("storage")) {
            routingController.storageStatus(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("addons") && args.length > 1 && args[1].equalsIgnoreCase("endpoints")) {
            routingController.addonEndpoints(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("addons") && (args.length == 1 || args[1].equalsIgnoreCase("state") || args[1].equalsIgnoreCase("state-summary") || args[1].equalsIgnoreCase("list"))) {
            routingController.addonStateSummary(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("block-values") && (args.length == 1 || args[1].equalsIgnoreCase("list"))) {
            routingController.listBlockValues(player);
            return;
        }
        if (args.length >= 6 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            routingController.setBlockValue(player, args[2], args[3], parseLongOrZero(args[4]), parseLongOrZero(args[5]));
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("upgrade-rules")) {
            routingController.listUpgradeRules(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            routingController.reload(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            if (!config.superiorSkyblock2MigrationEnabled()) {
                player.sendMessage(Component.text("SuperiorSkyblock2 migration is disabled by config."));
                return;
            }
            String action = args.length > 1 ? args[1] : "scan";
            if (action.equalsIgnoreCase("import") && args.length < 3) {
                player.sendMessage(Component.text("사용법: /ciadmin migrate-superiorskyblock2 import <approvalToken>"));
                return;
            }
            String path = args.length > 2 ? joinArgs(args, 2) : "";
            routingController.migrateSuperiorSkyblock2(player, action, path);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("info")) {
            routingController.playerInfoTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("setisland")) {
            routingController.setPlayerIslandTarget(player, args[2], parseUuidOrNil(args[3]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("clearisland")) {
            routingController.clearPlayerIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && (args.length == 1 || args[1].equalsIgnoreCase("list"))) {
            routingController.listTemplates(player);
            return;
        }
        if (args.length >= 4 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            routingController.upsertTemplate(player, args[2], args[3], args.length > 4 ? parseToggle(args, 4, true) : true, args.length > 5 ? args[5] : "");
            return;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("enable")) {
            routingController.enableTemplate(player, args[2]);
            return;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("disable")) {
            routingController.disableTemplate(player, args[2]);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("node") && (args.length == 1 || args[1].equalsIgnoreCase("menu") || args[1].equalsIgnoreCase("list"))) {
            routingController.listNodes(player);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("info")) {
            routingController.nodeInfo(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("islands")) {
            routingController.nodeIslands(player, args[2], args.length > 3 ? (int) parseLongOrZero(args[3]) : 50);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("drain")) {
            routingController.drainNode(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("undrain")) {
            routingController.undrainNode(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("sweep")) {
            routingController.sweepNode(player, args.length > 2 ? args[2] : "");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("kickall")) {
            routingController.kickAllNode(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin-request");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("shutdown-safe")) {
            routingController.shutdownSafeNode(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin-request");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("jobs") && (args.length == 1 || args[1].equalsIgnoreCase("list"))) {
            routingController.listJobs(player);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("retry")) {
            routingController.retryJob(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("cancel")) {
            routingController.cancelJob(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            routingController.recoverJobs(player, args.length > 2 ? args[2] : "recovery", args.length > 3 ? parseLongOrZero(args[3]) : 60000L, args.length > 4 ? (int) parseLongOrZero(args[4]) : 16);
            return;
        }
            sendCommandList(player, "CloudIslands 관리자 명령어 목록", adminCommands(), 1, "ciadmin command list");
    }


}
