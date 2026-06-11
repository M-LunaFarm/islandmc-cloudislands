package kr.lunaf.cloudislands.common.routing;

import java.time.Duration;
import java.time.Instant;
import kr.lunaf.cloudislands.api.model.NodeState;

public record NodeLoad(
    String nodeId,
    String velocityServerName,
    NodeState state,
    int players,
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
        return players < hardPlayerCap && activationQueue < maxActivationQueue;
    }

    public boolean supportsTemplate(String templateId) {
        if (supportedTemplates == null || supportedTemplates.isBlank() || supportedTemplates.equals("*")) {
            return true;
        }
        String requested = templateId == null || templateId.isBlank() ? "default" : templateId;
        for (String supported : supportedTemplates.split(",")) {
            if (supported.trim().equals("*") || supported.trim().equalsIgnoreCase(requested)) {
                return true;
            }
        }
        return false;
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
}
