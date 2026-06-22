package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.SnapshotActionView;
import org.junit.jupiter.api.Test;

class VelocitySnapshotMessageFormatterTest {
    private final VelocitySnapshotMessageFormatter formatter = new VelocitySnapshotMessageFormatter();

    @Test
    void formatsTypedSnapshotList() {
        assertEquals(
            "섬 스냅샷: #3 사유=manual 크기=2048 checksum=abcdef123456 생성=2026-06-22T00:00:00Z",
            formatter.snapshotList(List.of(new CoreGuiViews.SnapshotView(3L, "manual", 2048L, "2026-06-22T00:00:00Z", "abcdef1234567890", "/tmp/snapshot.zip")))
        );
    }

    @Test
    void formatsSnapshotListBodyThroughSharedObjectReader() {
        String body = """
            {"snapshots":[{"snapshotNo":3,"reason":"manual","sizeBytes":2048,"createdAt":"2026-06-22T00:00:00Z","checksum":"abcdef1234567890"}]}
            """;

        assertEquals(
            "섬 스냅샷: #3 사유=manual 크기=2048 checksum=abcdef123456 생성=2026-06-22T00:00:00Z",
            formatter.snapshotList(body)
        );
    }

    @Test
    void formatsTypedSnapshotAction() {
        assertEquals(
            "섬 스냅샷 요청: 접수됨 code=SNAPSHOT_REQUESTED",
            formatter.snapshotAction("섬 스냅샷 요청", new SnapshotActionView(true, "SNAPSHOT_REQUESTED"))
        );
    }
}
