package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.IslandInfoView;
import org.junit.jupiter.api.Test;

class IslandInfoMenuTest {
    @Test
    void ownerUsesProfileNameWithUuidFallback() {
        assertEquals(
            "IslandOwner",
            IslandInfoMenu.ownerDisplayName(info(" IslandOwner "), null)
        );
        assertEquals("99999999", IslandInfoMenu.ownerDisplayName(info(""), null));
    }

    private static IslandInfoView info(String ownerName) {
        return new IslandInfoView(
            "Island", "ACTIVE", "33333333-3333-3333-3333-333333333333", 7L, "12", true, false,
            100L, 100L, "99999999-9999-9999-9999-999999999999", "", ownerName
        );
    }
}
