package kr.lunaf.cloudislands.velocity.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import kr.lunaf.cloudislands.api.model.NodeState;
import org.junit.jupiter.api.Test;

class CoreNodeStateEventHandlerTest {
    @Test
    void delayedDownDoesNotEvacuatePlayersAfterNodeRecovery() {
        List<String> fallbacks = new ArrayList<>();
        CoreNodeStateEventHandler handler = new CoreNodeStateEventHandler(
            ignored -> CompletableFuture.completedFuture(NodeState.READY),
            fallbacks::add
        );

        handler.handle(event("DOWN", ""));

        assertEquals(List.of(), fallbacks);
    }

    @Test
    void confirmedDownEvacuatesPlayers() {
        List<String> fallbacks = new ArrayList<>();
        CoreNodeStateEventHandler handler = new CoreNodeStateEventHandler(
            ignored -> CompletableFuture.completedFuture(NodeState.DOWN),
            fallbacks::add
        );

        handler.handle(event("DOWN", ""));

        assertEquals(List.of("island-a"), fallbacks);
    }

    @Test
    void explicitAdminOperationRemainsEdgeTriggered() {
        AtomicInteger lookups = new AtomicInteger();
        List<String> fallbacks = new ArrayList<>();
        CoreNodeStateEventHandler handler = new CoreNodeStateEventHandler(
            ignored -> {
                lookups.incrementAndGet();
                return CompletableFuture.completedFuture(NodeState.READY);
            },
            fallbacks::add
        );

        handler.handle(event("KICKALL", ""));

        assertEquals(0, lookups.get());
        assertEquals(List.of("island-a"), fallbacks);
    }

    private CoreEventEnvelope event(String state, String operation) {
        return new CoreEventEnvelope(
            7L,
            "NODE_STATE_CHANGED",
            Map.of("nodeId", "island-a", "state", state, "operation", operation),
            "2026-07-17T00:00:00Z"
        );
    }
}
