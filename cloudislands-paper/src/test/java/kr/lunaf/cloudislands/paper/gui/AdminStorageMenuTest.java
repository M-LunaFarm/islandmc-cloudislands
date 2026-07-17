package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminStorageStatusView;
import kr.lunaf.cloudislands.coreclient.AdminStorageStatusView.NodeView;
import org.junit.jupiter.api.Test;

class AdminStorageMenuTest {
    @Test
    void storageNodesPrioritizeFailuresAndSanitizeOperationalText() {
        NodeView healthy = node("island-1", true, false, 0L, 0L, "s3");
        NodeView degraded = node("island-2", true, true, 4L, 3L, "minio\nprimary");
        NodeView down = node("island-3", false, false, 0L, 2L, "s3");

        List<NodeView> entries = AdminStorageMenu.storageEntries(new AdminStorageStatusView(List.of(healthy, degraded, down)));

        assertEquals(List.of(down, degraded, healthy), entries);
        assertEquals(Map.of("page", "2"), AdminStorageMenu.pageData(2));
        assertTrue(AdminStorageMenu.nodeTitle(degraded).contains("DEGRADED"));
        assertTrue(AdminStorageMenu.nodeLore(degraded, null).stream().noneMatch(line -> line.contains("\n")));
        assertTrue(AdminStorageMenu.nodeLore(degraded, null).stream().anyMatch(line -> line.contains("전체 실패: 3")));
        assertTrue(AdminStorageMenu.summaryLore(entries, null).stream().anyMatch(line -> line.contains("사용 불가: 1")));
        assertTrue(AdminStorageMenu.summaryLore(entries, null).stream().anyMatch(line -> line.contains("저장 재시도: 4")));
    }

    private static NodeView node(String nodeId, boolean available, boolean degraded, long retries, long failures, String backend) {
        return new NodeView(
            nodeId,
            available,
            backend,
            degraded,
            retries,
            0.125D,
            0.250D,
            failures,
            0L,
            0L,
            0L
        );
    }
}
