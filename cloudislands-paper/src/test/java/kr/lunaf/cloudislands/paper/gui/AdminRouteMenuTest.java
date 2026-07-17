package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.AdminRouteDebugView;
import kr.lunaf.cloudislands.coreclient.AdminRouteSessionView;
import kr.lunaf.cloudislands.coreclient.AdminRouteTicketView;
import org.junit.jupiter.api.Test;

class AdminRouteMenuTest {
    @Test
    void routeEntriesMergeDuplicateSessionsAndKeepActionIdentifiers() {
        UUID ticketId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID secondTicketId = UUID.randomUUID();
        UUID secondPlayerUuid = UUID.randomUUID();
        AdminRouteTicketView ticket = new AdminRouteTicketView(
            ticketId.toString(), playerUuid.toString(), UUID.randomUUID().toString(), "VISIT", "ISSUED",
            "island-2", "ci_island_2", "paper-2", "ISLAND", "", "", "2026-07-17T00:01:00Z", "secret-nonce"
        );
        AdminRouteDebugView debug = new AdminRouteDebugView(
            List.of(
                new AdminRouteSessionView(playerUuid.toString(), ticketId.toString(), "island-2", "paper-2", "secret-nonce", "2026-07-17T00:01:00Z"),
                new AdminRouteSessionView(secondPlayerUuid.toString(), secondTicketId.toString(), "island-3", "paper-3", "second-secret", "2026-07-17T00:02:00Z")
            ),
            List.of(ticket)
        );

        List<AdminRouteMenu.RouteEntry> entries = AdminRouteMenu.routeEntries(debug);

        assertEquals(2, entries.size());
        assertEquals("VISIT", entries.get(0).action());
        assertEquals(ticketId.toString(), AdminRouteMenu.routeActionData(entries.get(0), 1).get("ticketId"));
        assertEquals(playerUuid.toString(), AdminRouteMenu.routeActionData(entries.get(0), 1).get("playerUuid"));
        assertTrue(AdminRouteMenu.routeLore(entries.get(0), null).stream().noneMatch(line -> line.contains("\n")));
        assertFalse(AdminRouteMenu.routeLore(entries.get(0), null).stream().anyMatch(line -> line.contains("secret-nonce")));
    }
}
