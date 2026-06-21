package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.JdbcIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.JobCompletionService;
import kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.json.IslandJobJson;

public final class JobRoutes implements RouteGroup {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final IslandJobQueue jobs;
    private final JobCompletionService completion;
    private final AuditLogger audit;

    public JobRoutes(IslandJobQueue jobs, JobCompletionService completion, AuditLogger audit) {
        this.jobs = jobs;
        this.completion = completion;
        this.audit = audit;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/jobs", exchange -> CoreHttpResponses.write(exchange, 200, jobsJson(jobs)));
        registry.route("/v1/jobs/claim", this::claim);
        registry.route("/v1/jobs/complete", this::complete);
        registry.route("/v1/jobs/fail", this::fail);
        registry.route("/v1/jobs/recover", this::recover);
        registry.route("/v1/admin/jobs/recover", this::recover);
        registry.route("/v1/admin/jobs/list", exchange -> CoreHttpResponses.write(exchange, 200, jobsJson(jobs)));
        registry.route("/v1/admin/jobs/retry", this::retry);
        registry.route("/v1/admin/jobs/cancel", this::cancel);
    }

    private void claim(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String nodeId = JsonFields.text(body, "nodeId", "");
        List<IslandJob> claimed = jobs.claim(nodeId, supportedJobTypes(JsonFields.text(body, "supportedTypes", "")), JsonFields.integer(body, "maxJobs", 4));
        CoreHttpResponses.write(exchange, 200, IslandJobJson.writeArray(claimed));
    }

