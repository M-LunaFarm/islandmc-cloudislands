package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import org.junit.jupiter.api.Test;

class IslandInviteMenuTest {
    @Test
    void inviteItemsPreferNamesAndFallBackToCompactUuids() {
        PaperGuiViews.InviteView named = new PaperGuiViews.InviteView(
            "aaaaaaaa-0000-0000-0000-000000000001",
            "bbbbbbbb-0000-0000-0000-000000000002",
            "cccccccc-0000-0000-0000-000000000003",
            "",
            "",
            "Builders",
            "Alice"
        );
        PaperGuiViews.InviteView legacy = new PaperGuiViews.InviteView(
            "aaaaaaaa-0000-0000-0000-000000000001",
            "bbbbbbbb-0000-0000-0000-000000000002",
            "cccccccc-0000-0000-0000-000000000003",
            "",
            ""
        );

        assertEquals("Builders", IslandInviteMenu.islandDisplay(named));
        assertEquals("Alice", IslandInviteMenu.inviterDisplay(named));
        assertEquals("bbbbbbbb", IslandInviteMenu.islandDisplay(legacy));
        assertEquals("cccccccc", IslandInviteMenu.inviterDisplay(legacy));
    }
}
