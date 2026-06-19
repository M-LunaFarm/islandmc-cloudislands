package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class VelocityNodeJobMessageFormatterTest {
    @Test
    void formatsNodeSummaryWithVisibleTopology() {
        VelocityNodeJobMessageFormatter formatter = new VelocityNodeJobMessageFormatter(new VelocityRoutePrivacyFormatter(false));
        String body = """
            {"nodes":[{"id":"island-1","state":"READY","players":3,"softPlayerCap":20,"hardPlayerCap":30,"reservedSlots":2,"activeIslands":5,"maxActiveIslands":10,"activationQueue":1,"maxActivationQueue":4,"mspt":18.25,"score":0.42,"eligibleForNewActivation":true,"storageAvailable":true,"scoreBreakdown":{"playerPressure":0.1,"activeIslandPressure":0.2,"msptPressure":0.3,"activationQueuePressure":0.4,"memoryPressure":0.5,"recentFailurePenalty":0.6}},{"id":"island-2","state":"DOWN","storageAvailable":false}],"pools":[{"pool":"island","healthyNodeCount":1,"nodeCount":2,"players":3,"softPlayerCap":40,"hardPlayerCap":60,"reservedSlots":2,"activeIslands":5,"maxActiveIslands":20,"activationQueue":1,"maxActivationQueue":8}]}
            """;

        assertEquals(
            "Nodes: total=2 starting=0 warming=0 ready=1 softFull=0 hardFull=0 draining=0 shuttingDown=0 down=1 / pools: island nodes=1/2 players=3/40/60 reserved=2 islands=5/20 queue=1/8 / island-1 READY players=3/20/30 reserved=2 islands=5/10 queue=1/4 mspt=18.250 score=0.420 parts=p:0.100,a:0.200,m:0.300,q:0.400,mem:0.500,fail:0.600 activation=ok storage=ok | island-2 DOWN players=0/0/0 reserved=0 islands=0/0 queue=0/0 mspt=0.000 score=0.000 activation=blocked:UNKNOWN storage=down",
            formatter.nodeListSummary(body)
        );
    }

    @Test
    void hidesNodeNamesInStorageAndJobSummaries() {
        VelocityNodeJobMessageFormatter formatter = new VelocityNodeJobMessageFormatter(new VelocityRoutePrivacyFormatter(true));
        String storage = """
            {"nodes":[{"nodeId":"island-1","storageAvailable":false,"storage":{"backend":"s3","primaryDegraded":true,"healthCheckFailures":1,"uploadFailures":2,"downloadFailures":3,"operationFailures":4,"uploadSeconds":0.125,"downloadSeconds":0.5}}]}
            """;
        String jobs = """
            {"jobs":[{"id":"abcdef12-3456-7890-abcd-ef1234567890","type":"ACTIVATE","state":"FAILED","attempts":2,"targetNode":"island-1","error":"boom"}]}
            """;

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
            formatter.nodeActionSummary("Node drain", "island-1", "{\"accepted\":false,\"code\":\"BUSY\"}")
        );
        assertEquals("Node sweep: nodes=island-1,island-2 recoveryRequired=2", formatter.nodeSweep("{\"nodes\":[\"island-1\",\"island-2\"],\"recoveryRequired\":2}"));
        assertEquals("Job retry: accepted", formatter.jobAction("retry", "{\"ok\":true}"));
        assertEquals("Job recover: recovered=3", formatter.jobAction("recover", "{\"recovered\":3}"));
    }

    @Test
    void appendsActivationAndLevelScanSummary() {
        VelocityNodeJobMessageFormatter formatter = new VelocityNodeJobMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals(
            "{\"eligibleForNewActivation\":false,\"allocationBlockReason\":\"FULL\",\"levelScan\":{\"running\":true,\"lastIsland\":\"island-a\",\"startedAt\":10}} | 활성화 배정=차단(FULL) | 레벨 스캔=실행 중, 마지막 섬=island-a, 시작=10",
            formatter.appendLevelScanSummary("{\"eligibleForNewActivation\":false,\"allocationBlockReason\":\"FULL\",\"levelScan\":{\"running\":true,\"lastIsland\":\"island-a\",\"startedAt\":10}}")
        );
    }
}
