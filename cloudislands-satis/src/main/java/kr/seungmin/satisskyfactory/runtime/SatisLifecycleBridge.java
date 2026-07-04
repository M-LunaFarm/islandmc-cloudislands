package kr.seungmin.satisskyfactory.runtime;

import kr.seungmin.satisskyfactory.storage.SatisStatePortabilityPolicy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SatisLifecycleBridge {
    public Map<String, String> lifecycleState(LifecycleStateSnapshot snapshot) {
        OperationSnapshot operation = operationSnapshot(snapshot.operation());
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-lifecycle-island", snapshot.islandId().toString());
        state.put("last-lifecycle-operation", operation.safeOperation());
        state.put("last-lifecycle-database-open", Boolean.toString(snapshot.databaseOpen()));
        state.put("last-lifecycle-shared-database", Boolean.toString(snapshot.sharedDatabase()));
        state.put("last-lifecycle-schema", "3");
        state.put("last-lifecycle-at", Instant.now().toString());
        state.put("last-lifecycle-status", "success");
        state.put("last-lifecycle-error", "");
        putOperationPlacement(state, operation);
        if (snapshot.activeWorld() != null && !snapshot.activeWorld().isBlank()) {
            state.put("last-lifecycle-active-world", snapshot.activeWorld());
        }
        if (snapshot.activeCenter() != null && !snapshot.activeCenter().isBlank()) {
            state.put("last-lifecycle-active-center", snapshot.activeCenter());
        }
        state.put("last-lifecycle-remap-delta", snapshot.remapDelta() == null || snapshot.remapDelta().isBlank() ? "0,0,0" : snapshot.remapDelta());
        state.put("last-lifecycle-machines-remapped", Boolean.toString(snapshot.machinesRemapped()));
        state.put("last-lifecycle-resource-nodes-remapped", Boolean.toString(snapshot.resourceNodesRemapped()));
        state.put("last-lifecycle-machine-remap-deferred", Boolean.toString(snapshot.machineRemapDeferred()));
        state.put("last-lifecycle-resource-node-remap-deferred", Boolean.toString(snapshot.resourceNodeRemapDeferred()));
        state.put("last-lifecycle-deferred-remap-policy", SatisStatePortabilityPolicy.DEFERRED_REMAP_POLICY);
        state.put("last-lifecycle-remap-source", snapshot.remapSource() == null || snapshot.remapSource().isBlank() ? "active-world-center" : snapshot.remapSource());
        state.put("last-lifecycle-core-hydrate-key", snapshot.hydrationKey());
        state.put("last-lifecycle-core-hydrate-tracked", Boolean.toString(snapshot.hydrationTracked()));
        state.put("core-hydrated-activation-count", Integer.toString(snapshot.coreHydratedActivationCount()));
        return state;
    }

    public Map<String, String> suspendedLifecycleState(SuspendedLifecycleSnapshot snapshot) {
        OperationSnapshot operation = operationSnapshot(snapshot.operation() == null || snapshot.operation().isBlank() ? "recovery-required" : snapshot.operation());
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-lifecycle-island", snapshot.islandId().toString());
        state.put("last-lifecycle-operation", operation.safeOperation());
        state.put("last-lifecycle-database-open", Boolean.toString(snapshot.databaseOpen()));
        state.put("last-lifecycle-shared-database", Boolean.toString(snapshot.sharedDatabase()));
        state.put("last-lifecycle-schema", "3");
        state.put("last-lifecycle-at", Instant.now().toString());
        state.put("last-lifecycle-status", "suspended");
        state.put("last-lifecycle-error", "recovery-required-local-cache-evicted");
        state.put("last-lifecycle-suspend-mode", "drop-local-dirty-state");
        state.put("last-lifecycle-resume-source", "core-api-confirmed-state");
        state.put("last-lifecycle-state-authority", "last-core-confirmed-state-only");
        state.put("last-lifecycle-stale-write-policy", "discard-local-dirty-state");
        state.put("last-lifecycle-heartbeat-expiry-policy", SatisStatePortabilityPolicy.HEARTBEAT_EXPIRY_POLICY);
        state.put("last-lifecycle-fencing-token-policy", SatisStatePortabilityPolicy.FENCING_TOKEN_POLICY);
        state.put("last-lifecycle-error-policy", SatisStatePortabilityPolicy.LIFECYCLE_ERROR_POLICY);
        state.put("last-lifecycle-recovery-policy", SatisStatePortabilityPolicy.LIFECYCLE_RECOVERY_POLICY);
        putOperationPlacement(state, operation);
        return state;
    }

    public OperationSnapshot operationSnapshot(String operation) {
        String safeOperation = operation == null || operation.isBlank() ? "unknown" : operation;
        String activeNode = activeNode(safeOperation);
        String sourceNode = sourceNode(safeOperation);
        String eventNode = eventNode(safeOperation, activeNode);
        String targetNode = activeNode.isBlank() ? eventNode : activeNode;
        return new OperationSnapshot(
                safeOperation,
                eventNode,
                activeNode,
                sourceNode,
                targetNode,
                eventWorld(safeOperation),
                eventCell(safeOperation),
                eventPlacementSource(safeOperation)
        );
    }

    public String node(String nodeId) {
        return nodeId == null || nodeId.isBlank() ? "unknown" : nodeId;
    }

    public String world(String worldName) {
        return worldName == null || worldName.isBlank() ? "" : worldName;
    }

    public String worldToken(String worldName) {
        String safeWorld = world(worldName);
        return safeWorld.isBlank() ? "" : "@" + safeWorld;
    }

    public String cellToken(int cellX, int cellZ) {
        return "#" + cellX + "," + cellZ;
    }

    public String placementToken(String placementSource) {
        return placementSource == null || placementSource.isBlank() ? "" : ":placement-" + placementSource;
    }

    public String runtimeOperation(String state, String targetNode) {
        String safeState = state == null || state.isBlank() ? "UNKNOWN" : state;
        return "runtime:" + safeState + ":" + node(targetNode);
    }

    private void putOperationPlacement(Map<String, String> state, OperationSnapshot operation) {
        if (!operation.eventNode().isBlank()) {
            state.put("last-lifecycle-node", operation.eventNode());
        }
        if (!operation.activeNode().isBlank()) {
            state.put("last-lifecycle-active-node", operation.activeNode());
        }
        if (!operation.sourceNode().isBlank()) {
            state.put("last-lifecycle-source-node", operation.sourceNode());
        }
        if (!operation.targetNode().isBlank()) {
            state.put("last-lifecycle-target-node", operation.targetNode());
        }
        if (!operation.sourceNode().isBlank() && !operation.targetNode().isBlank() && !operation.sourceNode().equals(operation.targetNode())) {
            state.put("last-lifecycle-node-move", operation.sourceNode() + "->" + operation.targetNode());
        }
        state.put("last-lifecycle-node-move-policy", "preflush-source-remap-target-by-island-uuid");
        if (!operation.eventWorld().isBlank()) {
            state.put("last-lifecycle-active-world", operation.eventWorld());
        }
        if (!operation.eventCell().isBlank()) {
            state.put("last-lifecycle-active-cell", operation.eventCell());
        }
        if (!operation.placementSource().isBlank()) {
            state.put("last-lifecycle-placement-source", operation.placementSource());
        }
    }

    private String nodePart(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        int worldSeparator = nodeId.indexOf('@');
        int cellSeparator = nodeId.indexOf('#');
        int end = worldSeparator < 0 ? cellSeparator : (cellSeparator < 0 ? worldSeparator : Math.min(worldSeparator, cellSeparator));
        return node(end < 0 ? nodeId : nodeId.substring(0, end));
    }

    private String activeNode(String operation) {
        if (operation.startsWith("pre-activate:")) {
            return nodePart(operation.substring("pre-activate:".length()));
        }
        if (operation.startsWith("activated:")) {
            return nodePart(operation.substring("activated:".length()));
        }
        if (operation.startsWith("restore-requested:")) {
            return nodePart(operation.substring("restore-requested:".length()));
        }
        if (operation.startsWith("restored:")) {
            return nodePart(operation.substring("restored:".length()));
        }
        if (operation.startsWith("migrated:") || operation.startsWith("migration-requested:")) {
            int arrow = operation.indexOf("->");
            if (arrow < 0 || arrow + 2 >= operation.length()) {
                return "";
            }
            return nodePart(operation.substring(arrow + 2));
        }
        if (operation.startsWith("runtime:")) {
            int nodeSeparator = operation.lastIndexOf(':');
            if (nodeSeparator > "runtime:".length() && nodeSeparator + 1 < operation.length()) {
                return nodePart(operation.substring(nodeSeparator + 1));
            }
        }
        return "";
    }

    private String sourceNode(String operation) {
        if (operation.startsWith("migration-requested:")) {
            int arrow = operation.indexOf("->");
            if (arrow > "migration-requested:".length()) {
                return nodePart(operation.substring("migration-requested:".length(), arrow));
            }
        }
        if (operation.startsWith("migrated:")) {
            int arrow = operation.indexOf("->");
            if (arrow > "migrated:".length()) {
                return nodePart(operation.substring("migrated:".length(), arrow));
            }
        }
        if (operation.startsWith("deactivated:")) {
            return nodePart(operation.substring("deactivated:".length()));
        }
        return "";
    }

    private String eventNode(String operation, String activeNode) {
        if (operation.startsWith("migration-requested:")) {
            int arrow = operation.indexOf("->");
            if (arrow > "migration-requested:".length()) {
                return nodePart(operation.substring("migration-requested:".length(), arrow));
            }
        }
        if (!activeNode.isBlank()) {
            return activeNode;
        }
        if (operation.startsWith("deactivated:")) {
            return nodePart(operation.substring("deactivated:".length()));
        }
        if (operation.startsWith("runtime:")) {
            int nodeSeparator = operation.lastIndexOf(':');
            if (nodeSeparator > "runtime:".length() && nodeSeparator + 1 < operation.length()) {
                return nodePart(operation.substring(nodeSeparator + 1));
            }
        }
        return "";
    }

    private String eventWorld(String operation) {
        int worldSeparator = operation.indexOf('@');
        if (worldSeparator < 0 || worldSeparator + 1 >= operation.length()) {
            return "";
        }
        String value = operation.substring(worldSeparator + 1);
        int cellSeparator = value.indexOf('#');
        return world(cellSeparator < 0 ? value : value.substring(0, cellSeparator));
    }

    private String eventCell(String operation) {
        int cellSeparator = operation.indexOf('#');
        if (cellSeparator < 0 || cellSeparator + 1 >= operation.length()) {
            return "";
        }
        String cell = operation.substring(cellSeparator + 1);
        int separator = cell.indexOf(' ');
        if (separator >= 0) {
            cell = cell.substring(0, separator);
        }
        int placementSeparator = cell.indexOf(":placement-");
        if (placementSeparator >= 0) {
            cell = cell.substring(0, placementSeparator);
        }
        return cell;
    }

    private String eventPlacementSource(String operation) {
        int placementSeparator = operation.indexOf(":placement-");
        if (placementSeparator < 0) {
            return "";
        }
        String value = operation.substring(placementSeparator + ":placement-".length());
        int separator = value.indexOf(' ');
        return separator < 0 ? value : value.substring(0, separator);
    }

    public record OperationSnapshot(
            String safeOperation,
            String eventNode,
            String activeNode,
            String sourceNode,
            String targetNode,
            String eventWorld,
            String eventCell,
            String placementSource
    ) {
    }

    public record LifecycleStateSnapshot(
            UUID islandId,
            String operation,
            boolean databaseOpen,
            boolean sharedDatabase,
            String activeWorld,
            String activeCenter,
            String remapDelta,
            boolean machinesRemapped,
            boolean resourceNodesRemapped,
            String remapSource,
            boolean machineRemapDeferred,
            boolean resourceNodeRemapDeferred,
            String hydrationKey,
            boolean hydrationTracked,
            int coreHydratedActivationCount
    ) {
    }

    public record SuspendedLifecycleSnapshot(
            UUID islandId,
            String operation,
            boolean databaseOpen,
            boolean sharedDatabase
    ) {
    }
}
