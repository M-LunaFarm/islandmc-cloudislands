package kr.lunaf.cloudislands.protocol.job.json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;

public final class IslandJobJson {
    private IslandJobJson() {}

    public static String write(IslandJob job) {
        return "{"
            + "\"jobId\":\"" + job.jobId() + "\","
            + "\"type\":\"" + job.type() + "\","
            + "\"islandId\":\"" + job.islandId() + "\","
            + "\"targetNode\":\"" + escape(nullSafe(job.targetNode())) + "\","
            + "\"priority\":" + job.priority() + ","
            + "\"payload\":" + writeMap(job.payload()) + ","
            + "\"createdAt\":\"" + job.createdAt() + "\","
            + "\"claimLease\":" + writeClaimLease(job.claimLease())
            + "}";
    }

    public static String writeArray(List<IslandJob> jobs) {
        StringBuilder builder = new StringBuilder("{\"jobs\":[");
        boolean first = true;
        for (IslandJob job : jobs) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(write(job));
        }
        return builder.append("]}").toString();
    }

    public static List<IslandJob> readArray(String json) {
        List<IslandJob> jobs = new ArrayList<>();
        int arrayStart = json.indexOf('[');
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd < arrayStart) {
            return jobs;
        }
        int depth = 0;
        int objectStart = -1;
        for (int i = arrayStart + 1; i < arrayEnd; i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    jobs.add(read(json.substring(objectStart, i + 1)));
                    objectStart = -1;
                }
            }
        }
        return jobs;
    }

    public static IslandJob read(String json) {
        return new IslandJob(
            uuid(json, "jobId", UUID.randomUUID()),
            enumValue(IslandJobType.class, text(json, "type", IslandJobType.ACTIVATE_ISLAND.name()), IslandJobType.ACTIVATE_ISLAND),
            uuid(json, "islandId", new UUID(0L, 0L)),
            text(json, "targetNode", ""),
            integer(json, "priority", 0),
            readPayload(json),
            instant(json, "createdAt", Instant.now()),
            readClaimLease(json, uuid(json, "jobId", new UUID(0L, 0L)))
        );
    }

    private static String writeClaimLease(JobClaimLease lease) {
        if (lease == null || !lease.claimed()) {
            return "{}";
        }
        return "{"
            + "\"jobId\":\"" + lease.jobId() + "\","
            + "\"streamId\":\"" + escape(lease.streamId()) + "\","
            + "\"claimedByNode\":\"" + escape(lease.claimedByNode()) + "\","
            + "\"claimToken\":\"" + escape(lease.claimToken()) + "\","
            + "\"claimEpoch\":" + lease.claimEpoch() + ","
            + "\"leaseExpiresAt\":\"" + lease.leaseExpiresAt() + "\","
            + "\"attempt\":" + lease.attempt()
            + "}";
    }

    private static JobClaimLease readClaimLease(String json, UUID fallbackJobId) {
        String leaseJson = objectJson(json, "claimLease");
        if (leaseJson.isBlank()) {
            return JobClaimLease.unclaimed(fallbackJobId);
        }
        UUID jobId = uuid(leaseJson, "jobId", fallbackJobId);
        return new JobClaimLease(
            jobId,
            text(leaseJson, "streamId", ""),
            text(leaseJson, "claimedByNode", ""),
            text(leaseJson, "claimToken", ""),
            longValue(leaseJson, "claimEpoch", 0L),
            instant(leaseJson, "leaseExpiresAt", Instant.EPOCH),
            integer(leaseJson, "attempt", 0)
        );
    }

    private static String writeMap(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    private static Map<String, String> readPayload(String json) {
        int objectStart = valueStart(json, "payload");
        if (objectStart < 0 || json.charAt(objectStart) != '{') {
            return Map.of();
        }
        int depth = 0;
        int objectEnd = -1;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    objectEnd = i;
                    break;
                }
            }
        }
        if (objectEnd < objectStart) {
            return Map.of();
        }
        String body = json.substring(objectStart + 1, objectEnd).trim();
        if (body.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                values.put(unquote(pair.substring(0, colon).trim()), unquote(pair.substring(colon + 1).trim()));
            }
        }
        return Map.copyOf(values);
    }

    private static String objectJson(String json, String field) {
        int objectStart = valueStart(json, field);
        if (objectStart < 0 || json.charAt(objectStart) != '{') {
            return "";
        }
        int depth = 0;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(objectStart, i + 1);
                }
            }
        }
        return "";
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String text(String json, String field, String fallback) {
        int valueStart = valueStart(json, field);
        if (valueStart < 0 || json.charAt(valueStart) != '"') {
            return fallback;
        }
        boolean escaped = false;
        for (int end = valueStart + 1; end < json.length(); end++) {
            char value = json.charAt(end);
            if (escaped) {
                escaped = false;
            } else if (value == '\\') {
                escaped = true;
            } else if (value == '"') {
                return unquote(json.substring(valueStart, end + 1));
            }
        }
        return fallback;
    }

    private static int integer(String json, String field, int fallback) {
        int valueStart = valueStart(json, field);
        if (valueStart < 0) {
            return fallback;
        }
        int end = valueStart;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(valueStart, end));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(String json, String field, long fallback) {
        int valueStart = valueStart(json, field);
        if (valueStart < 0) {
            return fallback;
        }
        int end = valueStart;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(valueStart, end));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int valueStart(String json, String field) {
        String needle = "\"" + field + "\"";
        int fieldStart = json.indexOf(needle);
        if (fieldStart < 0) {
            return -1;
        }
        int colon = json.indexOf(':', fieldStart + needle.length());
        if (colon < 0) {
            return -1;
        }
        int valueStart = colon + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        return valueStart < json.length() ? valueStart : -1;
    }

    private static UUID uuid(String json, String field, UUID fallback) {
        try {
            return UUID.fromString(text(json, field, fallback.toString()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static Instant instant(String json, String field, Instant fallback) {
        try {
            return Instant.parse(text(json, field, fallback.toString()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
