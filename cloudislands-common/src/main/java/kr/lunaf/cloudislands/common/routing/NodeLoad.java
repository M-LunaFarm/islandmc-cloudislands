package kr.lunaf.cloudislands.common.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.NodeState;

public record NodeLoad(
    String nodeId,
    String pool,
    String velocityServerName,
    String nodeVersion,
    NodeState state,
    int players,
    int softPlayerCap,
    int hardPlayerCap,
    int reservedSlots,
    int activeIslands,
    int maxActiveIslands,
    double mspt,
    int activationQueue,
    int maxActivationQueue,
    double chunkLoadPressure,
    long heapUsedMb,
    long heapMaxMb,
    int recentFailurePenalty,
    Instant lastHeartbeat,
    boolean storageAvailable,
    String supportedTemplates
) {
    public boolean inPool(String requestedPool) {
        if (requestedPool == null || requestedPool.isBlank()) {
            return true;
        }
        return (pool == null || pool.isBlank() ? "island" : pool).equalsIgnoreCase(requestedPool);
    }

    public boolean eligible(Instant now, Duration heartbeatTimeout) {
        return allocationBlockReason(now, heartbeatTimeout).isBlank();
    }

    public String allocationBlockReason(Instant now, Duration heartbeatTimeout) {
        if (state != NodeState.READY && state != NodeState.SOFT_FULL) {
            return "STATE_" + state.name();
        }
        if (!storageAvailable) {
            return "STORAGE_UNAVAILABLE";
        }
        if (lastHeartbeat == null) {
            return "HEARTBEAT_MISSING";
        }
        if (lastHeartbeat.plus(heartbeatTimeout).isBefore(now)) {
            return "HEARTBEAT_STALE";
        }
        if (hardPlayerCap > 0 && players >= hardPlayerCap) {
            return "HARD_PLAYER_CAP";
        }
        if (maxActiveIslands > 0 && activeIslands >= maxActiveIslands) {
            return "MAX_ACTIVE_ISLANDS";
        }
        if (maxActivationQueue > 0 && activationQueue >= maxActivationQueue) {
            return "MAX_ACTIVATION_QUEUE";
        }
        return "";
    }

    public boolean acceptsExistingRoute(Instant now, Duration heartbeatTimeout, String templateId, String minVersion) {
        return existingRouteBlockReason(now, heartbeatTimeout, templateId, minVersion).isBlank();
    }

    public String existingRouteBlockReason(Instant now, Duration heartbeatTimeout, String templateId, String minVersion) {
        if (state != NodeState.READY && state != NodeState.SOFT_FULL) {
            return "STATE_" + state.name();
        }
        if (lastHeartbeat == null) {
            return "HEARTBEAT_MISSING";
        }
        if (lastHeartbeat.plus(heartbeatTimeout).isBefore(now)) {
            return "HEARTBEAT_STALE";
        }
        if (hardPlayerCap > 0 && players >= hardPlayerCap) {
            return "HARD_PLAYER_CAP";
        }
        if (!supportsTemplate(templateId)) {
            return "TEMPLATE_UNSUPPORTED";
        }
        if (!satisfiesMinVersion(minVersion)) {
            return "NODE_VERSION_TOO_OLD";
        }
        return "";
    }

    public boolean supportsTemplate(String templateId) {
        String templates = templateList();
        if (templates == null || templates.isBlank() || templates.equals("*")) {
            return true;
        }
        String requested = templateId == null || templateId.isBlank() ? "default" : templateId;
        for (String supported : templates.split(",")) {
            if (supported.trim().equals("*") || supported.trim().equalsIgnoreCase(requested)) {
                return true;
            }
        }
        return false;
    }

    public String templateList() {
        if (supportedTemplates == null) {
            return null;
        }
        int metadata = supportedTemplates.indexOf(';');
        return metadata < 0 ? supportedTemplates : supportedTemplates.substring(0, metadata);
    }

    public Map<String, String> heartbeatMetadata() {
        Map<String, String> result = new LinkedHashMap<>();
        if (supportedTemplates == null) {
            return result;
        }
        String[] parts = supportedTemplates.split(";");
        for (int i = 1; i < parts.length; i++) {
            int separator = parts[i].indexOf('=');
            if (separator <= 0) {
                continue;
            }
            result.put(parts[i].substring(0, separator), parts[i].substring(separator + 1));
        }
        return result;
    }

    public boolean satisfiesMinVersion(String minVersion) {
        if (minVersion == null || minVersion.isBlank()) {
            return true;
        }
        if (nodeVersion == null || nodeVersion.isBlank()) {
            return false;
        }
        return compareVersions(nodeVersion, minVersion) >= 0;
    }

    public double score() {
        return playerPressure() * 0.25D
            + activeIslandPressure() * 0.15D
            + msptPressure() * 0.25D
            + activationQueuePressure() * 0.15D
            + chunkLoadPressure * 0.10D
            + memoryPressure() * 0.05D
            + recentFailurePenalty * 0.05D;
    }

    public double playerPressure() {
        return ratio(players, softPlayerCap);
    }

    public double activeIslandPressure() {
        return ratio(activeIslands, maxActiveIslands);
    }

    public double msptPressure() {
        return Math.min(mspt / 50.0D, 1.5D);
    }

    public double activationQueuePressure() {
        return ratio(activationQueue, maxActivationQueue);
    }

    public double memoryPressure() {
        return heapMaxMb <= 0 ? 1.0D : Math.min((double) heapUsedMb / heapMaxMb, 1.5D);
    }

    private static double ratio(int value, int max) {
        return max <= 0 ? 1.0D : Math.min((double) value / max, 1.5D);
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int leftValue = part(leftParts, i);
            int rightValue = part(rightParts, i);
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9].*", ""));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
