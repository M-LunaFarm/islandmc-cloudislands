package kr.lunaf.cloudislands.coreservice.job;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobCompletionPolicy;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public record JobCompletionRequest(IslandJob job, Map<String, String> completionPayload, String requestHash) {
    private static final Set<String> COMMON_KEYS = Set.of(
        IslandJobCompletionPolicy.JOB_ID_KEY,
        IslandJobCompletionPolicy.JOB_TYPE_KEY,
        IslandJobCompletionPolicy.TARGET_NODE_KEY,
        IslandJobCompletionPolicy.COMPLETION_NODE_KEY,
        IslandJobCompletionPolicy.FENCING_TOKEN_KEY
    );
    private static final Set<String> ACTIVATION_KEYS = Set.of(
        "worldName",
        "cellX",
        "cellZ",
        "schemaVersion",
        "extractedRoot",
        "placementSource",
        "snapshotNo",
        "reason",
        "checksum",
        "sizeBytes",
        "preMutationSnapshotNo",
        "preMutationReason",
        "preMutationChecksum",
        "preMutationSizeBytes"
    );
    private static final Set<String> SNAPSHOT_KEYS = Set.of(
        "snapshotNo",
        "reason",
        "checksum",
        "sizeBytes",
        "ownerUuid"
    );

    public JobCompletionRequest {
        completionPayload = Map.copyOf(completionPayload == null ? Map.of() : completionPayload);
    }

    public static JobCompletionRequest completed(IslandJob claimedJob, Map<String, String> completionPayload) {
        if (claimedJob == null || claimedJob.jobId() == null || claimedJob.type() == null || claimedJob.islandId() == null) {
            throw new IllegalArgumentException("Claimed job identity is required");
        }
        Map<String, String> typedPayload = typedPayload(claimedJob.type(), completionPayload);
        Map<String, String> mergedPayload = new LinkedHashMap<>(claimedJob.payload());
        mergedPayload.putAll(typedPayload);
        IslandJob completedJob = new IslandJob(
            claimedJob.jobId(),
            claimedJob.type(),
            claimedJob.islandId(),
            claimedJob.targetNode(),
            claimedJob.priority(),
            Map.copyOf(mergedPayload),
            claimedJob.createdAt(),
            claimedJob.claimLease()
        );
        return new JobCompletionRequest(completedJob, typedPayload, hashFor(completedJob, typedPayload));
    }

    String requestPayloadJson() {
        return json(completionPayload);
    }

    String receiptPayloadJson() {
        return json(Map.of(
            "jobId", job.jobId().toString(),
            "jobType", job.type().name(),
            "islandId", job.islandId().toString(),
            "targetNode", job.targetNode() == null ? "" : job.targetNode(),
            "requestHash", requestHash
        ));
    }

    private static Map<String, String> typedPayload(IslandJobType type, Map<String, String> payload) {
        Map<String, String> values = payload == null ? Map.of() : payload;
        Set<String> allowed = allowedKeys(type);
        LinkedHashMap<String, String> typed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null || !allowed.contains(key)) {
                throw new IllegalArgumentException("Field 'payload." + key + "' is not allowed for " + type.name());
            }
            typed.put(key, entry.getValue() == null ? "" : entry.getValue());
        }
        return Map.copyOf(typed);
    }

    private static Set<String> allowedKeys(IslandJobType type) {
        return switch (type) {
            case CREATE_ISLAND, ACTIVATE_ISLAND, RESET_ISLAND, RESTORE_ISLAND, MIGRATE_ISLAND ->
                union(COMMON_KEYS, ACTIVATION_KEYS);
            case SAVE_ISLAND, SNAPSHOT_ISLAND, DEACTIVATE_ISLAND, DELETE_ISLAND ->
                union(COMMON_KEYS, SNAPSHOT_KEYS);
            default -> COMMON_KEYS;
        };
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(first);
        values.addAll(second);
        return Set.copyOf(values);
    }

    private static String hashFor(IslandJob job, Map<String, String> completionPayload) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("jobId=").append(job.jobId()).append('\n');
        canonical.append("jobType=").append(job.type().name()).append('\n');
        canonical.append("islandId=").append(job.islandId()).append('\n');
        canonical.append("targetNode=").append(job.targetNode() == null ? "" : job.targetNode()).append('\n');
        for (Map.Entry<String, String> entry : new TreeMap<>(completionPayload).entrySet()) {
            canonical.append("payload.").append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return sha256(canonical.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String json(Map<String, String> values) {
        return kr.lunaf.cloudislands.common.json.SimpleJson.stringify(new TreeMap<>(values));
    }
}
