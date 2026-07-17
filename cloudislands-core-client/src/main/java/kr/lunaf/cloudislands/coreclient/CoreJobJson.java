package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;

final class CoreJobJson {
    private CoreJobJson() {
    }

    static List<JobView> jobs(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<Map<?, ?>> jobs = CoreJson.objects(root, "jobs");
        boolean legacyRedisFailures = jobs.isEmpty();
        if (legacyRedisFailures) {
            jobs = CoreJson.objects(root, "failedJobs");
        }
        boolean failedFallback = legacyRedisFailures;
        return jobs.stream()
            .map(object -> job(object, failedFallback))
            .toList();
    }

    static JobActionView action(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = CoreJson.acceptedWithCode(root, successCode);
        return new JobActionView(accepted, CoreJson.code(root, successCode, accepted));
    }

    static JobRecoveryView recovery(String body) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = CoreJson.acceptedWithCode(root, "RECOVERED");
        String recovered = CoreJson.text(root, "recovered");
        if (recovered.isBlank() && root.containsKey("recovered")) {
            recovered = Long.toString(CoreJson.number(root, "recovered"));
        }
        return new JobRecoveryView(accepted, accepted ? recovered : "", CoreJson.code(root, "RECOVERED", accepted));
    }

    private static JobView job(Map<?, ?> object, boolean failedFallback) {
        String id = CoreJson.text(object, "id");
        if (id.isBlank()) {
            id = CoreJson.text(object, "jobId");
        }
        String error = CoreJson.text(object, "error");
        if (error.isBlank()) {
            error = CoreJson.text(object, "errorMessage");
        }
        String state = CoreJson.text(object, "state");
        if (state.isBlank() && failedFallback) {
            state = "FAILED";
        }
        long attempts = CoreJson.number(object, "attempts");
        if (attempts == 0L && object.containsKey("attempt")) {
            attempts = CoreJson.number(object, "attempt");
        }
        String updatedAt = CoreJson.text(object, "updatedAt");
        if (updatedAt.isBlank()) {
            updatedAt = CoreJson.text(object, "failedAt");
        }
        return new JobView(
            id,
            CoreJson.text(object, "type"),
            CoreJson.text(object, "islandId"),
            CoreJson.text(object, "targetNode"),
            state,
            (int) CoreJson.number(object, "priority"),
            attempts,
            CoreJson.text(object, "lockedBy"),
            error,
            CoreJson.stringMap(CoreJson.objectValue(object, "payload")),
            CoreJson.text(object, "createdAt"),
            updatedAt
        );
    }
}
