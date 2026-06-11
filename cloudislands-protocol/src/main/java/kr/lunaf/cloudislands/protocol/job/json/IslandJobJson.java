package kr.lunaf.cloudislands.protocol.job.json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class IslandJobJson {
    private IslandJobJson() {}

    public static String write(IslandJob job) {
        return "{"
            + "\"jobId\":\"" + job.jobId() + "\","
            + "\"type\":\"" + job.type() + "\","
            + "\"islandId\":\"" + job.islandId() + "\","
            + "\"targetNode\":\"" + nullSafe(job.targetNode()) + "\","
            + "\"priority\":" + job.priority() + ","
            + "\"createdAt\":\"" + job.createdAt() + "\""
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
            Map.of(),
            instant(json, "createdAt", Instant.now())
        );
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String text(String json, String field, String fallback) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + needle.length();
        int end = json.indexOf('"', valueStart);
        return end < 0 ? fallback : json.substring(valueStart, end);
    }

    private static int integer(String json, String field, int fallback) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + needle.length();
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
