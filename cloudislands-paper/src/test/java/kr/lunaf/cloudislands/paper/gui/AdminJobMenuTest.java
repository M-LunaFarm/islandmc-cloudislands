package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.JobView;
import org.junit.jupiter.api.Test;

class AdminJobMenuTest {
    @Test
    void jobEntriesKeepFullIdentifiersAndSanitizeOperatorText() {
        UUID jobId = UUID.randomUUID();
        JobView job = new JobView(
            jobId.toString(),
            "RESTORE_ISLAND",
            UUID.randomUUID().toString(),
            "island-2",
            "FAILED",
            10,
            3L,
            "",
            "checksum failed\nwith control line",
            Map.of(),
            "2026-07-17T00:00:00Z",
            "2026-07-17T00:01:00Z"
        );

        assertEquals(
            Map.of("jobId", jobId.toString(), "page", "2"),
            AdminJobMenu.jobActionData(job, 2)
        );
        assertTrue(AdminJobMenu.jobTitle(job).contains("RESTORE_ISLAND"));
        assertTrue(AdminJobMenu.jobLore(job, null).stream().noneMatch(line -> line.contains("\n")));
        assertTrue(AdminJobMenu.jobLore(job, null).stream().anyMatch(line -> line.contains("FAILED")));
    }
}
