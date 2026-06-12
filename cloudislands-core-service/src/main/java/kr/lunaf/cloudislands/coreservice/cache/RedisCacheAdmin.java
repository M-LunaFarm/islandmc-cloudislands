package kr.lunaf.cloudislands.coreservice.cache;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class RedisCacheAdmin {
    private static final List<String> CACHE_PATTERNS = List.of(
        "ci:player:*:island",
        "ci:player:*:route-ticket",
        "ci:player:*:route-session",
        "ci:island:*:summary",
        "ci:island:*:runtime",
        "ci:island:*:members",
        "ci:island:*:bans",
        "ci:island:*:permissions",
        "ci:island:*:flags",
        "ci:island:*:homes",
        "ci:island:*:warps",
        "ci:island:*:bank",
        "ci:rankings:*"
    );

    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public RedisCacheAdmin(URI redisUri) {
        this.redisUri = redisUri;
    }

    public int clearApplicationCaches() {
        int cleared = 0;
        for (String pattern : CACHE_PATTERNS) {
            cleared += clearPattern(pattern);
        }
        return cleared;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private int clearPattern(String pattern) {
        int cleared = 0;
        for (String key : keys(pattern)) {
            try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                if (!"0".equals(redis.command("DEL", key))) {
                    cleared++;
                }
            } catch (IOException | RuntimeException ignored) {
                failures.incrementAndGet();
            }
        }
        return cleared;
    }

    private List<String> keys(String pattern) {
        List<String> keys = new ArrayList<>();
        String cursor = "0";
        do {
            try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                String response = redis.command("SCAN", cursor, "MATCH", pattern, "COUNT", "100");
                String[] lines = response.split("\\n");
                cursor = lines.length == 0 || lines[0].isBlank() ? "0" : lines[0];
                for (int i = 1; i < lines.length; i++) {
                    if (!lines[i].isBlank()) {
                        keys.add(lines[i]);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                failures.incrementAndGet();
                return keys;
            }
        } while (!"0".equals(cursor));
        return keys;
    }
}
