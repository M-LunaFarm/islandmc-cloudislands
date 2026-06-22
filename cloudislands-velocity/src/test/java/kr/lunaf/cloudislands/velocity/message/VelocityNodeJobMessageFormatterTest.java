package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.NodeLevelScanSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.coreclient.AdminNodeActionView;
import kr.lunaf.cloudislands.coreclient.AdminStorageStatusView;
import kr.lunaf.cloudislands.coreclient.JobActionView;
import kr.lunaf.cloudislands.coreclient.JobRecoveryView;
import kr.lunaf.cloudislands.coreclient.JobView;
import org.junit.jupiter.api.Test;

class VelocityNodeJobMessageFormatterTest {
    @Test
    void formatsNodeSummaryWithVisibleTopology() {
        VelocityNodeJobMessageFormatter formatter = new VelocityNodeJobMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals(
            "Nodes: total=2 starting=0 warming=0 ready=1 softFull=0 hardFull=0 draining=0 shuttingDown=0 down=1 / island-1 READY players=3/20/30 reserved=2 islands=5/10 queue=1/4 mspt=18.250 score=0.420 parts=p:0.100,a:0.200,m:0.300,q:0.400,mem:0.500,fail:0.600 activation=ok storage=ok | 레벨 스캔=대기 | island-2 DOWN players=0/0/0 reserved=0 islands=0/0 queue=0/0 mspt=0.000 score=0.000 activation=blocked:UNKNOWN storage=down | 레벨 스캔=대기",
            formatter.nodeListSummary(List.of(
                node("island-1", NodeState.READY, 3, 20, 30, 2, 5, 10, 1, 4, 18.25D, 0.42D, true, true, "", NodeLevelScanSnapshot.empty(), Map.of(
                    "playerPressure", 0.1D,
                    "activeIslandPressure", 0.2D,
                    "msptPressure", 0.3D,
                    "activationQueuePressure", 0.4D,
                    "memoryPressure", 0.5D,
                    "recentFailurePenalty", 0.6D
                )),
                node("island-2", NodeState.DOWN, 0, 0, 0, 0, 0, 0, 0, 0, 0.0D, 0.0D, false, false, "", NodeLevelScanSnapshot.empty(), Map.of())
            ))
        );
    }

    @Test
    void hidesNodeNamesInStorageAndJobSummaries() {
        VelocityNodeJobMessageFormatter formatter = new VelocityNodeJobMessageFormatter(new VelocityRoutePrivacyFormatter(true));
        AdminStorageStatusView storage = new AdminStorageStatusView(List.of(new AdminStorageStatusView.NodeView(
            "island-1", false, "s3", true, 0L, 0.125D, 0.5D, 1L, 2L, 3L, 4L
        )));
        List<JobView> jobs = List.of(new JobView(
            "abcdef12-3456-7890-abcd-ef1234567890",
            "ACTIVATE",
            "",
            "island-1",
            "FAILED",
            0,
            2L,
            "",
            "boom",
            Map.of(),
            "",
            ""
        ));

        String storageMessage = formatter.storageStatus(storage);
        String jobsMessage = formatter.jobList(jobs);

        assertEquals("Storage status: node-1=DOWN(backend=s3, primaryDegraded=true, failures=10, up=0.125s, down=0.500s) / unavailable=1", storageMessage);
        assertEquals("Jobs: total=1 pending=0 claimed=0 failed=1 done=0 other=0 / abcdef12 ACTIVATE FAILED attempts=2 error=boom", jobsMessage);
        assertFalse(storageMessage.contains("island-1"));
        assertFalse(jobsMessage.contains("island-1"));
    }

    @Test
    void formatsNodeAndJobActions() {
        VelocityNodeJobMessageFormatter formatter = new VelocityNodeJobMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals(
            "Node drain: rejected node=island-1 code=BUSY",
            formatter.nodeActionSummary("Node drain", "island-1", new AdminNodeActionView(false, "BUSY", "island-1", "drain"))
        );
        assertEquals("Node sweep: nodes=island-1,island-2 recoveryRequired=2", formatter.nodeSweep(new AdminNodeActionView(true, "", "", "sweep", List.of("island-1", "island-2"), 2)));
        assertEquals("Job retry: accepted", formatter.jobAction("retry", new JobActionView(true, "")));
        assertEquals("Job recover: recovered=3", formatter.jobRecovery(new JobRecoveryView(true, "3", "")));
    }

    @Test
    void appendsActivationAndLevelScanSummary() {
        VelocityNodeJobMessageFormatter formatter = new VelocityNodeJobMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals(
            "Nodes: total=1 starting=0 warming=0 ready=1 softFull=0 hardFull=0 draining=0 shuttingDown=0 down=0 / island-1 READY players=0/0/0 reserved=0 islands=0/0 queue=0/0 mspt=0.000 score=0.000 activation=blocked:FULL storage=ok | 레벨 스캔=실행 중, 마지막 섬=island-a, 시작=10",
            formatter.nodeListSummary(List.of(node("island-1", NodeState.READY, 0, 0, 0, 0, 0, 0, 0, 0, 0.0D, 0.0D, false, true, "FULL", new NodeLevelScanSnapshot(true, "island-a", 10L, 0L, 0L), Map.of())))
        );
    }

    private static IslandNodeSnapshot node(
        String nodeId,
        NodeState state,
        int players,
        int softPlayerCap,
        int hardPlayerCap,
        int reservedSlots,
        int activeIslands,
        int maxActiveIslands,
        int activationQueue,
        int maxActivationQueue,
        double mspt,
        double score,
        boolean eligibleForNewActivation,
        boolean storageAvailable,
        String allocationBlockReason,
        NodeLevelScanSnapshot levelScan,
        Map<String, Double> scoreBreakdown
    ) {
        return new IslandNodeSnapshot(
            nodeId,
            "island",
            nodeId,
            "test",
            state,
            players,
            softPlayerCap,
            hardPlayerCap,
            reservedSlots,
            activeIslands,
            maxActiveIslands,
            mspt,
            activationQueue,
            maxActivationQueue,
            0.0D,
            0L,
            0L,
            0,
            storageAvailable,
            "",
            Instant.EPOCH,
            score,
            scoreBreakdown,
            eligibleForNewActivation,
            allocationBlockReason,
            levelScan,
            kr.lunaf.cloudislands.api.model.NodeStorageSnapshot.empty()
        );
    }
}
