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
            adminActions.adminIslandInfoTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("where")) {
            adminActions.adminIslandWhereTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("tp")) {
            player.sendActionBar(Component.text("섬으로 이동하는 중입니다."));
            adminActions.adminTeleportIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("save")) {
            adminActions.snapshotTarget(player, args[2], "ADMIN_SAVE");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("snapshot")) {
            adminActions.snapshotTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "MANUAL");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("snapshots")) {
            adminActions.listSnapshotsTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("rollback")) {
            adminActions.restoreTarget(player, args[2], parseLongOrZero(args[3]));
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("restore")) {
            adminActions.restoreTarget(player, args[2], parseLongOrZero(args[3]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("delete")) {
            adminActions.adminDeleteIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("repair")) {
            adminActions.repairIslandTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("activate")) {
            adminActions.activateIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("deactivate")) {
            adminActions.deactivateIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("migrate")) {
            adminActions.migrateIslandTarget(player, args[2], args[3]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("quarantine")) {
            adminActions.quarantineIslandTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin");
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("debug")) {
            if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
                adminActions.debugRoutes(player, new UUID(0L, 0L));
            } else {
                adminActions.debugRoutesTarget(player, args[2]);
            }
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("ticket")) {
            adminActions.routeTicketTarget(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("clear")) {
            UUID ticketId = args.length > 3 ? parseUuidOrNil(args[3]) : new UUID(0L, 0L);
            adminActions.clearRouteTarget(player, args.length > 2 ? args[2] : "", ticketId);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("rankings")) {
            String type = args.length > 1 ? args[1] : "level";
            int limit = args.length > 2 ? (int) parseLongOrZero(args[2]) : 10;
            if (type.equalsIgnoreCase("worth") || type.equalsIgnoreCase("value")) {
                playerProgression.showWorthRanking(player, limit);
            } else {
                playerProgression.showLevelRanking(player, limit);
            }
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("cache") && args[1].equalsIgnoreCase("clear")) {
            adminActions.clearCache(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("events")) {
            adminActions.listEvents(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("audit")) {
            adminActions.listAuditLogs(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("metrics")) {
            adminActions.metrics(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
            adminActions.coreConfig(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("storage")) {
            adminActions.storageStatus(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("addons") && args.length > 1 && args[1].equalsIgnoreCase("endpoints")) {
            adminActions.addonEndpoints(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("addons") && (args.length == 1 || args[1].equalsIgnoreCase("state") || args[1].equalsIgnoreCase("state-summary") || args[1].equalsIgnoreCase("list"))) {
            adminActions.addonStateSummary(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("block-values") && (args.length == 1 || args[1].equalsIgnoreCase("list"))) {
            adminActions.listBlockValues(player);
            return;
        }
        if (args.length >= 6 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            adminActions.setBlockValue(player, args[2], args[3], parseLongOrZero(args[4]), parseLongOrZero(args[5]));
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("upgrade-rules")) {
            playerProgression.listUpgradeRules(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            adminActions.reload(player);
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
            adminActions.migrateSuperiorSkyblock2(player, action, path);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("info")) {
            adminActions.playerInfoTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("setisland")) {
            adminActions.setPlayerIslandTarget(player, args[2], parseUuidOrNil(args[3]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("clearisland")) {
            adminActions.clearPlayerIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && (args.length == 1 || args[1].equalsIgnoreCase("list"))) {
            adminActions.listTemplates(player);
            return;
        }
        if (args.length >= 4 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            adminActions.upsertTemplate(player, args[2], args[3], args.length > 4 ? parseToggle(args, 4, true) : true, args.length > 5 ? args[5] : "");
            return;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("enable")) {
            adminActions.enableTemplate(player, args[2]);
            return;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("disable")) {
            adminActions.disableTemplate(player, args[2]);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("node") && (args.length == 1 || args[1].equalsIgnoreCase("menu") || args[1].equalsIgnoreCase("list"))) {
            adminActions.listNodes(player);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("info")) {
            adminActions.nodeInfo(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("islands")) {
            adminActions.nodeIslands(player, args[2], args.length > 3 ? (int) parseLongOrZero(args[3]) : 50);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("drain")) {
            adminActions.drainNode(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("undrain")) {
            adminActions.undrainNode(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("sweep")) {
            adminActions.sweepNode(player, args.length > 2 ? args[2] : "");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("kickall")) {
            adminActions.kickAllNode(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin-request");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("shutdown-safe")) {
            adminActions.shutdownSafeNode(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin-request");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("jobs") && (args.length == 1 || args[1].equalsIgnoreCase("list"))) {
            adminActions.listJobs(player);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("retry")) {
            adminActions.retryJob(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("cancel")) {
            adminActions.cancelJob(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            adminActions.recoverJobs(player, args.length > 2 ? args[2] : "recovery", args.length > 3 ? parseLongOrZero(args[3]) : 60000L, args.length > 4 ? (int) parseLongOrZero(args[4]) : 16);
            return;
        }
            sendCommandList(player, "CloudIslands 관리자 명령어 목록", adminCommands(), 1, "ciadmin command list");
    }


}
