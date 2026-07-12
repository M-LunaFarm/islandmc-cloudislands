package kr.lunaf.cloudislands.coreservice.redis;

import java.util.List;
import java.util.Optional;

public final class RedisListCachePayload {
    private RedisListCachePayload() {
    }

    public static <T> Optional<List<T>> complete(String payload, List<T> parsedRows) {
        if (payload == null || payload.isBlank() || parsedRows == null || parsedRows.isEmpty()) {
            return Optional.empty();
        }
        long encodedRows = payload.lines().filter(line -> !line.isBlank()).count();
        return encodedRows == parsedRows.size() ? Optional.of(parsedRows) : Optional.empty();
    }
}
