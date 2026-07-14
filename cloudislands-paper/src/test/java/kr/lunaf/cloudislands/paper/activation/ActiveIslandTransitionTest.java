package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveIslandTransitionTest {
    @Test
    void transitionFenceHasSingleOwnerAndCanBeReleased() {
        ActiveIslandRegistry registry = new ActiveIslandRegistry();
        UUID islandId = UUID.randomUUID();

        assertTrue(registry.beginTransition(islandId));
        assertTrue(registry.isTransitioning(islandId));
        assertFalse(registry.beginTransition(islandId));

        registry.endTransition(islandId);

        assertFalse(registry.isTransitioning(islandId));
        assertTrue(registry.beginTransition(islandId));
    }
}
