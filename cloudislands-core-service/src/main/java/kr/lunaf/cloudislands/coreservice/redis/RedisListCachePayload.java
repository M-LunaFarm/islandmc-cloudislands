package kr.lunaf.cloudislands.coreservice.redis;

import java.util.List;
import java.util.Optional;

public final class RedisListCachePayload {
    private static final String EMPTY_PAYLOAD = "#CI_EMPTY_LIST_V1";

    private RedisListCachePayload() {
    }

    public static <T> Optional<List<T>> complete(String payload, List<T> parsedRows) {
        if (EMPTY_PAYLOAD.equals(payload)) {
            return Optional.of(List.of());
        }
        if (payload == null || payload.isBlank() || parsedRows == null || parsedRows.isEmpty()) {
            return Optional.empty();
        }
        long encodedRows = payload.lines().filter(line -> !line.isBlank()).count();
        return encodedRows == parsedRows.size() ? Optional.of(parsedRows) : Optional.empty();
    }

    public static String emptyPayload() {
        return EMPTY_PAYLOAD;
    }
}
