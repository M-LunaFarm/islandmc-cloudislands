package kr.lunaf.cloudislands.velocity.event;

import java.util.Map;
import java.util.function.Consumer;

public final class CoreNodeStateEventHandler {
    private final Consumer<String> nodeFallback;

    public CoreNodeStateEventHandler(Consumer<String> nodeFallback) {
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
        nodeFallback.accept(nodeId);
    }
}
