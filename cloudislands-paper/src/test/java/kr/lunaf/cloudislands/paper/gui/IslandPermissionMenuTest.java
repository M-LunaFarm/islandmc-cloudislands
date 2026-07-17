package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PermissionOverrideView;
import org.junit.jupiter.api.Test;

class IslandPermissionMenuTest {
    @Test
    void permissionOverrideUsesProfileNameWithUuidFallback() {
        assertEquals(
            "BuilderPlayer",
            IslandPermissionMenu.overrideDisplayName(new PermissionOverrideView("33333333-3333-3333-3333-333333333333", "BREAK", false, " BuilderPlayer "))
        );
        assertEquals(
            "33333333",
            IslandPermissionMenu.overrideDisplayName(new PermissionOverrideView("33333333-3333-3333-3333-333333333333", "BREAK", false, null))
        );
    }
}
