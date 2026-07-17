package kr.lunaf.cloudislands.paper.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import org.junit.jupiter.api.Test;

class DirectLocalJoinRecoveryPolicyTest {
    private final UUID islandId = UUID.randomUUID();
    private final ActiveIslandRegistry.ActiveIsland active = new ActiveIslandRegistry.ActiveIsland(
        islandId,
        "ci_shard_014",
        800,
        573,
        819200,
        586752,
        300,
        1L,
        2L,
        Instant.EPOCH
    );

    @Test
    void keepsPlayerOnlyInsideTheMarkedActiveIsland() {
        assertFalse(DirectLocalJoinRecoveryPolicy.requiresFallback(
            islandId, "ci_shard_014", 819200.5D, 586752.5D, List.of(active)
        ));
        assertTrue(DirectLocalJoinRecoveryPolicy.requiresFallback(
            UUID.randomUUID(), "ci_shard_014", 819200.5D, 586752.5D, List.of(active)
        ));
    }

    @Test
    void recoversWhenPaperReusesIslandCoordinatesInTheFallbackWorld() {
        assertTrue(DirectLocalJoinRecoveryPolicy.requiresFallback(
            islandId, "world", 819200.5D, 586752.5D, List.of(active)
        ));
    }

    @Test
    void recoversWhenTheRememberedIslandIsNotActiveAfterRestart() {
        assertTrue(DirectLocalJoinRecoveryPolicy.requiresFallback(
            islandId, "world", 819200.5D, 586752.5D, List.of()
        ));
    }

    @Test
    void resolvesInclusiveIslandBoundsWithoutCoordinateMagnitudeHeuristics() {
        assertEquals(islandId, DirectLocalJoinRecoveryPolicy.islandAt(
            "ci_shard_014", 819350.0D, 586602.0D, List.of(active)
        ).orElseThrow().islandId());
        assertTrue(DirectLocalJoinRecoveryPolicy.islandAt(
            "ci_shard_014", 819350.01D, 586602.0D, List.of(active)
        ).isEmpty());
    }
}
