package kr.lunaf.cloudislands.paper.command;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IslandCommandControllerPolicyTest {
    @Test
    void playerRouteMessagesUsePlayerRouteTicketView() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));

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

        assertFalse(backend.contains("implements CommandExecutor, TabCompleter"), "command execution backend must not own tab completion");
        assertFalse(backend.contains("onTabComplete("), "tab completion belongs in IslandCommandTabCompleter");
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
        assertTrue(backend.contains("bankCommands.handleGuiAction(player, actionId"));
        assertFalse(backend.contains("depositIslandBank("), "bank deposit logic belongs in IslandBankCommandHandler");
        assertFalse(backend.contains("withdrawIslandBank("), "bank withdraw logic belongs in IslandBankCommandHandler");
        assertTrue(bankHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(bankHandler.contains("boolean handleGuiAction(Player player, String actionId, Map<String, String> data)"));
        assertTrue(bankHandler.contains("coreApiClient.depositIslandBank"));
        assertTrue(bankHandler.contains("coreApiClient.withdrawIslandBank"));
    }

    @Test
    void snapshotCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String snapshotHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandSnapshotCommandHandler.java"));

        assertTrue(backend.contains("private final IslandSnapshotCommandHandler snapshotCommands;"));
        assertTrue(backend.contains("snapshotCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("snapshotCommands.handleGuiAction(player, actionId"));
        assertFalse(backend.contains("requestIslandSnapshot("), "snapshot create logic belongs in IslandSnapshotCommandHandler");
        assertFalse(backend.contains("restoreIslandSnapshot("), "snapshot restore logic belongs in IslandSnapshotCommandHandler");
        assertTrue(snapshotHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(snapshotHandler.contains("boolean handleGuiAction(Player player, String actionId, Map<String, String> data, GuiClick click)"));
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
        assertTrue(backend.contains("chatLogCommands.handleGuiAction(player, actionId"));
        assertFalse(backend.contains("sendIslandChat("), "chat send logic belongs in IslandChatLogCommandHandler");
        assertFalse(backend.contains("listIslandLogs("), "log list logic belongs in IslandChatLogCommandHandler");
        assertTrue(chatLogHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(chatLogHandler.contains("boolean handleGuiAction(Player player, String actionId, Map<String, String> data)"));
        assertTrue(chatLogHandler.contains("coreApiClient.sendIslandChat"));
        assertTrue(chatLogHandler.contains("coreApiClient.listIslandLogs"));
    }

    @Test
    void progressionCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String progressionHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandProgressionCommandHandler.java"));

        assertTrue(backend.contains("private final IslandProgressionCommandHandler progressionCommands;"));
        assertTrue(backend.contains("progressionCommands.handleCommand(player, subcommand, args)"));
        assertTrue(backend.contains("progressionCommands.handleGuiAction(player, actionId"));
        assertFalse(backend.contains("recalculateIslandLevel("), "level recalculation logic belongs in IslandProgressionCommandHandler");
        assertFalse(backend.contains("purchaseIslandUpgrade("), "upgrade purchase logic belongs in IslandProgressionCommandHandler");
        assertFalse(backend.contains("completeIslandTask("), "mission completion logic belongs in IslandProgressionCommandHandler");
        assertTrue(progressionHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(progressionHandler.contains("boolean handleGuiAction(Player player, String actionId, Map<String, String> data)"));
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
        assertTrue(backend.contains("environmentCommands.handleGuiAction(player, actionId"));
        assertFalse(backend.contains("setIslandBiome("), "biome mutation logic belongs in IslandEnvironmentCommandHandler");
        assertFalse(backend.contains("setIslandLimit("), "limit mutation logic belongs in IslandEnvironmentCommandHandler");
        assertFalse(backend.contains("applyIslandBorder("), "border UI logic belongs in IslandEnvironmentCommandHandler");
        assertTrue(environmentHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(environmentHandler.contains("boolean handleGuiAction(Player player, String actionId, Map<String, String> data)"));
        assertTrue(environmentHandler.contains("coreApiClient.setIslandBiomeResult"));
        assertTrue(environmentHandler.contains("coreApiClient.setIslandLimit"));
        assertTrue(environmentHandler.contains("coreApiClient.setIslandFlagResult"));
    }
}
