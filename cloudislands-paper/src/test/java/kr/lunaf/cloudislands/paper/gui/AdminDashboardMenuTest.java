package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminDashboardMenuTest {
    @Test
    void everyOperationalButtonHasItsNarrowPermission() {
        Map<String, String> expected = Map.ofEntries(
            Map.entry("admin.node.list", "cloudislands.admin.node"),
            Map.entry("admin.jobs.open", "cloudislands.admin.jobs"),
            Map.entry("admin.route.open", "cloudislands.admin.route"),
            Map.entry("admin.storage.open", "cloudislands.admin.storage"),
            Map.entry("admin.events.open", "cloudislands.admin.events"),
            Map.entry("admin.audit.open", "cloudislands.admin.audit"),
            Map.entry("admin.metrics.open", "cloudislands.admin.metrics"),
            Map.entry("admin.reviews.open", "cloudislands.admin.island"),
            Map.entry("admin.templates.open", "cloudislands.admin.templates"),
            Map.entry("admin.migration.open", "cloudislands.admin.migrate-superiorskyblock2")
        );

        expected.forEach((action, permission) -> assertEquals(permission, AdminDashboardMenu.requiredPermission(action)));
        assertEquals("", AdminDashboardMenu.requiredPermission("island.main.open"));
        assertEquals("", AdminDashboardMenu.requiredPermission("gui.close"));
    }
}
