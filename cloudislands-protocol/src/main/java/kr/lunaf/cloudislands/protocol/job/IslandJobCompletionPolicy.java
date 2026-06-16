package kr.lunaf.cloudislands.protocol.job;

import java.util.Map;

public final class IslandJobCompletionPolicy {
    public static final String CONTRACT = "job-completion-carries-job-identity-node-and-fencing-token";
    public static final String JOB_ID_KEY = "jobId";
    public static final String JOB_TYPE_KEY = "jobType";
    public static final String TARGET_NODE_KEY = "targetNode";
    public static final String COMPLETION_NODE_KEY = "completionNode";
    public static final String FENCING_TOKEN_KEY = "fencingToken";
    public static final String IGNORED_REASON_KEY = "reason";
    public static final String STALE_FENCING_TOKEN = "STALE_FENCING_TOKEN";
    public static final String STALE_NODE_COMPLETION = "STALE_NODE_COMPLETION";
    public static final String RUNTIME_NOT_ACCEPTING_COMPLETION = "RUNTIME_NOT_ACCEPTING_COMPLETION";

    private IslandJobCompletionPolicy() {
    }

    public static IslandJobCompletionPayload carryJobContext(IslandJob job, IslandJobCompletionPayload payload, String completionNode) {
        IslandJobCompletionPayload result = payload == null ? IslandJobCompletionPayload.empty() : payload;
        if (job == null) {
            return result.with(COMPLETION_NODE_KEY, completionNode);
        }
        result = result
            .with(JOB_ID_KEY, job.jobId() == null ? "" : job.jobId().toString())
            .with(JOB_TYPE_KEY, job.type() == null ? "" : job.type().name())
            .with(TARGET_NODE_KEY, job.targetNode())
            .with(COMPLETION_NODE_KEY, completionNode);
        String fencingToken = job.payload().get(FENCING_TOKEN_KEY);
        if (fencingToken != null && !fencingToken.isBlank() && !result.fields().containsKey(FENCING_TOKEN_KEY)) {
            result = result.with(FENCING_TOKEN_KEY, fencingToken);
        }
        return result;
    }

    public static long fencingToken(Map<String, String> payload) {
        if (payload == null) {
            return 0L;
        }
        try {
            return Long.parseLong(payload.getOrDefault(FENCING_TOKEN_KEY, "0"));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
