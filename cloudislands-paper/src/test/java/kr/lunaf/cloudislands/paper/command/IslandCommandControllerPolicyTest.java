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
    void commandRoutingIsSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String router = routerSource();

        assertTrue(backend.contains("private final IslandCommandRouter router;"));
        assertTrue(backend.contains("return router.handleCommand(sender, command, label, args);"));
        assertTrue(backend.contains("router.handleGuiAction(player, action, click);"));
        assertFalse(backend.contains("commandListPage("), "command route parsing belongs in IslandCommandRouter");
        assertFalse(backend.contains("sendCommandList(Player player"), "command list rendering belongs in IslandCommandRouter");
        assertTrue(router.contains("final class IslandCommandRouter"));
        assertTrue(router.contains("boolean handleCommand(@NotNull CommandSender sender"));
        assertTrue(router.contains("void handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(router.contains("CommandListPolicy.page"));
    }

    @Test
    void bankCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String bankHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandBankCommandHandler.java"));
        String bankUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/BankUseCase.java"));

        assertTrue(backend.contains("private final IslandBankCommandHandler bankCommands;"));
        assertTrue(routerSource().contains("bankCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("bankCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("depositIslandBank("), "bank deposit logic belongs in IslandBankCommandHandler");
        assertFalse(backend.contains("withdrawIslandBank("), "bank withdraw logic belongs in IslandBankCommandHandler");
        assertTrue(bankHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(bankHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(bankHandler.contains("private final BankUseCase bankUseCase;"));
        assertTrue(bankHandler.contains("bankUseCase.deposit("));
        assertTrue(bankHandler.contains("bankUseCase.withdraw("));
        assertFalse(bankHandler.contains("coreApiClient.depositIslandBank"), "bank mutation logic belongs in BankUseCase");
        assertFalse(bankHandler.contains("coreApiClient.withdrawIslandBank"), "bank mutation logic belongs in BankUseCase");
        assertTrue(bankUseCase.contains("coreApiClient.depositIslandBank"));
        assertTrue(bankUseCase.contains("coreApiClient.withdrawIslandBank"));
    }

    @Test
    void snapshotCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String snapshotHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandSnapshotCommandHandler.java"));
        String snapshotUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/SnapshotUseCase.java"));

        assertTrue(backend.contains("private final IslandSnapshotCommandHandler snapshotCommands;"));
        assertTrue(routerSource().contains("snapshotCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("snapshotCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("requestIslandSnapshot("), "snapshot create logic belongs in SnapshotUseCase");
        assertFalse(backend.contains("restoreIslandSnapshot("), "snapshot restore logic belongs in SnapshotUseCase");
        assertTrue(snapshotHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(snapshotHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(snapshotHandler.contains("private final SnapshotUseCase snapshotUseCase;"));
        assertTrue(snapshotHandler.contains("snapshotUseCase.snapshotViews("));
        assertTrue(snapshotHandler.contains("snapshotUseCase.requestSnapshotAction("));
        assertTrue(snapshotHandler.contains("snapshotUseCase.restoreSnapshotAction("));
        assertFalse(snapshotUseCase.contains("public CompletableFuture<String> listSnapshots("), "snapshot list usecase must expose typed views instead of raw JSON");
        assertFalse(snapshotUseCase.contains("public CompletableFuture<String> requestSnapshot("), "snapshot create usecase must expose typed actions instead of raw JSON");
        assertFalse(snapshotUseCase.contains("public CompletableFuture<String> restoreSnapshot("), "snapshot restore usecase must expose typed actions instead of raw JSON");
        assertFalse(snapshotHandler.contains("coreApiClient.requestIslandSnapshotResult"), "snapshot mutation logic belongs in SnapshotUseCase");
        assertFalse(snapshotHandler.contains("coreApiClient.restoreIslandSnapshotResult"), "snapshot mutation logic belongs in SnapshotUseCase");
        assertTrue(snapshotUseCase.contains("coreApiClient.requestIslandSnapshotResult"));
        assertTrue(snapshotUseCase.contains("coreApiClient.restoreIslandSnapshotResult"));
    }

    @Test
    void warehouseCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String warehouseHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandWarehouseCommandHandler.java"));
        String warehouseUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandWarehouseUseCase.java"));

        assertTrue(backend.contains("private final IslandWarehouseCommandHandler warehouseCommands;"));
        assertTrue(routerSource().contains("warehouseCommands.handleCommand(player, subcommand, args)"));
        assertFalse(backend.contains("listIslandWarehouse("), "warehouse list logic belongs in IslandWarehouseCommandHandler");
        assertFalse(backend.contains("changeIslandWarehouse("), "warehouse mutation logic belongs in IslandWarehouseCommandHandler");
        assertTrue(warehouseHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(warehouseHandler.contains("IslandWarehouseUseCase"));
        assertTrue(warehouseHandler.contains("warehouseUseCase.listItems"));
        assertTrue(warehouseHandler.contains("warehouseUseCase.deposit"));
        assertFalse(warehouseHandler.contains("coreApiClient.islandWarehouse"));
        assertFalse(warehouseHandler.contains("coreApiClient.depositIslandWarehouse"));
        assertFalse(warehouseHandler.contains("coreApiClient.withdrawIslandWarehouse"));
        assertFalse(warehouseUseCase.contains("public CompletableFuture<String> list("), "warehouse list usecase must expose typed item views instead of raw JSON");
        assertTrue(warehouseUseCase.contains("warehouseQueries.listItems"));
        assertFalse(warehouseUseCase.contains("coreApiClient.islandWarehouse"));
        assertTrue(warehouseUseCase.contains("coreApiClient.depositIslandWarehouse"));
        assertTrue(warehouseUseCase.contains("coreApiClient.withdrawIslandWarehouse"));
    }

    @Test
    void chatAndLogCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String chatLogHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandChatLogCommandHandler.java"));
        String communicationUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandCommunicationUseCase.java"));

        assertTrue(backend.contains("private final IslandChatLogCommandHandler chatLogCommands;"));
        assertTrue(routerSource().contains("chatLogCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("chatLogCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("sendIslandChat("), "chat send logic belongs in IslandChatLogCommandHandler");
        assertFalse(backend.contains("listIslandLogs("), "log list logic belongs in IslandChatLogCommandHandler");
        assertTrue(chatLogHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(chatLogHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(chatLogHandler.contains("IslandCommunicationUseCase"));
        assertTrue(chatLogHandler.contains("communicationUseCase.sendChatAction"));
        assertTrue(chatLogHandler.contains("communicationUseCase.logViews"));
        assertFalse(chatLogHandler.contains("coreApiClient.sendIslandChat"));
        assertFalse(chatLogHandler.contains("coreApiClient.listIslandLogs"));
        assertFalse(communicationUseCase.contains("public CompletableFuture<String> sendChat("), "chat send usecase must expose typed actions instead of raw JSON");
        assertFalse(communicationUseCase.contains("public CompletableFuture<String> listLogs("), "log list usecase must expose typed log views instead of raw JSON");
        assertTrue(communicationUseCase.contains("coreApiClient.sendIslandChat"));
        assertTrue(communicationUseCase.contains("communicationQueries.listLogs"));
        assertFalse(communicationUseCase.contains("coreApiClient.listIslandLogs"));
    }

    @Test
    void progressionCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String progressionHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandProgressionCommandHandler.java"));
        String progressionUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandProgressionUseCase.java"));

        assertTrue(backend.contains("private final IslandProgressionCommandHandler progressionCommands;"));
        assertTrue(routerSource().contains("progressionCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("progressionCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("recalculateIslandLevel("), "level recalculation logic belongs in IslandProgressionCommandHandler");
        assertFalse(backend.contains("purchaseIslandUpgrade("), "upgrade purchase logic belongs in IslandProgressionCommandHandler");
        assertFalse(backend.contains("completeIslandTask("), "mission completion logic belongs in IslandProgressionCommandHandler");
        assertTrue(progressionHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(progressionHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(progressionHandler.contains("IslandProgressionUseCase"));
        assertTrue(progressionHandler.contains("progressionUseCase.islandLevel"));
        assertTrue(progressionHandler.contains("progressionUseCase.blockDetailsView"));
        assertTrue(progressionHandler.contains("progressionUseCase.topWorthViews"));
        assertTrue(progressionHandler.contains("progressionUseCase.recalculateLevelView"));
        assertTrue(progressionHandler.contains("progressionUseCase.upgradeViews"));
        assertTrue(progressionHandler.contains("progressionUseCase.purchaseUpgradeResult"));
        assertTrue(progressionHandler.contains("progressionUseCase.missionViews"));
        assertTrue(progressionHandler.contains("progressionUseCase.completeMissionResult"));
        assertFalse(progressionHandler.contains("coreApiClient.recalculateIslandLevel"));
        assertFalse(progressionHandler.contains("coreApiClient.purchaseIslandUpgrade"));
        assertFalse(progressionHandler.contains("coreApiClient.completeIslandMission"));
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> islandInfo("), "progression island info usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> blockDetails("), "block details usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> topIslandsByWorth("), "worth ranking usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> topIslandsByLevel("), "level ranking usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> topIslandsByReviews("), "review ranking usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> recalculateLevel("), "level recalculation usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> listUpgrades("), "upgrade list usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> purchaseUpgrade("), "upgrade mutation usecase must expose typed results instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> listMissions("), "mission list usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> completeMission("), "mission mutation usecase must expose typed results instead of raw JSON");
        assertTrue(progressionUseCase.contains("ProgressionQueryClient progressionQueries"), "progression reads must stay behind a typed core-client query boundary");
        assertTrue(progressionUseCase.contains("progressionQueries.blockDetails"));
        assertTrue(progressionUseCase.contains("progressionQueries.topWorth"));
        assertTrue(progressionUseCase.contains("progressionQueries.upgrades"));
        assertFalse(progressionUseCase.contains("coreApiClient.islandBlockDetails"));
        assertFalse(progressionUseCase.contains("PaperGuiViews.islandUpgrades(coreApiClient"));
        assertFalse(progressionUseCase.contains("PaperGuiViews.islandMissions(coreApiClient"));
        assertTrue(progressionUseCase.contains("coreApiClient.recalculateIslandLevel"));
        assertTrue(progressionUseCase.contains("coreApiClient.purchaseIslandUpgrade"));
        assertTrue(progressionUseCase.contains("coreApiClient.completeIslandMission"));
    }

    @Test
    void environmentCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String environmentHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java"));
        String environmentUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandEnvironmentUseCase.java"));

        assertTrue(backend.contains("private final IslandEnvironmentCommandHandler environmentCommands;"));
        assertTrue(routerSource().contains("environmentCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("environmentCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setIslandBiome("), "biome mutation logic belongs in IslandEnvironmentCommandHandler");
        assertFalse(backend.contains("setIslandLimit("), "limit mutation logic belongs in IslandEnvironmentCommandHandler");
        assertFalse(backend.contains("applyIslandBorder("), "border UI logic belongs in IslandEnvironmentCommandHandler");
        assertTrue(environmentHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(environmentHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(environmentHandler.contains("IslandEnvironmentUseCase"));
        assertTrue(environmentHandler.contains("environmentUseCase.setBiomeAction"));
        assertTrue(environmentHandler.contains("environmentUseCase.setLimitAction"));
        assertTrue(environmentHandler.contains("environmentUseCase.setFlagAction"));
        assertTrue(environmentHandler.contains("environmentUseCase.islandInfoView"));
        assertTrue(environmentHandler.contains("environmentUseCase.limitViews"));
        assertFalse(environmentHandler.contains("coreApiClient.setIslandBiomeResult"));
        assertFalse(environmentHandler.contains("coreApiClient.setIslandLimit"));
        assertFalse(environmentHandler.contains("coreApiClient.setIslandFlagResult"));
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> islandBiome("), "biome read usecase must expose typed values instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> islandInfo("), "island info usecase must expose typed views instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> listFlags("), "flag list usecase must expose typed values instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> listLimits("), "limit list usecase must expose typed views instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> setBiome("), "biome mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> setLimit("), "limit mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> setFlag("), "flag mutation usecase must expose typed actions instead of raw JSON");
        assertTrue(environmentUseCase.contains("coreApiClient.setIslandBiomeResult"));
        assertTrue(environmentUseCase.contains("coreApiClient.setIslandLimit"));
        assertTrue(environmentUseCase.contains("coreApiClient.setIslandFlagResult"));
    }

    @Test
    void settingsCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String settingsHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandSettingsCommandHandler.java"));
        String settingsUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandSettingsUseCase.java"));

        assertTrue(backend.contains("private final IslandSettingsCommandHandler settingsCommands;"));
        assertTrue(routerSource().contains("settingsCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("settingsCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setIslandPublicAccess("), "public access logic belongs in IslandSettingsCommandHandler");
        assertFalse(backend.contains("setIslandFlag("), "flag mutation logic belongs in IslandSettingsCommandHandler");
        assertFalse(backend.contains("setIslandName("), "name mutation logic belongs in IslandSettingsCommandHandler");
        assertTrue(settingsHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(settingsHandler.contains("boolean handleGuiAction(Player player, GuiAction action, boolean rightClick)"));
        assertTrue(settingsHandler.contains("IslandSettingsUseCase"));
        assertTrue(settingsHandler.contains("settingsUseCase.setPublicAccessAction"));
        assertTrue(settingsHandler.contains("settingsUseCase.setFlagAction"));
        assertTrue(settingsHandler.contains("settingsUseCase.setNameAction"));
        assertFalse(settingsHandler.contains("coreApiClient.setIslandPublicAccessResult"));
        assertFalse(settingsHandler.contains("coreApiClient.setIslandFlagResult"));
        assertFalse(settingsHandler.contains("coreApiClient.setIslandNameResult"));
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> setPublicAccess("), "public access usecase must expose typed actions instead of raw JSON");
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> setLocked("), "locked usecase must expose typed actions instead of raw JSON");
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> setName("), "name usecase must expose typed actions instead of raw JSON");
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> setFlag("), "flag usecase must expose typed actions instead of raw JSON");
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> listFlags("), "flag list usecase must expose typed values instead of raw JSON");
        assertTrue(settingsUseCase.contains("coreApiClient.setIslandPublicAccessResult"));
        assertTrue(settingsUseCase.contains("coreApiClient.setIslandFlagResult"));
        assertTrue(settingsUseCase.contains("coreApiClient.setIslandNameResult"));
    }

    @Test
    void homeWarpCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String homeWarpHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandHomeWarpCommandHandler.java"));
        String homeWarpUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandHomeWarpUseCase.java"));

        assertTrue(backend.contains("private final IslandHomeWarpCommandHandler homeWarpCommands;"));
        assertTrue(routerSource().contains("homeWarpCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("homeWarpCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setHome("), "home mutation logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("setWarp("), "warp mutation logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("teleportHome("), "home teleport logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("teleportWarp("), "warp teleport logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("listPublicWarps("), "public warp listing belongs in IslandHomeWarpCommandHandler");
        assertTrue(homeWarpHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(homeWarpHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(homeWarpHandler.contains("IslandHomeWarpUseCase"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.setHomeAction"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.setWarpAction"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.deleteWarpAction"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.homeViews"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.warpViews"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.publicWarpViews"));
        assertFalse(homeWarpHandler.contains("coreApiClient.setIslandHomeResult"));
        assertFalse(homeWarpHandler.contains("coreApiClient.setIslandWarpResult"));
        assertFalse(homeWarpHandler.contains("coreApiClient.deleteIslandWarpResult"));
        assertFalse(homeWarpHandler.contains("coreApiClient.listPublicWarps"));
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> setHome("), "home mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> setWarp("), "warp mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> listHomes("), "home list usecase must expose typed views instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> listWarps("), "warp list usecase must expose typed views instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> islandInfo("), "island info usecase must expose typed views instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> deleteWarp("), "warp delete usecase must expose typed actions instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> setWarpPublicAccess("), "warp access usecase must expose typed actions instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> listPublicWarps("), "public warp list usecase must expose typed views instead of raw JSON");
        assertTrue(homeWarpUseCase.contains("coreApiClient.setIslandHomeResult"));
        assertTrue(homeWarpUseCase.contains("coreApiClient.setIslandWarpResult"));
        assertTrue(homeWarpUseCase.contains("coreApiClient.deleteIslandWarpResult"));
        assertTrue(homeWarpUseCase.contains("homeWarpQueries.publicWarps"));
        assertFalse(homeWarpUseCase.contains("PaperGuiViews.publicWarps(coreApiClient"));
    }

    @Test
    void visitReviewCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String visitReviewHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandVisitReviewCommandHandler.java"));
        String navigationUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandNavigationUseCase.java"));

        assertTrue(backend.contains("private final IslandVisitReviewCommandHandler visitReviewCommands;"));
        assertTrue(routerSource().contains("visitReviewCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("visitReviewCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("routeVisitTarget("), "visit target resolution belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("routeRandomVisit("), "random visit routing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("listPublicIslands("), "public island listing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("listIslandReviews("), "review listing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("rateIslandReview("), "review mutation logic belongs in IslandVisitReviewCommandHandler");
        assertTrue(visitReviewHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(visitReviewHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(visitReviewHandler.contains("IslandNavigationUseCase"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.resolveVisitTarget"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.publicIslandViews"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.reviewViews"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.setReviewAction"));
        assertFalse(visitReviewHandler.contains("coreApiClient.createVisitTicket"));
        assertFalse(visitReviewHandler.contains("coreApiClient.createRandomVisitTicket"));
        assertFalse(visitReviewHandler.contains("coreApiClient.listPublicIslands"));
        assertFalse(visitReviewHandler.contains("coreApiClient.setIslandReview"));
        assertTrue(navigationUseCase.contains("coreApiClient.createVisitTicket"));
        assertTrue(navigationUseCase.contains("coreApiClient.createRandomVisitTicket"));
        assertTrue(navigationUseCase.contains("NavigationQueryClient navigationQueries"), "navigation reads must stay behind a typed core-client query boundary");
        assertTrue(navigationUseCase.contains("navigationQueries.publicIslands"), "public island list usecase must read through the typed navigation query client");
        assertFalse(navigationUseCase.contains("PaperGuiViews.publicIslands(coreApiClient"), "public island list usecase must not parse raw Core bodies in Paper");
        assertFalse(navigationUseCase.contains("public CompletableFuture<String> listPublicIslands("), "public island list usecase must expose typed views instead of raw JSON");
        assertFalse(navigationUseCase.contains("public CompletableFuture<String> listReviews("), "review list usecase must expose typed views instead of raw JSON");
        assertFalse(navigationUseCase.contains("public CompletableFuture<String> setReview("), "review mutation usecase must expose typed actions instead of raw JSON");
        assertTrue(navigationUseCase.contains("coreApiClient.setIslandReview"));
    }

    @Test
    void lifecycleCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String lifecycleHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java"));
        String creationUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandCreationUseCase.java"));

        assertTrue(backend.contains("private final IslandLifecycleCommandHandler lifecycleCommands;"));
        assertTrue(routerSource().contains("lifecycleCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("lifecycleCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("createIsland("), "island creation logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("deleteIsland("), "island deletion logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("resetIsland("), "island reset logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("dangerConfirmed("), "danger confirmation logic belongs in IslandLifecycleCommandHandler");
        assertTrue(lifecycleHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(lifecycleHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(lifecycleHandler.contains("IslandCreationUseCase"));
        assertTrue(lifecycleHandler.contains("creationUseCase.create("));
        assertTrue(lifecycleHandler.contains("creationUseCase.resetAction("));
        assertFalse(lifecycleHandler.contains("coreApiClient.createIsland"));
        assertFalse(lifecycleHandler.contains("coreApiClient.deleteIsland"));
        assertFalse(lifecycleHandler.contains("coreApiClient.resetIslandResult"));
        assertFalse(creationUseCase.contains("public CompletableFuture<String> reset("), "reset usecase must expose typed actions instead of raw JSON");
        assertTrue(creationUseCase.contains("coreApiClient.createIsland"));
        assertTrue(creationUseCase.contains("coreApiClient.deleteIsland"));
        assertTrue(creationUseCase.contains("coreApiClient.resetIslandResult"));
        assertTrue(lifecycleHandler.contains("DangerousGuiActionPolicy.confirmed"));
    }

    @Test
    void overviewCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String overviewHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandOverviewCommandHandler.java"));

        assertTrue(backend.contains("private final IslandOverviewCommandHandler overviewCommands;"));
        assertTrue(routerSource().contains("overviewCommands.handleCommand(player, subcommand)"));
        assertTrue(routerSource().contains("overviewCommands.handleGuiAction(player, action"));
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
        String memberUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/MemberManagementUseCase.java"));
        String permissionHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandPermissionCommandHandler.java"));
        String permissionUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/PermissionManagementUseCase.java"));

        assertTrue(backend.contains("private final IslandMembershipCommandHandler membershipCommands;"));
        assertTrue(routerSource().contains("membershipCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("membershipCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("subcommand.equals(\"members\")"), "membership command routing belongs in IslandMembershipCommandHandler");
        assertFalse(backend.contains("case \"island.members.open\""), "membership GUI routing belongs in IslandMembershipCommandHandler");
        assertFalse(backend.contains("case \"island.permissions.open\""), "permission GUI routing belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(membershipHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(membershipHandler.contains("subcommand.equals(\"members\")"));
        assertTrue(membershipHandler.contains("case PERMISSIONS_OPEN"));
        assertFalse(memberUseCase.contains("public CompletableFuture<String> removeMember("), "member removal usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> playerInfoByName("), "player lookup usecase must expose typed UUID lookup instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> islandInfoByName("), "island lookup usecase must expose typed invite resolution instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> createInvite("), "invite create usecase must expose typed views instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> acceptInvite("), "invite accept usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> declineInvite("), "invite decline usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> setRole("), "member role usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> trustTemporarily("), "temporary trust usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> transferOwnership("), "ownership transfer usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> banVisitor("), "visitor ban usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> pardonVisitor("), "visitor pardon usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> kickVisitor("), "visitor kick usecase must expose typed actions instead of raw JSON");
        assertTrue(permissionHandler.contains("permissionUseCase.listPermissionViews"));
        assertTrue(permissionHandler.contains("permissionUseCase.listRoleViews"));
        assertTrue(permissionHandler.contains("permissionUseCase.saveSequentiallyTyped"));
        assertTrue(permissionHandler.contains("permissionUseCase.upsertRoleTyped"));
        assertTrue(permissionHandler.contains("permissionUseCase.resetRoleTyped"));
        assertTrue(permissionHandler.contains("permissionUseCase.setPermissionAction"));
        assertTrue(permissionHandler.contains("permissionUseCase.setPermissionOverrideAction"));
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> listPermissions("), "permission list usecase must expose typed views instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> listRoles("), "role list usecase must expose typed views instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> upsertRole("), "role upsert usecase must expose typed results instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> resetRole("), "role reset usecase must expose typed results instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> setPermission("), "permission mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> setPermissionOverride("), "permission override usecase must expose typed actions instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> saveSequentially("), "permission save usecase must expose typed mutation results instead of raw strings");
    }

    @Test
    void adminNodeCommandsRouteOutsideCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String adminHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandAdminNodeCommandHandler.java"));
        String adminUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandAdminNodeUseCase.java"));

        assertTrue(backend.contains("private final IslandAdminNodeCommandHandler adminCommands;"));
        assertTrue(routerSource().contains("adminCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("case \"admin.node.list\""), "admin node GUI routing belongs in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("openAdminNodeMenu("), "admin menu opening belongs in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("drainAdminNode("), "admin node mutations belong in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("shutdownAdminNodeSafely("), "admin danger mutations belong in IslandAdminNodeCommandHandler");
        assertTrue(adminHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(adminHandler.contains("action instanceof GuiAction.AdminNodeAction"));
        assertTrue(adminHandler.contains("case LIST ->"));
        assertTrue(adminHandler.contains("IslandAdminNodeUseCase"));
        assertTrue(adminHandler.contains("adminNodeUseCase.drainAction"));
        assertTrue(adminHandler.contains("adminNodeUseCase.shutdownSafelyAction"));
        assertFalse(adminHandler.contains("coreApiClient.drainNode"));
        assertFalse(adminHandler.contains("coreApiClient.shutdownNodeSafely"));
        assertFalse(adminUseCase.contains("public CompletableFuture<String> drain("), "admin drain usecase must expose typed actions instead of raw JSON");
        assertFalse(adminUseCase.contains("public CompletableFuture<String> undrain("), "admin undrain usecase must expose typed actions instead of raw JSON");
        assertFalse(adminUseCase.contains("public CompletableFuture<String> sweep("), "admin sweep usecase must expose typed actions instead of raw JSON");
        assertFalse(adminUseCase.contains("public CompletableFuture<String> kickAll("), "admin kick-all usecase must expose typed actions instead of raw JSON");
        assertFalse(adminUseCase.contains("public CompletableFuture<String> shutdownSafely("), "admin shutdown usecase must expose typed actions instead of raw JSON");
        assertTrue(adminUseCase.contains("coreApiClient.drainNode"));
        assertTrue(adminUseCase.contains("coreApiClient.shutdownNodeSafely"));
    }

    @Test
    void routingCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String routingHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandRoutingCommandHandler.java"));
        String routingUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandRoutingUseCase.java"));

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
        assertTrue(routingHandler.contains("IslandRoutingUseCase"));
        assertTrue(routingHandler.contains("routingUseCase.createWarpTicket"));
        assertTrue(routingHandler.contains("routingUseCase.clearRouteAction"));
        assertFalse(routingHandler.contains("coreApiClient.createWarpTicket"));
        assertFalse(routingHandler.contains("coreApiClient.routeTicketStatus"));
        assertFalse(routingHandler.contains("coreApiClient.publishRouteSession"));
        assertFalse(routingHandler.contains("coreApiClient.clearRoute"));
        assertFalse(routingUseCase.contains("public CompletableFuture<String> clearRoute("), "route clear usecase must expose typed actions instead of raw strings");
        assertTrue(routingUseCase.contains("coreApiClient.createWarpTicket"));
        assertTrue(routingUseCase.contains("coreApiClient.routeTicketStatus"));
        assertTrue(routingUseCase.contains("coreApiClient.publishRouteSession"));
        assertTrue(routingUseCase.contains("coreApiClient.clearRoute"));
    }

    private static String routerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouter.java"));
    }
}
