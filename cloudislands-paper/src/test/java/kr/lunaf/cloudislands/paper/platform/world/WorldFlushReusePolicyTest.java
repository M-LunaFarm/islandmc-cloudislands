package kr.lunaf.cloudislands.paper.platform.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WorldFlushReusePolicyTest {
    @Test
    void reusesOnlyRecentAutomaticFlushesForTheSameWorld() {
        WorldFlushReusePolicy policy = new WorldFlushReusePolicy(Duration.ofSeconds(30));

        assertTrue(policy.requiresFlush("ci_shard_001", "AUTO", 1_000L));
        policy.record("ci_shard_001", 1_000L);

        assertFalse(policy.requiresFlush("ci_shard_001", "AUTO", 30_999L));
        assertTrue(policy.requiresFlush("ci_shard_001", "AUTO", 31_000L));
        assertTrue(policy.requiresFlush("ci_shard_002", "AUTO", 1_001L));
    }

    @Test
    void operatorAndLifecycleReasonsAlwaysForceAFreshFlush() {
        WorldFlushReusePolicy policy = new WorldFlushReusePolicy(Duration.ofMinutes(1));
        policy.record("ci_shard_001", 10_000L);

        assertTrue(policy.requiresFlush("ci_shard_001", "DEACTIVATION", 10_001L));
        assertTrue(policy.requiresFlush("ci_shard_001", "BEFORE_MIGRATION", 10_001L));
        assertTrue(policy.requiresFlush("ci_shard_001", "SAVE_ISLAND", 10_001L));
        assertTrue(policy.requiresFlush("ci_shard_001", "AUTO", 9_999L), "clock rollback must fail safe");
    }
}
