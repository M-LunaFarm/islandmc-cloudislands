package kr.lunaf.cloudislands.coreservice.permission;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionOverrideSnapshot;
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
        cache(islandId, delegate.list(islandId), delegate.listPlayerOverrides(islandId));
    }

    @Override
    public void putRoleKey(UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        delegate.putRoleKey(islandId, roleKey, permission, allowed);
        cache(islandId, delegate.list(islandId), delegate.listPlayerOverrides(islandId));
    }

    @Override
    public List<IslandPermissionRuleSnapshot> list(UUID islandId) {
        Optional<CachedRules> cached = cachedRules(islandId);
        if (cached.isPresent()) {
            return cached.get().rules();
        }
        List<IslandPermissionRuleSnapshot> rules = delegate.list(islandId);
        cache(islandId, rules, delegate.listPlayerOverrides(islandId));
        return rules;
    }

    @Override
    public void putPlayerOverride(UUID islandId, UUID playerUuid, IslandPermission permission, boolean allowed) {
        delegate.putPlayerOverride(islandId, playerUuid, permission, allowed);
        cache(islandId, delegate.list(islandId), delegate.listPlayerOverrides(islandId));
    }

    @Override
    public List<IslandPermissionOverrideSnapshot> listPlayerOverrides(UUID islandId) {
        Optional<CachedRules> cached = cachedRules(islandId);
        if (cached.isPresent()) {
            return cached.get().overrides();
        }
        List<IslandPermissionRuleSnapshot> rules = delegate.list(islandId);
        List<IslandPermissionOverrideSnapshot> overrides = delegate.listPlayerOverrides(islandId);
        cache(islandId, rules, overrides);
        return overrides;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private void cache(UUID islandId, List<IslandPermissionRuleSnapshot> rules, List<IslandPermissionOverrideSnapshot> overrides) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandPermissions(islandId), rulesJson(rules, overrides));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<CachedRules> cachedRules(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandPermissions(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<IslandPermissionRuleSnapshot> rules = new ArrayList<>();
            for (String object : objects(arrayBody(json, "rules"))) {
                rules.add(new IslandPermissionRuleSnapshot(
                    JsonFields.uuid(object, "islandId", islandId),
                    roleKey(object),
                    JsonFields.enumValue(IslandPermission.class, object, "permission", IslandPermission.INTERACT),
                    JsonFields.bool(object, "allowed", false)
                ));
            }
            List<IslandPermissionOverrideSnapshot> overrides = new ArrayList<>();
            for (String object : objects(arrayBody(json, "overrides"))) {
                overrides.add(new IslandPermissionOverrideSnapshot(
                    JsonFields.uuid(object, "islandId", islandId),
                    JsonFields.uuid(object, "playerUuid", new UUID(0L, 0L)),
                    JsonFields.enumValue(IslandPermission.class, object, "permission", IslandPermission.INTERACT),
                    JsonFields.bool(object, "allowed", false)
                ));
            }
            return Optional.of(new CachedRules(List.copyOf(rules), List.copyOf(overrides)));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String rulesJson(List<IslandPermissionRuleSnapshot> rules, List<IslandPermissionOverrideSnapshot> overrides) {
        StringBuilder builder = new StringBuilder("{\"rules\":[");
        boolean first = true;
        for (IslandPermissionRuleSnapshot rule : rules) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(rule.islandId()).append("\",")
                .append("\"role\":\"").append(rule.effectiveRoleKey()).append("\",")
                .append("\"roleKey\":\"").append(rule.effectiveRoleKey()).append("\",")
                .append("\"permission\":\"").append(rule.permission().name()).append("\",")
                .append("\"allowed\":").append(rule.allowed())
                .append('}');
        }
        builder.append("],\"overrides\":[");
        first = true;
        for (IslandPermissionOverrideSnapshot override : overrides) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(override.islandId()).append("\",")
                .append("\"playerUuid\":\"").append(override.playerUuid()).append("\",")
                .append("\"permission\":\"").append(override.permission().name()).append("\",")
                .append("\"allowed\":").append(override.allowed())
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String roleKey(String object) {
        String roleKey = JsonFields.text(object, "roleKey", "");
        if (roleKey.isBlank()) {
            roleKey = JsonFields.text(object, "role", IslandRole.VISITOR.name());
        }
        return kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository.normalizeRoleKey(roleKey);
    }

    private static String arrayBody(String json, String field) {
        int fieldStart = json.indexOf("\"" + field + "\":[");
        if (fieldStart < 0) {
            return "";
        }
        int start = json.indexOf('[', fieldStart);
        int depth = 0;
        for (int index = start; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, index);
                }
            }
        }
        return "";
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

    private record CachedRules(List<IslandPermissionRuleSnapshot> rules, List<IslandPermissionOverrideSnapshot> overrides) {}
}
