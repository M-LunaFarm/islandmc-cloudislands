package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView.RecentVisitorView;
import org.junit.jupiter.api.Test;

class IslandVisitorStatsMenuTest {
    @Test
    void recentVisitorsPreferNamesWithUuidFallback() {
        assertEquals("VisitPlayer", IslandVisitorStatsMenu.visitorDisplayName(new RecentVisitorView("33333333-3333-3333-3333-333333333333", "", " VisitPlayer ")));
        assertEquals("33333333", IslandVisitorStatsMenu.visitorDisplayName(new RecentVisitorView("33333333-3333-3333-3333-333333333333", "")));
    }
}
