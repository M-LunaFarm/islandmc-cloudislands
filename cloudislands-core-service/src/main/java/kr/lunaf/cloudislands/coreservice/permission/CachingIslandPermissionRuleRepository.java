package kr.lunaf.cloudislands.coreservice.permission;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
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
}
