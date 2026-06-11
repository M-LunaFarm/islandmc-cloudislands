package kr.lunaf.cloudislands.paper.cache;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.plugin.Plugin;

public final class PermissionCacheSyncService {
    private static final Pattern MEMBER = Pattern.compile("\\{[^}]*\"playerUuid\":\"([^\"]+)\"[^}]*\"role\":\"([^\"]+)\"[^}]*}");
    private static final Pattern RULE = Pattern.compile("\\{[^}]*\"role\":\"([^\"]+)\"[^}]*\"permission\":\"([^\"]+)\"[^}]*\"allowed\":(true|false)[^}]*}");

    private final Plugin plugin;
    private final CoreApiClient client;
    private final LocalIslandPermissionCache cache;

    public PermissionCacheSyncService(Plugin plugin, CoreApiClient client, LocalIslandPermissionCache cache) {
        this.plugin = plugin;
        this.client = client;
        this.cache = cache;
    }

    public void sync(UUID islandId) {
        try {
            cache.invalidate(islandId);
            loadMembers(islandId, client.listIslandMembers(islandId).join());
            loadRules(islandId, client.listIslandPermissions(islandId).join());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to sync island permission cache for " + islandId + ": " + exception.getMessage());
        }
    }

    private void loadMembers(UUID islandId, String json) {
        Matcher matcher = MEMBER.matcher(json == null ? "" : json);
        while (matcher.find()) {
            try {
                cache.putRole(islandId, UUID.fromString(matcher.group(1)), IslandRole.valueOf(matcher.group(2)));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadRules(UUID islandId, String json) {
        Matcher matcher = RULE.matcher(json == null ? "" : json);
        while (matcher.find()) {
            try {
                cache.putRule(islandId, IslandRole.valueOf(matcher.group(1)), IslandPermission.valueOf(matcher.group(2)), Boolean.parseBoolean(matcher.group(3)));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
