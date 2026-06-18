package kr.lunaf.cloudislands.protocol.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePreparationProgressPolicyTest {
    @Test
    void preparingProgressStartsLowAndNeverLooksCompleteBeforeReady() {
        assertEquals(20, RoutePreparationProgressPolicy.preparingPercent(0));
        assertEquals(24, RoutePreparationProgressPolicy.preparingPercent(1));
        assertEquals(95, RoutePreparationProgressPolicy.preparingPercent(99));
        assertEquals(0.95F, RoutePreparationProgressPolicy.preparingProgress(99));
    }

    @Test
    void handoffProgressStaysBelowReadyCompletion() {
        assertEquals(0.10F, RoutePreparationProgressPolicy.handoffProgress(0));
        assertEquals(0.95F, RoutePreparationProgressPolicy.handoffProgress(99));
    }

    @Test
    void playerMessagesHidePhysicalNodeAndShardNames() {
        assertEquals("섬", RoutePreparationProgressPolicy.safeTargetName("island-2"));
        assertEquals("섬", RoutePreparationProgressPolicy.safeTargetName("ci_shard_001"));
        assertEquals("내 섬", RoutePreparationProgressPolicy.safeTargetName("내 섬"));
        assertEquals("섬 로딩 중 28%", RoutePreparationProgressPolicy.loadingTitle("island-2", 2));
        assertEquals("섬을 준비하는 중입니다... 28%", RoutePreparationProgressPolicy.preparingActionBar("ci_shard_001", 2));
        assertTrue(RoutePreparationProgressPolicy.CONTRACT.contains("hide-node-names"));
    }
}
