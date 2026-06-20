package kr.lunaf.cloudislands.paper.command;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IslandCommandControllerPolicyTest {
    @Test
    void playerRouteMessagesUsePlayerRouteTicketView() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandRoutingCommandHandler.java"));

        assertTrue(source.contains("import kr.lunaf.cloudislands.common.feature.PlayerRouteTicketView;"));
        assertTrue(source.contains("PlayerRouteTicketView.from(ticket).destination()"));
        assertTrue(source.contains("case \"my-island\" -> \"내 섬\";"));
        assertTrue(source.contains("case \"island-visit\" -> \"방문할 섬\";"));
        assertTrue(source.contains("case \"island-warps\" -> \"섬 워프\";"));
    }

    @Test
    void tabCompletionIsSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String controller = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandController.java"));
        String completer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandCatalog.java"));

        assertFalse(backend.contains("implements CommandExecutor, TabCompleter"), "command execution backend must not own tab completion");
        assertFalse(backend.contains("onTabComplete("), "tab completion belongs in IslandCommandTabCompleter");
        assertTrue(backend.contains("static final List<String> SUBCOMMANDS = IslandCommandCatalog.SUBCOMMANDS;"), "command keyword catalog must live outside the backend");
        assertTrue(backend.contains("static final List<String> HELP_COMMANDS = IslandCommandCatalog.HELP_COMMANDS;"), "help command catalog must live outside the backend");
        assertTrue(catalog.contains("final class IslandCommandCatalog"), "command catalog must be isolated in its own class");
        assertTrue(controller.contains("private final IslandCommandTabCompleter tabCompleter;"));
        assertTrue(controller.contains("return tabCompleter.onTabComplete(sender, command, alias, args);"));
        assertTrue(completer.contains("implements TabCompleter"));
        assertTrue(completer.contains("IslandCommandBackend.SUBCOMMANDS"));
        assertTrue(completer.contains("IslandCommandBackend.HELP_COMMANDS.size()"));
    }

    @Test
    void bankCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String bankHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandBankCommandHandler.java"));

        assertTrue(backend.contains("private final IslandBankCommandHandler bankCommands;"));
        assertTrue(backend.contains("bankCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("bankCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("depositIslandBank("), "bank deposit logic belongs in IslandBankCommandHandler");
        assertFalse(backend.contains("withdrawIslandBank("), "bank withdraw logic belongs in IslandBankCommandHandler");
        assertTrue(bankHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(bankHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(bankHandler.contains("coreApiClient.depositIslandBank"));
        assertTrue(bankHandler.contains("coreApiClient.withdrawIslandBank"));
    }

    @Test
    void snapshotCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String snapshotHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandSnapshotCommandHandler.java"));

        assertTrue(backend.contains("private final IslandSnapshotCommandHandler snapshotCommands;"));
        assertTrue(backend.contains("snapshotCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("snapshotCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("requestIslandSnapshot("), "snapshot create logic belongs in IslandSnapshotCommandHandler");
        assertFalse(backend.contains("restoreIslandSnapshot("), "snapshot restore logic belongs in IslandSnapshotCommandHandler");
        assertTrue(snapshotHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(snapshotHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(snapshotHandler.contains("coreApiClient.requestIslandSnapshotResult"));
        assertTrue(snapshotHandler.contains("coreApiClient.restoreIslandSnapshotResult"));
    }

    @Test
    void warehouseCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String warehouseHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandWarehouseCommandHandler.java"));

        assertTrue(backend.contains("private final IslandWarehouseCommandHandler warehouseCommands;"));
        assertTrue(backend.contains("warehouseCommands.handleCommand(player, subcommand, args)"));
        assertFalse(backend.contains("listIslandWarehouse("), "warehouse list logic belongs in IslandWarehouseCommandHandler");
        assertFalse(backend.contains("changeIslandWarehouse("), "warehouse mutation logic belongs in IslandWarehouseCommandHandler");
        assertTrue(warehouseHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(warehouseHandler.contains("coreApiClient.islandWarehouse"));
        assertTrue(warehouseHandler.contains("coreApiClient.depositIslandWarehouse"));
        assertTrue(warehouseHandler.contains("coreApiClient.withdrawIslandWarehouse"));
    }

    @Test
    void chatAndLogCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String chatLogHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandChatLogCommandHandler.java"));

        assertTrue(backend.contains("private final IslandChatLogCommandHandler chatLogCommands;"));
        assertTrue(backend.contains("chatLogCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("chatLogCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("sendIslandChat("), "chat send logic belongs in IslandChatLogCommandHandler");
        assertFalse(backend.contains("listIslandLogs("), "log list logic belongs in IslandChatLogCommandHandler");
        assertTrue(chatLogHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(chatLogHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(chatLogHandler.contains("coreApiClient.sendIslandChat"));
        assertTrue(chatLogHandler.contains("coreApiClient.listIslandLogs"));
    }

    @Test
    void progressionCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String progressionHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandProgressionCommandHandler.java"));

        assertTrue(backend.contains("private final IslandProgressionCommandHandler progressionCommands;"));
        assertTrue(backend.contains("progressionCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("progressionCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("recalculateIslandLevel("), "level recalculation logic belongs in IslandProgressionCommandHandler");
        assertFalse(backend.contains("purchaseIslandUpgrade("), "upgrade purchase logic belongs in IslandProgressionCommandHandler");
        assertFalse(backend.contains("completeIslandTask("), "mission completion logic belongs in IslandProgressionCommandHandler");
        assertTrue(progressionHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(progressionHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(progressionHandler.contains("coreApiClient.recalculateIslandLevel"));
        assertTrue(progressionHandler.contains("coreApiClient.purchaseIslandUpgrade"));
        assertTrue(progressionHandler.contains("coreApiClient.completeIslandMission"));
    }

    @Test
    void environmentCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String environmentHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java"));

        assertTrue(backend.contains("private final IslandEnvironmentCommandHandler environmentCommands;"));
        assertTrue(backend.contains("environmentCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("environmentCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setIslandBiome("), "biome mutation logic belongs in IslandEnvironmentCommandHandler");
        assertFalse(backend.contains("setIslandLimit("), "limit mutation logic belongs in IslandEnvironmentCommandHandler");
        assertFalse(backend.contains("applyIslandBorder("), "border UI logic belongs in IslandEnvironmentCommandHandler");
        assertTrue(environmentHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(environmentHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(environmentHandler.contains("coreApiClient.setIslandBiomeResult"));
        assertTrue(environmentHandler.contains("coreApiClient.setIslandLimit"));
        assertTrue(environmentHandler.contains("coreApiClient.setIslandFlagResult"));
    }

    @Test
    void settingsCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String settingsHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandSettingsCommandHandler.java"));

        assertTrue(backend.contains("private final IslandSettingsCommandHandler settingsCommands;"));
        assertTrue(backend.contains("settingsCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("settingsCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setIslandPublicAccess("), "public access logic belongs in IslandSettingsCommandHandler");
        assertFalse(backend.contains("setIslandFlag("), "flag mutation logic belongs in IslandSettingsCommandHandler");
        assertFalse(backend.contains("setIslandName("), "name mutation logic belongs in IslandSettingsCommandHandler");
        assertTrue(settingsHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(settingsHandler.contains("boolean handleGuiAction(Player player, GuiAction action, boolean rightClick)"));
        assertTrue(settingsHandler.contains("coreApiClient.setIslandPublicAccessResult"));
        assertTrue(settingsHandler.contains("coreApiClient.setIslandFlagResult"));
        assertTrue(settingsHandler.contains("coreApiClient.setIslandNameResult"));
    }

    @Test
    void homeWarpCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String homeWarpHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandHomeWarpCommandHandler.java"));

        assertTrue(backend.contains("private final IslandHomeWarpCommandHandler homeWarpCommands;"));
        assertTrue(backend.contains("homeWarpCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("homeWarpCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setHome("), "home mutation logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("setWarp("), "warp mutation logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("teleportHome("), "home teleport logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("teleportWarp("), "warp teleport logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("listPublicWarps("), "public warp listing belongs in IslandHomeWarpCommandHandler");
        assertTrue(homeWarpHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(homeWarpHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(homeWarpHandler.contains("coreApiClient.setIslandHomeResult"));
        assertTrue(homeWarpHandler.contains("coreApiClient.setIslandWarpResult"));
        assertTrue(homeWarpHandler.contains("coreApiClient.deleteIslandWarpResult"));
        assertTrue(homeWarpHandler.contains("coreApiClient.listPublicWarps"));
    }

    @Test
    void visitReviewCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String visitReviewHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandVisitReviewCommandHandler.java"));

        assertTrue(backend.contains("private final IslandVisitReviewCommandHandler visitReviewCommands;"));
        assertTrue(backend.contains("visitReviewCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("visitReviewCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("routeVisitTarget("), "visit target resolution belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("routeRandomVisit("), "random visit routing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("listPublicIslands("), "public island listing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("listIslandReviews("), "review listing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("rateIslandReview("), "review mutation logic belongs in IslandVisitReviewCommandHandler");
        assertTrue(visitReviewHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(visitReviewHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(visitReviewHandler.contains("coreApiClient.createVisitTicket"));
        assertTrue(visitReviewHandler.contains("coreApiClient.createRandomVisitTicket"));
        assertTrue(visitReviewHandler.contains("coreApiClient.listPublicIslands"));
        assertTrue(visitReviewHandler.contains("coreApiClient.setIslandReview"));
    }

    @Test
    void lifecycleCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String lifecycleHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java"));

        assertTrue(backend.contains("private final IslandLifecycleCommandHandler lifecycleCommands;"));
        assertTrue(backend.contains("lifecycleCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("lifecycleCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("createIsland("), "island creation logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("deleteIsland("), "island deletion logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("resetIsland("), "island reset logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("dangerConfirmed("), "danger confirmation logic belongs in IslandLifecycleCommandHandler");
        assertTrue(lifecycleHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(lifecycleHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(lifecycleHandler.contains("coreApiClient.createIsland"));
        assertTrue(lifecycleHandler.contains("coreApiClient.deleteIsland"));
        assertTrue(lifecycleHandler.contains("coreApiClient.resetIslandResult"));
        assertTrue(lifecycleHandler.contains("DangerousGuiActionPolicy.confirmed"));
    }

    @Test
    void overviewCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String overviewHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandOverviewCommandHandler.java"));

        assertTrue(backend.contains("private final IslandOverviewCommandHandler overviewCommands;"));
        assertTrue(backend.contains("overviewCommands.handleCommand(player, subcommand)"));
        assertTrue(backend.contains("overviewCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("openIslandInfoMenu("), "info menu routing belongs in IslandOverviewCommandHandler");
        assertFalse(backend.contains("IslandMyIslandsMenu.open("), "my islands menu routing belongs in IslandOverviewCommandHandler");
        assertTrue(overviewHandler.contains("boolean handleCommand(Player player, String subcommand)"));
        assertTrue(overviewHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(overviewHandler.contains("IslandInfoMenu.open"));
        assertTrue(overviewHandler.contains("IslandMyIslandsMenu.open"));
    }

    @Test
    void membershipCommandsRouteOutsideCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String membershipHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));

        assertTrue(backend.contains("private final IslandMembershipCommandHandler membershipCommands;"));
        assertTrue(backend.contains("membershipCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("membershipCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("subcommand.equals(\"members\")"), "membership command routing belongs in IslandMembershipCommandHandler");
        assertFalse(backend.contains("case \"island.members.open\""), "membership GUI routing belongs in IslandMembershipCommandHandler");
        assertFalse(backend.contains("case \"island.permissions.open\""), "permission GUI routing belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(membershipHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(membershipHandler.contains("subcommand.equals(\"members\")"));
        assertTrue(membershipHandler.contains("case \"island.permissions.open\""));
    }

    @Test
    void adminNodeCommandsRouteOutsideCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String adminHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandAdminNodeCommandHandler.java"));

        assertTrue(backend.contains("private final IslandAdminNodeCommandHandler adminCommands;"));
        assertTrue(backend.contains("adminCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("case \"admin.node.list\""), "admin node GUI routing belongs in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("openAdminNodeMenu("), "admin menu opening belongs in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("drainAdminNode("), "admin node mutations belong in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("shutdownAdminNodeSafely("), "admin danger mutations belong in IslandAdminNodeCommandHandler");
        assertTrue(adminHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(adminHandler.contains("case \"admin.node.list\""));
        assertTrue(adminHandler.contains("coreApiClient.drainNode(nodeId)"));
        assertTrue(adminHandler.contains("coreApiClient.shutdownNodeSafely("));
    }

    @Test
    void routingCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String routingHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandRoutingCommandHandler.java"));

        assertTrue(backend.contains("private final IslandRoutingCommandHandler routingCommands;"));
        assertTrue(backend.contains("routingCommands.routeWarp(player, islandId, warpName)"));
        assertTrue(backend.contains("routingCommands.routeTicket(player, ticketFuture, failureMessage)"));
        assertTrue(backend.contains("routingCommands.clearRouteLoading(event.getPlayer())"));
        assertFalse(backend.contains("routeBossBars"), "route loading state belongs in IslandRoutingCommandHandler");
        assertFalse(backend.contains("sendPluginMessage(plugin, \"BungeeCord\""), "Bungee plugin messaging belongs in IslandRoutingCommandHandler");
        assertFalse(backend.contains("RoutePreparationProgressPolicy"), "route preparation polling belongs in IslandRoutingCommandHandler");
        assertTrue(routingHandler.contains("void routeWarp(Player player, UUID islandId, String warpName)"));
        assertTrue(routingHandler.contains("void routeTicket(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage)"));
        assertTrue(routingHandler.contains("routeBossBars"));
        assertTrue(routingHandler.contains("sendPluginMessage(plugin, \"BungeeCord\""));
        assertTrue(routingHandler.contains("RoutePreparationProgressPolicy"));
    }
}
