package kr.lunaf.cloudislands.coreservice.role;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandRoleRepository implements IslandRoleRepository {
    private final IslandRoleRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandRoleRepository(IslandRoleRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public IslandRoleSnapshot upsert(UUID islandId, IslandRole role, int weight, String displayName) {
        IslandRoleSnapshot snapshot = delegate.upsert(islandId, role, weight, displayName);
        cache(islandId, delegate.list(islandId));
        return snapshot;
    }

    @Override
    public boolean reset(UUID islandId, IslandRole role) {
        boolean removed = delegate.reset(islandId, role);
        cache(islandId, delegate.list(islandId));
        return removed;
    }

    @Override
    public List<IslandRoleSnapshot> list(UUID islandId) {
        Optional<List<IslandRoleSnapshot>> cached = cachedRoles(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<IslandRoleSnapshot> roles = delegate.list(islandId);
        cache(islandId, roles);
        return roles;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private void cache(UUID islandId, List<IslandRoleSnapshot> roles) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandRoles(islandId), rolesJson(roles));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<List<IslandRoleSnapshot>> cachedRoles(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandRoles(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<IslandRoleSnapshot> roles = new ArrayList<>();
            for (String object : objects(json)) {
                roles.add(new IslandRoleSnapshot(
                    JsonFields.uuid(object, "islandId", islandId),
                    JsonFields.enumValue(IslandRole.class, object, "role", IslandRole.MEMBER),
                    JsonFields.integer(object, "weight", 0),
                    JsonFields.text(object, "displayName", "")
                ));
            }
            return Optional.of(List.copyOf(roles));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String rolesJson(List<IslandRoleSnapshot> roles) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (IslandRoleSnapshot role : roles) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(role.islandId()).append("\",")
                .append("\"role\":\"").append(role.role().name()).append("\",")
                .append("\"weight\":").append(role.weight()).append(',')
                .append("\"displayName\":\"").append(escape(role.displayName())).append("\"")
                .append('}');
        }
        return builder.append(']').toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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
