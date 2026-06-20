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
}
