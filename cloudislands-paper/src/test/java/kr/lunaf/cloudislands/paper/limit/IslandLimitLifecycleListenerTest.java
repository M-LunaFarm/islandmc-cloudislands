package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IslandLimitLifecycleListenerTest {
    @Test
    void invalidatesEveryIslandScopedRuntimeStore() {
        UUID islandId = UUID.randomUUID();
        List<UUID> invalidated = new ArrayList<>();
        IslandRuntimeStateInvalidator invalidator = new IslandRuntimeStateInvalidator(List.of(
            invalidated::add,
            invalidated::add,
            invalidated::add,
            invalidated::add,
            invalidated::add
        ));

        invalidator.invalidate(islandId);

        assertEquals(List.of(islandId, islandId, islandId, islandId, islandId), invalidated);
    }
}
