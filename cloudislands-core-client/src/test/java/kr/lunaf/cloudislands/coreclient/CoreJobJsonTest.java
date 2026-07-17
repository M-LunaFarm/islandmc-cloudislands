package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CoreJobJsonTest {
    @Test
    void parsesLegacyRedisFailedJobsDuringRollingUpgrade() {
        String body = """
            {
              "mode": "REDIS",
              "failedJobs": [{
                "jobId": "00000000-0000-0000-0000-000000000001",
                "type": "RESTORE_ISLAND",
                "islandId": "00000000-0000-0000-0000-000000000002",
                "targetNode": "island-2",
                "attempt": "3",
                "failedAt": "2026-07-17T02:03:04Z",
                "error": "storage unavailable"
              }]
            }
            """;

        List<JobView> jobs = CoreJobJson.jobs(body);

        assertEquals(1, jobs.size());
        assertEquals("00000000-0000-0000-0000-000000000001", jobs.getFirst().id());
        assertEquals("FAILED", jobs.getFirst().state());
        assertEquals(3L, jobs.getFirst().attempts());
        assertEquals("2026-07-17T02:03:04Z", jobs.getFirst().updatedAt());
        assertEquals("storage unavailable", jobs.getFirst().error());
    }

    @Test
    void prefersTypedJobsWhenBothFieldsArePresent() {
        String body = """
            {
              "jobs": [{"id":"typed-job","state":"PENDING","attempts":1}],
              "failedJobs": [{"jobId":"legacy-job","attempt":"3"}]
            }
            """;

        List<JobView> jobs = CoreJobJson.jobs(body);

        assertEquals(1, jobs.size());
        assertEquals("typed-job", jobs.getFirst().id());
        assertEquals("PENDING", jobs.getFirst().state());
    }
}
