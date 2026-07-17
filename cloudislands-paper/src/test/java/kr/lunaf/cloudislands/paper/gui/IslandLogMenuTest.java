package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.LogEntryView;
import org.junit.jupiter.api.Test;

class IslandLogMenuTest {
    @Test
    void logActorUsesProfileNameWithCompactUuidFallback() {
        assertEquals("IslandOwner", IslandLogMenu.actorDisplay(log(" IslandOwner ")));
        assertEquals("11111111...", IslandLogMenu.actorDisplay(log("")));
    }

    private static LogEntryView log(String actorName) {
        return new LogEntryView(
            "11111111-1111-1111-1111-111111111111",
            "ISLAND_RENAME",
            Map.of(),
            "now",
            actorName
        );
    }
}
