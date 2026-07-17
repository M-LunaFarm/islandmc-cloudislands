package kr.lunaf.cloudislands.velocity.event;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import kr.lunaf.cloudislands.api.model.NodeState;

public final class CoreNodeStateEventHandler {
    private final Function<String, CompletionStage<NodeState>> currentNodeState;
    private final Consumer<String> nodeFallback;

    public CoreNodeStateEventHandler(Function<String, CompletionStage<NodeState>> currentNodeState, Consumer<String> nodeFallback) {
        this.currentNodeState = currentNodeState;
        this.nodeFallback = nodeFallback;
    }

    public void handle(CoreEventEnvelope event) {
        String type = event.type();
        Map<String, String> fields = event.fields();
        if (!type.equals("NODE_STATE_CHANGED")) {
            return;
        }
        String state = fields.getOrDefault("state", "");
        String operation = fields.getOrDefault("operation", "");
        if (!state.equals("KICKALL") && !state.equals("SHUTDOWN_SAFE") && !state.equals("DOWN") && !operation.equals("SHUTDOWN_SAFE")) {
            return;
        }
        String nodeId = fields.getOrDefault("nodeId", "");
        if (nodeId.isBlank() || nodeId.equals("*")) {
            return;
        }
        if (state.equals("DOWN")) {
            confirmDownBeforeFallback(nodeId);
            return;
        }
        nodeFallback.accept(nodeId);
    }

    private void confirmDownBeforeFallback(String nodeId) {
        CompletionStage<NodeState> lookup;
        try {
            lookup = currentNodeState.apply(nodeId);
        } catch (RuntimeException ignored) {
            return;
        }
        if (lookup == null) {
            return;
        }
        lookup.thenAccept(state -> {
            if (state == NodeState.DOWN) {
                nodeFallback.accept(nodeId);
            }
        }).exceptionally(error -> null);
    }
}
