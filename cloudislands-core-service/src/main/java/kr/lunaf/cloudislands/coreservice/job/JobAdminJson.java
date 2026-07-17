package kr.lunaf.cloudislands.coreservice.job;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.protocol.job.IslandJob;

final class JobAdminJson {
    private JobAdminJson() {
    }

    static Map<String, Object> entry(IslandJob job, String state, long attempts, String lockedBy,
                                     String error, Instant updatedAt) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("id", job.jobId() == null ? "" : job.jobId().toString());
        values.put("type", job.type() == null ? "" : job.type().name());
        values.put("islandId", job.islandId() == null ? "" : job.islandId().toString());
        values.put("targetNode", safe(job.targetNode()));
        values.put("state", safe(state));
        values.put("priority", job.priority());
        values.put("attempts", Math.max(0L, attempts));
        values.put("lockedBy", safe(lockedBy));
        values.put("error", safe(error));
        values.put("payload", job.payload() == null ? Map.of() : job.payload());
        values.put("createdAt", instant(job.createdAt()));
        values.put("updatedAt", instant(updatedAt));
        return Map.copyOf(values);
    }

    static String jobs(List<Map<String, Object>> jobs) {
        return SimpleJson.stringify(Map.of("jobs", jobs == null ? List.of() : List.copyOf(jobs)));
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
