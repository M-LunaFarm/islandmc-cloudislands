package kr.lunaf.cloudislands.paper.command;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
