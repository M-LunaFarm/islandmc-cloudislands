package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import org.junit.jupiter.api.Test;

class IslandBanMenuTest {
    @Test
    void banItemsPreferNamesAndFallBackToCompactUuids() {
        PaperGuiViews.BanView named = new PaperGuiViews.BanView(
            "aaaaaaaa-0000-0000-0000-000000000001",
            "bbbbbbbb-0000-0000-0000-000000000002",
            "spam",
            "",
            "",
            "Griefer",
            "Moderator"
        );
        PaperGuiViews.BanView legacy = new PaperGuiViews.BanView(
            "aaaaaaaa-0000-0000-0000-000000000001",
            "bbbbbbbb-0000-0000-0000-000000000002",
            "spam",
            "",
            ""
        );

        assertEquals("Griefer", IslandBanMenu.bannedDisplay(named));
        assertEquals("Moderator", IslandBanMenu.actorDisplay(named));
        assertEquals("aaaaaaaa", IslandBanMenu.bannedDisplay(legacy));
        assertEquals("bbbbbbbb", IslandBanMenu.actorDisplay(legacy));
    }
}
