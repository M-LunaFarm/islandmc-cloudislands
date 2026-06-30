package kr.lunaf.cloudislands.coreservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.common.routing.NodeLoad;

public final class IslandActivationCoordinator {
    private IslandActivationCoordinator() {
    }

    public static boolean placementMissing(IslandRuntimeSnapshot runtime) {
        return runtime == null
            || runtime.activeWorld() == null
            || runtime.activeWorld().isBlank()
            || runtime.cellX() == null
            || runtime.cellZ() == null;
    }

    public static boolean memberReservedSlotsExhausted(NodeLoad node) {
        if (node == null || node.reservedSlots() <= 0 || node.softPlayerCap() <= 0) {
            return false;
        }
        int reservedLimit = node.softPlayerCap() + node.reservedSlots();
        if (node.hardPlayerCap() > 0) {
            reservedLimit = Math.min(reservedLimit, node.hardPlayerCap());
        }
        return node.players() >= reservedLimit;
    }

    public static boolean duplicateVelocityServerName(NodeLoad target, List<NodeLoad> snapshot) {
        String targetKey = velocityServerKey(target);
        if (targetKey.isBlank()) {
            return false;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (NodeLoad node : snapshot) {
            String key = velocityServerKey(node);
            if (!key.isBlank()) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts.getOrDefault(targetKey, 0) > 1;
    }

    public static boolean activeRouteRecoveryReason(String blockReason) {
        return blockReason.equals("NODE_NOT_FOUND")
            || blockReason.equals("HEARTBEAT_MISSING")
            || blockReason.equals("HEARTBEAT_STALE")
            || blockReason.equals("STATE_DOWN");
    }

    private static String velocityServerKey(NodeLoad node) {
        if (node == null || node.velocityServerName() == null || node.velocityServerName().isBlank()) {
            return "";
        }
        String pool = node.pool() == null || node.pool().isBlank() ? "island" : node.pool().trim().toLowerCase(java.util.Locale.ROOT);
        return pool + "\n" + node.velocityServerName().trim().toLowerCase(java.util.Locale.ROOT);
    }
}
