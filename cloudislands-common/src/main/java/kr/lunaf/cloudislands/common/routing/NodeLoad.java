package kr.lunaf.cloudislands.common.routing;

import java.time.Duration;
import java.time.Instant;
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
        if (state != NodeState.READY && state != NodeState.SOFT_FULL) {
            return false;
        }
        if (!storageAvailable) {
            return false;
        }
        if (lastHeartbeat == null || lastHeartbeat.plus(heartbeatTimeout).isBefore(now)) {
            return false;
        }
        return players < hardPlayerCap && activeIslands < maxActiveIslands && activationQueue < maxActivationQueue;
    }

    public boolean acceptsExistingRoute(Instant now, Duration heartbeatTimeout, String templateId, String minVersion) {
        if (state != NodeState.READY && state != NodeState.SOFT_FULL) {
            return false;
        }
        if (!storageAvailable) {
            return false;
        }
        if (lastHeartbeat == null || lastHeartbeat.plus(heartbeatTimeout).isBefore(now)) {
            return false;
        }
        return players < hardPlayerCap && supportsTemplate(templateId) && satisfiesMinVersion(minVersion);
    }

    public boolean supportsTemplate(String templateId) {
        String templates = templateList(supportedTemplates);
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

    private static String templateList(String value) {
        if (value == null) {
            return null;
        }
        int metadata = value.indexOf(';');
        return metadata < 0 ? value : value.substring(0, metadata);
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
        double playerRatio = ratio(players, hardPlayerCap);
        double activeIslandRatio = ratio(activeIslands, maxActiveIslands);
        double msptRatio = Math.min(mspt / 50.0D, 1.5D);
        double queueRatio = ratio(activationQueue, maxActivationQueue);
        double memoryPressure = heapMaxMb <= 0 ? 1.0D : Math.min((double) heapUsedMb / heapMaxMb, 1.5D);
        return playerRatio * 0.25D
            + activeIslandRatio * 0.15D
            + msptRatio * 0.25D
            + queueRatio * 0.15D
            + chunkLoadPressure * 0.10D
            + memoryPressure * 0.05D
            + recentFailurePenalty * 0.05D;
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
