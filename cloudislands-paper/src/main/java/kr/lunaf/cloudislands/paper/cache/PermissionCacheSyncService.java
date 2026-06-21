package kr.lunaf.cloudislands.paper.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PermissionCacheSyncService {
    private final Plugin plugin;
    private final CoreApiClient client;
    private final LocalIslandPermissionCache cache;

    public PermissionCacheSyncService(Plugin plugin, CoreApiClient client, LocalIslandPermissionCache cache) {
        this.plugin = plugin;
        this.client = client;
        this.cache = cache;
    }

    public void sync(UUID islandId) {
        if (Bukkit.isPrimaryThread()) {
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runAsync(plugin, () -> sync(islandId));
            return;
        }
        try {
            cache.invalidate(islandId);
            loadMembers(islandId, client.listIslandMembers(islandId).join());
            loadRoles(islandId, client.listIslandRoles(islandId).join());
            loadRules(islandId, client.listIslandPermissions(islandId).join());
            loadFlags(islandId, client.listIslandFlags(islandId).join());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to sync island permission cache for " + islandId + ": " + exception.getMessage());
        }
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public void invalidate(UUID islandId) {
        cache.invalidate(islandId);
    }

    private void loadMembers(UUID islandId, String json) {
        for (Map<?, ?> object : objects(json, "members")) {
            try {
                cache.putRoleKey(islandId, UUID.fromString(text(object, "playerUuid")), roleKey(object, IslandRole.VISITOR.name()));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadRules(UUID islandId, String json) {
        List<Map<?, ?>> rules = objects(json, "rules");
        if (rules.isEmpty()) {
            rules = objects(json, "permissions");
        }
        for (Map<?, ?> object : rules) {
            try {
                cache.putRuleKey(islandId, roleKey(object, IslandRole.VISITOR.name()), IslandPermission.valueOf(text(object, "permission")), bool(object, "allowed"));
            } catch (RuntimeException ignored) {
            }
        }
        for (Map<?, ?> object : objects(json, "overrides")) {
            try {
                cache.putPlayerOverride(islandId, UUID.fromString(text(object, "playerUuid")), IslandPermission.valueOf(text(object, "permission")), bool(object, "allowed"));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadRoles(UUID islandId, String json) {
        for (Map<?, ?> object : objects(json, "roles")) {
            try {
                cache.putRoleDefinition(islandId, roleKey(object, ""));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadFlags(UUID islandId, String json) {
        for (Map.Entry<?, ?> entry : SimpleJson.object(SimpleJson.parse(json)).entrySet()) {
            try {
                cache.putFlag(islandId, IslandFlag.valueOf(SimpleJson.text(entry.getKey())), SimpleJson.text(entry.getValue()));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private List<Map<?, ?>> objects(String json, String arrayField) {
        List<Map<?, ?>> result = new ArrayList<>();
        Object array = SimpleJson.object(SimpleJson.parse(json)).get(arrayField);
        for (Object item : SimpleJson.list(array)) {
            Map<?, ?> object = SimpleJson.object(item);
            if (!object.isEmpty()) {
                result.add(object);
            }
        }
        return result;
    }

    private String text(Map<?, ?> object, String field) {
        return SimpleJson.text(object.get(field));
    }

    private String roleKey(Map<?, ?> object, String fallback) {
        String value = text(object, "roleKey");
        if (value.isBlank()) {
            value = text(object, "role");
        }
        if (value.isBlank()) {
            value = fallback;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private boolean bool(Map<?, ?> object, String field) {
        Object value = object.get(field);
        return value instanceof Boolean booleanValue ? booleanValue : Boolean.parseBoolean(SimpleJson.text(value));
    }
}
