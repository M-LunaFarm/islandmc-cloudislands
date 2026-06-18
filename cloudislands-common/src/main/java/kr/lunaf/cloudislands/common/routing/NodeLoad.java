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
    private static final double PLAYER_WEIGHT = 0.25D;
    private static final double ACTIVE_ISLAND_WEIGHT = 0.15D;
    private static final double MSPT_WEIGHT = 0.25D;
    private static final double ACTIVATION_QUEUE_WEIGHT = 0.15D;
    private static final double CHUNK_LOAD_WEIGHT = 0.10D;
    private static final double MEMORY_WEIGHT = 0.05D;
    private static final double RECENT_FAILURE_WEIGHT = 0.05D;

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
        if (defaultNodeIdentityRisk()) {
            return "DEFAULT_NODE_IDENTITY";
        }
        if (!storageAvailable) {
            return "STORAGE_UNAVAILABLE";
        }
        if (storagePrimaryDegraded()) {
            return "STORAGE_PRIMARY_DEGRADED";
        }
        if (storageSaveRetryQueueTotal() > 0) {
            return "STORAGE_SAVE_RETRY_QUEUE";
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
        if (defaultNodeIdentityRisk()) {
            return "DEFAULT_NODE_IDENTITY";
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
        if (!satisfiesTemplateVersion(templateId, minVersion)) {
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

    public boolean storagePrimaryDegraded() {
        return "true".equalsIgnoreCase(heartbeatMetadata().getOrDefault("storagePrimaryDegraded", "false"));
    }

    public int storageSaveRetryQueueTotal() {
        Map<String, String> metadata = heartbeatMetadata();
        if (metadata.containsKey("storageSaveRetryQueueTotal")) {
            return metadataInt(metadata, "storageSaveRetryQueueTotal");
        }
        return Math.max(0, metadataInt(metadata, "periodicSaveRetryQueue")
            + metadataInt(metadata, "emptySaveRetryQueue"));
    }

    public boolean defaultNodeIdentityRisk() {
        return "true".equalsIgnoreCase(heartbeatMetadata().getOrDefault("defaultNodeIdentityRisk", "false"));
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

    public boolean satisfiesTemplateVersion(String templateId, String minVersion) {
        if (minVersion == null || minVersion.isBlank()) {
            return true;
        }
        String templateVersion = templateNodeVersion(templateId);
        if (templateVersion.isBlank()) {
            return satisfiesMinVersion(minVersion);
        }
        return compareVersions(templateVersion, minVersion) >= 0;
    }

    public String templateNodeVersion(String templateId) {
        Map<String, String> metadata = heartbeatMetadata();
        if (metadata.isEmpty()) {
            return "";
        }
        String requested = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        String direct = metadataValueIgnoreCase(metadata, "templateVersion." + requested);
        if (!direct.isBlank()) {
            return direct;
        }
        String versions = metadataValueIgnoreCase(metadata, "templateVersions");
        if (versions.isBlank()) {
            return "";
        }
        String fallback = "";
        for (String entry : versions.split(",")) {
            String value = entry.trim();
            if (value.isBlank()) {
                continue;
            }
            int separator = value.indexOf(':');
            if (separator < 0) {
                separator = value.indexOf('=');
            }
            if (separator <= 0) {
                continue;
            }
            String key = value.substring(0, separator).trim();
            String version = value.substring(separator + 1).trim();
            if (version.isBlank()) {
                continue;
            }
            if (key.equalsIgnoreCase(requested)) {
                return version;
            }
            if (key.equals("*") || key.equalsIgnoreCase("default")) {
                fallback = version;
            }
        }
        return fallback;
    }

    private String metadataValueIgnoreCase(Map<String, String> metadata, String key) {
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    public double score() {
        return playerScoreContribution()
            + activeIslandScoreContribution()
            + msptScoreContribution()
            + activationQueueScoreContribution()
            + chunkLoadScoreContribution()
            + memoryScoreContribution()
            + recentFailureScoreContribution();
    }

    public Map<String, Double> scoreBreakdown() {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("playerPressure", playerPressure());
        breakdown.put("playerWeight", PLAYER_WEIGHT);
        breakdown.put("playerContribution", playerScoreContribution());
        breakdown.put("activeIslandPressure", activeIslandPressure());
        breakdown.put("activeIslandWeight", ACTIVE_ISLAND_WEIGHT);
        breakdown.put("activeIslandContribution", activeIslandScoreContribution());
        breakdown.put("msptPressure", msptPressure());
        breakdown.put("msptWeight", MSPT_WEIGHT);
        breakdown.put("msptContribution", msptScoreContribution());
        breakdown.put("activationQueuePressure", activationQueuePressure());
        breakdown.put("activationQueueWeight", ACTIVATION_QUEUE_WEIGHT);
        breakdown.put("activationQueueContribution", activationQueueScoreContribution());
        breakdown.put("chunkLoadRawPressure", chunkLoadPressure());
        breakdown.put("chunkLoadPressure", chunkLoadScorePressure());
        breakdown.put("chunkLoadWeight", CHUNK_LOAD_WEIGHT);
        breakdown.put("chunkLoadContribution", chunkLoadScoreContribution());
        breakdown.put("memoryPressure", memoryPressure());
        breakdown.put("memoryWeight", MEMORY_WEIGHT);
        breakdown.put("memoryContribution", memoryScoreContribution());
        breakdown.put("recentFailurePenalty", (double) recentFailurePenalty());
        breakdown.put("recentFailurePressure", recentFailurePressure());
        breakdown.put("recentFailureWeight", RECENT_FAILURE_WEIGHT);
        breakdown.put("recentFailureContribution", recentFailureScoreContribution());
        breakdown.put("storageSaveRetryQueueTotal", (double) storageSaveRetryQueueTotal());
        breakdown.put("score", score());
        return Map.copyOf(breakdown);
    }

    public double playerPressure() {
        return ratio(players, softPlayerCap);
    }

    public double activeIslandPressure() {
        return ratio(activeIslands, maxActiveIslands);
    }

    public double msptPressure() {
        return clampPressure(mspt / 50.0D);
    }

    public double activationQueuePressure() {
        return ratio(activationQueue, maxActivationQueue);
    }

    public double chunkLoadScorePressure() {
        return clampPressure(chunkLoadPressure);
    }

    public double memoryPressure() {
        return heapMaxMb <= 0 ? 1.0D : clampPressure((double) heapUsedMb / heapMaxMb);
    }

    public double recentFailurePressure() {
        return clampPressure((double) Math.max(0, recentFailurePenalty) / 20.0D);
    }

    public double playerScoreContribution() {
        return playerPressure() * PLAYER_WEIGHT;
    }

    public double activeIslandScoreContribution() {
        return activeIslandPressure() * ACTIVE_ISLAND_WEIGHT;
    }

    public double msptScoreContribution() {
        return msptPressure() * MSPT_WEIGHT;
    }

    public double activationQueueScoreContribution() {
        return activationQueuePressure() * ACTIVATION_QUEUE_WEIGHT;
    }

    public double chunkLoadScoreContribution() {
        return chunkLoadScorePressure() * CHUNK_LOAD_WEIGHT;
    }

    public double memoryScoreContribution() {
        return memoryPressure() * MEMORY_WEIGHT;
    }

    public double recentFailureScoreContribution() {
        return recentFailurePressure() * RECENT_FAILURE_WEIGHT;
    }

    private static double ratio(int value, int max) {
        return max <= 0 ? 1.0D : clampPressure((double) value / max);
    }

    private static double clampPressure(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.5D;
        }
        return Math.min(Math.max(value, 0.0D), 1.5D);
    }

    private static int metadataInt(Map<String, String> metadata, String key) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
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
