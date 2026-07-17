package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PublicIslandView;
import org.junit.jupiter.api.Test;

class IslandVisitMenuTest {
    @Test
    void ownerUsesProfileNameWithUuidFallback() {
        assertEquals(
            "IslandOwner",
            IslandVisitMenu.ownerDisplayName(island(" IslandOwner "))
        );
        assertEquals("99999999", IslandVisitMenu.ownerDisplayName(island("")));
    }

    private static PublicIslandView island(String ownerName) {
        return new PublicIslandView(
            "33333333-3333-3333-3333-333333333333",
            "99999999-9999-9999-9999-999999999999",
            "Island", "", 7L, "12", ownerName
        );
    }
}
