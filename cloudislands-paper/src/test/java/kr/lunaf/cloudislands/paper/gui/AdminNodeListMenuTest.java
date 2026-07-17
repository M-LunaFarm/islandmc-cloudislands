package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import org.junit.jupiter.api.Test;

class AdminNodeListMenuTest {
    @Test
    void nodeEntriesKeepTargetAndSanitizeOperationalText() {
        IslandNodeSnapshot node = new IslandNodeSnapshot(
            "island-2", "default\npool", "paper-2", "1.21.8", NodeState.READY,
            12, 40, 60, 2, 8, 20, 21.5, 1, 10, 0.2,
            512, 2048, 0, true, "default", Instant.parse("2026-07-17T00:00:00Z"), 12.4, Map.of()
        );

        assertEquals(Map.of("nodeId", "island-2"), AdminNodeListMenu.nodeActionData(node));
        assertTrue(AdminNodeListMenu.nodeTitle(node).contains("island-2 / READY"));
        assertTrue(AdminNodeListMenu.nodeLore(node, null).stream().noneMatch(line -> line.contains("\n")));
        assertTrue(AdminNodeListMenu.nodeLore(node, null).stream().anyMatch(line -> line.contains("12/40/60")));
    }
}
