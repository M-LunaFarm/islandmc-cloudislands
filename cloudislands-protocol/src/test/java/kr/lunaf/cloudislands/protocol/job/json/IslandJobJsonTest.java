package kr.lunaf.cloudislands.protocol.job.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
import org.junit.jupiter.api.Test;

class IslandJobJsonTest {
    @Test
    void decodesProtocolV1JobResponseWithoutClaimLease() throws Exception {
        List<IslandJob> jobs = IslandJobJson.readArray(resource("compatibility/protocol-v1-job-claim-response.json"));

        assertEquals(1, jobs.size());
        IslandJob job = jobs.getFirst();
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000101"), job.jobId());
        assertEquals(IslandJobType.SAVE_ISLAND, job.type());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000202"), job.islandId());
        assertEquals("island-node-1", job.targetNode());
        assertEquals(7, job.priority());
        assertEquals(Map.of("snapshotNo", "42", "reason", "scheduled-backup"), job.payload());
        assertEquals(Instant.parse("2026-06-23T00:00:00Z"), job.createdAt());
        assertEquals(job.jobId(), job.claimLease().jobId());
        assertFalse(job.claimLease().claimed());
    }

    @Test
    void roundTripsClaimLeaseInClaimResponse() {
        UUID jobId = UUID.randomUUID();
        Instant leaseExpiresAt = Instant.parse("2026-06-23T01:02:03Z");
        IslandJob job = new IslandJob(
            jobId,
            IslandJobType.SAVE_ISLAND,
            UUID.randomUUID(),
            "island-node-1",
            10,
            Map.of("snapshotNo", "7"),
            Instant.EPOCH,
            new JobClaimLease(jobId, "1730000000000-0", "island-node-1", "token-1", 3L, leaseExpiresAt, 2)
        );

        IslandJob parsed = IslandJobJson.readArray(IslandJobJson.writeArray(List.of(job))).getFirst();

        assertEquals(jobId, parsed.claimLease().jobId());
        assertEquals("1730000000000-0", parsed.claimLease().streamId());
        assertEquals("island-node-1", parsed.claimLease().claimedByNode());
        assertEquals("token-1", parsed.claimLease().claimToken());
        assertEquals(3L, parsed.claimLease().claimEpoch());
        assertEquals(leaseExpiresAt, parsed.claimLease().leaseExpiresAt());
        assertEquals(2, parsed.claimLease().attempt());
        assertTrue(parsed.claimLease().claimed());
    }

    @Test
    void decodesCurrentClaimLeaseFromVersionedEnvelope() {
        String body = """
            {
              "protocolVersion": 1,
              "jobs": [
                {
                  "jobId": "00000000-0000-0000-0000-000000000303",
                  "type": "RESTORE_ISLAND",
                  "islandId": "00000000-0000-0000-0000-000000000404",
                  "targetNode": "island-node-2",
                  "priority": 10,
                  "payload": {"snapshotNo": "9"},
                  "createdAt": "2026-06-23T01:00:00Z",
                  "claimLease": {
                    "jobId": "00000000-0000-0000-0000-000000000303",
                    "streamId": "1730000000000-1",
                    "claimedByNode": "island-node-2",
                    "claimToken": "token-2",
                    "claimEpoch": 5,
                    "leaseExpiresAt": "2026-06-23T01:05:00Z",
                    "attempt": 3
                  }
                }
              ]
            }
            """;

        IslandJob job = IslandJobJson.readArray(body).getFirst();

        assertEquals(IslandJobType.RESTORE_ISLAND, job.type());
        assertEquals("9", job.payload().get("snapshotNo"));
        assertEquals("1730000000000-1", job.claimLease().streamId());
        assertEquals("island-node-2", job.claimLease().claimedByNode());
        assertEquals("token-2", job.claimLease().claimToken());
        assertEquals(5L, job.claimLease().claimEpoch());
        assertEquals(Instant.parse("2026-06-23T01:05:00Z"), job.claimLease().leaseExpiresAt());
        assertEquals(3, job.claimLease().attempt());
        assertTrue(job.claimLease().claimed());
    }

    private static String resource(String path) throws IOException {
        try (var stream = IslandJobJsonTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(stream, path).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
