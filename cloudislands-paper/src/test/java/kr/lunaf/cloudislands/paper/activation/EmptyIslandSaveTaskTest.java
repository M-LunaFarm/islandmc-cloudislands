package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.IslandLifecycleActionView;
import org.junit.jupiter.api.Test;

class EmptyIslandSaveTaskTest {
    @Test
    void onlyAcceptsTheSameActivationGenerationAfterSaveCompletes() {
        UUID islandId = UUID.randomUUID();
        Instant activatedAt = Instant.now();
        ActiveIslandRegistry.ActiveIsland expected = active(islandId, 17L, activatedAt);

        assertTrue(EmptyIslandSaveTask.sameActivation(expected, active(islandId, 17L, activatedAt)));
        assertFalse(EmptyIslandSaveTask.sameActivation(expected, active(islandId, 18L, activatedAt.plusSeconds(1))));
        assertFalse(EmptyIslandSaveTask.sameActivation(expected, active(UUID.randomUUID(), 17L, activatedAt)));
        assertFalse(EmptyIslandSaveTask.sameActivation(expected, null));
    }

    @Test
    void retriesRejectedEmptyIslandDeactivationRequests() {
        assertTrue(EmptyIslandSaveTask.deactivationAccepted(
            new IslandLifecycleActionView(true, "DEACTIVATION_QUEUED"), null
        ));
        assertFalse(EmptyIslandSaveTask.deactivationAccepted(
            new IslandLifecycleActionView(false, "INVALID_STATE"), null
        ));
        assertFalse(EmptyIslandSaveTask.deactivationAccepted(null, null));
        assertFalse(EmptyIslandSaveTask.deactivationAccepted(
            new IslandLifecycleActionView(true, "DEACTIVATION_QUEUED"), new IllegalStateException("offline")
        ));
    }

    private ActiveIslandRegistry.ActiveIsland active(UUID islandId, long fencingToken, Instant activatedAt) {
        return new ActiveIslandRegistry.ActiveIsland(
            islandId, "ci_shard_001", 0, 0, 0, 0, 100, 1L, fencingToken, activatedAt
        );
    }
}
