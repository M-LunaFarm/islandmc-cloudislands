package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminAuditEntryView;
import org.junit.jupiter.api.Test;

class AdminAuditMenuTest {
    @Test
    void auditLoreSanitizesAndBoundsRedactedPayload() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("accepted", "true");
        payload.put("islandId", "island-a");
        payload.put("nodeId", "node-a");
        payload.put("reason", "maintenance\nwindow");
        payload.put("requestedBy", "operator-a");
        payload.put("result", "draining");
        AdminAuditEntryView entry = new AdminAuditEntryView(
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002",
            "ADMIN",
            "NODE_DRAIN",
            "NODE",
            "island-1",
            payload,
            "2026-07-17T12:00:00Z"
        );

        List<String> lore = AdminAuditMenu.auditLore(entry, null);

        assertTrue(lore.stream().noneMatch(line -> line.contains("\n")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("작업: NODE_DRAIN")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("reason = maintenance window")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("추가 payload: 1")));
    }
}