    private void complete(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID jobId = JsonFields.uuid(body, "jobId", SYSTEM_ACTOR);
        String nodeId = JsonFields.text(body, "nodeId", "");
        Map<String, String> payload = JsonFields.object(body, "payload");
        java.util.Optional<IslandJob> claimed = jobs.findClaimed(jobId).map(job -> {
            Map<String, String> merged = new HashMap<>();
            merged.putAll(job.payload());
            merged.putAll(payload);
            return new IslandJob(job.jobId(), job.type(), job.islandId(), job.targetNode(), job.priority(), Map.copyOf(merged), job.createdAt());
        });
        if (claimed.isEmpty()) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("JOB_CLAIM_MISMATCH", "Job is not claimed by this node"));
            return;
        }
        try {
            completion.completed(claimed.get());
        } catch (RuntimeException exception) {
            CoreHttpResponses.write(exchange, 500, ApiResponses.error("JOB_COMPLETION_FAILED", "Job completion was not committed; retry the claimed job"));
            return;
        }
        if (!jobs.complete(nodeId, jobId)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("JOB_CLAIM_MISMATCH", "Job is not claimed by this node"));
            return;
        }
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void fail(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID jobId = JsonFields.uuid(body, "jobId", SYSTEM_ACTOR);
        String nodeId = JsonFields.text(body, "nodeId", "");
        String error = JsonFields.text(body, "error", "unknown");
        java.util.Optional<IslandJob> claimed = jobs.findClaimed(jobId);
        if (claimed.isEmpty() || !jobs.fail(nodeId, jobId, error)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("JOB_CLAIM_MISMATCH", "Job is not claimed by this node"));
            return;
        }
        claimed.ifPresent(job -> completion.failed(job, error));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void recover(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String nodeId = JsonFields.text(body, "nodeId", "recovery");
        long minIdleMillis = JsonFields.longValue(body, "minIdleMillis", 60000L);
        int maxJobs = JsonFields.integer(body, "maxJobs", 16);
        if (jobs instanceof RedisIslandJobQueue redisJobs) {
            String recovered = redisJobs.recoverPending(nodeId, minIdleMillis, maxJobs);
            audit.log(SYSTEM_ACTOR, "ADMIN", "JOB_RECOVER", "JOB", nodeId, Map.of("backend", "REDIS", "minIdleMillis", Long.toString(minIdleMillis), "maxJobs", Integer.toString(maxJobs), "result", recovered));
            CoreHttpResponses.write(exchange, 202, "{\"recovered\":\"" + recovered.replace("\"", "'") + "\"}");
        } else if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
            String recovered = jdbcJobs.recoverPending(nodeId, minIdleMillis, maxJobs);
            audit.log(SYSTEM_ACTOR, "ADMIN", "JOB_RECOVER", "JOB", nodeId, Map.of("backend", "JDBC", "minIdleMillis", Long.toString(minIdleMillis), "maxJobs", Integer.toString(maxJobs), "result", recovered));
            CoreHttpResponses.write(exchange, 202, "{\"recovered\":" + recovered + "}");
        } else {
            audit.log(SYSTEM_ACTOR, "ADMIN", "JOB_RECOVER", "JOB", nodeId, Map.of("backend", "UNAVAILABLE", "minIdleMillis", Long.toString(minIdleMillis), "maxJobs", Integer.toString(maxJobs)));
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("RECOVERY_UNAVAILABLE", "Job recovery is only available when CI_JOB_QUEUE_MODE=REDIS or JDBC"));
        }
    }

    private void retry(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID jobId = JsonFields.uuid(body, "jobId", SYSTEM_ACTOR);
        boolean retried = jobs.retry(jobId);
        audit.log(SYSTEM_ACTOR, "ADMIN", "JOB_RETRY", "JOB", jobId.toString(), Map.of("retried", Boolean.toString(retried)));
        CoreHttpResponses.write(exchange, retried ? 202 : 404, retried ? ApiResponses.ok(true) : ApiResponses.error("JOB_NOT_RETRIED", "Job was not found or cannot be retried"));
    }

    private void cancel(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID jobId = JsonFields.uuid(body, "jobId", SYSTEM_ACTOR);
        boolean canceled = jobs.cancel(jobId);
        audit.log(SYSTEM_ACTOR, "ADMIN", "JOB_CANCEL", "JOB", jobId.toString(), Map.of("canceled", Boolean.toString(canceled)));
        CoreHttpResponses.write(exchange, canceled ? 202 : 404, canceled ? ApiResponses.ok(true) : ApiResponses.error("JOB_NOT_CANCELED", "Job was not found or cannot be canceled"));
    }

    static String jobsJson(IslandJobQueue jobs) {
        if (jobs instanceof InMemoryIslandJobPublisher memoryJobs) {
            return memoryJobs.toJson();
        }
        if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
            return jdbcJobs.toJson();
        }
        if (jobs instanceof RedisIslandJobQueue redisJobs) {
            Map<String, Long> counts = redisJobs.countsByState();
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("mode", "REDIS");
            values.put("pending", counts.getOrDefault("PENDING", 0L));
            values.put("claimed", counts.getOrDefault("CLAIMED", 0L));
            values.put("failed", counts.getOrDefault("FAILED", 0L));
            values.put("retryAttempts", redisJobs.retryAttemptsTotal());
            values.put("redisFailures", redisJobs.redisFailuresTotal());
            return SimpleJson.stringify(values);
        }
        return SimpleJson.stringify(Map.of("mode", "EXTERNAL"));
    }

    static List<IslandJobType> supportedJobTypes(String value) {
        if (value == null || value.isBlank()) {
            return List.of(
                IslandJobType.CREATE_ISLAND,
                IslandJobType.ACTIVATE_ISLAND,
                IslandJobType.SAVE_ISLAND,
                IslandJobType.DEACTIVATE_ISLAND,
                IslandJobType.SNAPSHOT_ISLAND,
                IslandJobType.DELETE_ISLAND,
                IslandJobType.MIGRATE_ISLAND,
                IslandJobType.RESTORE_ISLAND,
                IslandJobType.RESET_ISLAND
            );
        }
        List<IslandJobType> result = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                result.add(IslandJobType.valueOf(part.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown worker capabilities.
            }
        }
        return result.isEmpty() ? supportedJobTypes("") : List.copyOf(result);
    }
}
