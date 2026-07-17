package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;
import org.junit.jupiter.api.Test;

class AdminMigrationMenuTest {
    @Test
    void statusShowsOperationalProgressWithoutExposingApprovalToken() {
        MigrationRunSnapshot status = new MigrationRunSnapshot(
            "DRY_RUN_PASSED",
            "plugins/SuperiorSkyblock2\nunsafe-line",
            "manifest.json",
            "report.json",
            "test-approval-token",
            "fingerprint",
            12,
            true,
            false,
            0,
            false,
            12,
            false,
            0,
            true,
            false,
            0,
            0L,
            0L,
            12,
            10,
            20,
            2,
            4,
            3,
            5,
            6,
            7,
            8,
            9,
            10,
            11,
            12,
            0,
            2,
            List.of()
        );

        List<String> lore = AdminMigrationMenu.statusLore(status, null);

        assertTrue(lore.stream().noneMatch(line -> line.contains("\n")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("매니페스트: 12")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("승인 토큰 준비: YES")));
        assertFalse(lore.stream().anyMatch(line -> line.contains("test-approval-token")));
    }
}
