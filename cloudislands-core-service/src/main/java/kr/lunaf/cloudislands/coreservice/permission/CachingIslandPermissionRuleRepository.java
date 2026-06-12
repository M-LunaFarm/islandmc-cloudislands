package kr.lunaf.cloudislands.coreservice.permission;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandPermissionRuleRepository implements IslandPermissionRuleRepository {
    private final IslandPermissionRuleRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandPermissionRuleRepository(IslandPermissionRuleRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public void put(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        delegate.put(islandId, role, permission, allowed);
        cache(islandId, delegate.list(islandId));
    }

    @Override
    public List<IslandPermissionRuleSnapshot> list(UUID islandId) {
        Optional<List<IslandPermissionRuleSnapshot>> cached = cachedRules(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<IslandPermissionRuleSnapshot> rules = delegate.list(islandId);
        cache(islandId, rules);
        return rules;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private void cache(UUID islandId, List<IslandPermissionRuleSnapshot> rules) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandPermissions(islandId), rulesJson(rules));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<List<IslandPermissionRuleSnapshot>> cachedRules(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandPermissions(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<IslandPermissionRuleSnapshot> rules = new ArrayList<>();
            for (String object : objects(json)) {
                rules.add(new IslandPermissionRuleSnapshot(
                    JsonFields.uuid(object, "islandId", islandId),
                    JsonFields.enumValue(IslandRole.class, object, "role", IslandRole.VISITOR),
                    JsonFields.enumValue(IslandPermission.class, object, "permission", IslandPermission.INTERACT),
                    JsonFields.bool(object, "allowed", false)
                ));
            }
            return Optional.of(List.copyOf(rules));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String rulesJson(List<IslandPermissionRuleSnapshot> rules) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (IslandPermissionRuleSnapshot rule : rules) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(rule.islandId()).append("\",")
                .append("\"role\":\"").append(rule.role().name()).append("\",")
                .append("\"permission\":\"").append(rule.permission().name()).append("\",")
                .append("\"allowed\":").append(rule.allowed())
                .append('}');
        }
        return builder.append(']').toString();
    }

    private static List<String> objects(String json) {
        List<String> result = new ArrayList<>();
        int index = 0;
        while (index < json.length()) {
            int start = json.indexOf('{', index);
            if (start < 0) {
                return result;
            }
            int end = matchingObjectEnd(json, start);
            if (end < 0) {
                return result;
            }
            result.add(json.substring(start, end + 1));
            index = end + 1;
        }
        return result;
    }

    private static int matchingObjectEnd(String json, int objectStart) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
