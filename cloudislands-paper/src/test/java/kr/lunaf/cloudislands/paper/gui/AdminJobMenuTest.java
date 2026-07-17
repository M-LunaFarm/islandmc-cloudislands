package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void jobInteractionsMatchCoreRetryAndCancelStateRules() {
        AdminJobMenu.JobInteraction failed = AdminJobMenu.interaction(job("FAILED"));
        AdminJobMenu.JobInteraction pending = AdminJobMenu.interaction(job("PENDING"));
        AdminJobMenu.JobInteraction claimed = AdminJobMenu.interaction(job("CLAIMED"));
        AdminJobMenu.JobInteraction completed = AdminJobMenu.interaction(job("COMPLETED"));
        AdminJobMenu.JobInteraction canceled = AdminJobMenu.interaction(job("CANCELED"));
        AdminJobMenu.JobInteraction unknown = AdminJobMenu.interaction(job("NEW_STATE"));

        assertEquals("admin.jobs.retry", failed.actionId());
        assertTrue(failed.retryable());
        assertTrue(failed.cancelable());
        assertEquals("admin.jobs.cancel.prepare", pending.actionId());
        assertFalse(pending.retryable());
        assertTrue(pending.cancelable());
        assertEquals("", claimed.actionId());
        assertEquals("admin-job-menu-active-no-action", claimed.noActionMessageKey());
        assertEquals("", completed.actionId());
        assertEquals("", canceled.actionId());
        assertEquals("", unknown.actionId());
        assertEquals("admin-job-menu-unknown-no-action", unknown.noActionMessageKey());
    }

    @Test
    void jobLoreOnlyAdvertisesActionsAcceptedForItsState() {
        assertTrue(AdminJobMenu.jobLore(job("FAILED"), null).stream().anyMatch(line -> line.contains("재시도")));
        assertTrue(AdminJobMenu.jobLore(job("FAILED"), null).stream().anyMatch(line -> line.contains("취소")));
        assertFalse(AdminJobMenu.jobLore(job("PENDING"), null).stream().anyMatch(line -> line.contains("재시도")));
        assertTrue(AdminJobMenu.jobLore(job("PENDING"), null).stream().anyMatch(line -> line.contains("취소")));
        assertFalse(AdminJobMenu.jobLore(job("COMPLETED"), null).stream().anyMatch(line -> line.contains("재시도")));
        assertFalse(AdminJobMenu.jobLore(job("COMPLETED"), null).stream().anyMatch(line -> line.contains("취소 확인")));
        assertTrue(AdminJobMenu.jobLore(job("COMPLETED"), null).stream().anyMatch(line -> line.contains("변경할 수 없습니다")));
    }

    private static JobView job(String state) {
        return new JobView(
            "00000000-0000-0000-0000-000000000001",
            "RESTORE_ISLAND",
            "00000000-0000-0000-0000-000000000002",
            "island-2",
            state,
            10,
            1L,
            "",
            "",
            Map.of(),
            "2026-07-17T00:00:00Z",
            "2026-07-17T00:01:00Z"
        );
    }
}
