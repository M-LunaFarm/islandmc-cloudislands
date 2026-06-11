package kr.lunaf.cloudislands.paper.cache;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PermissionEventPoller {
    private static final Pattern EVENT = Pattern.compile("\\{[^}]*\"type\":\"([^\"]+)\"[^}]*\"fields\":\\{([^}]*)}[^}]*\"occurredAt\":\"([^\"]+)\"[^}]*}");
    private static final Pattern FIELD = Pattern.compile("\"([^\"]+)\":\"([^\"]*)\"");

    private final Plugin plugin;
    private final CoreApiClient client;
    private final PermissionCacheSyncService permissionSync;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public PermissionEventPoller(Plugin plugin, CoreApiClient client, PermissionCacheSyncService permissionSync) {
        this.plugin = plugin;
        this.client = client;
        this.permissionSync = permissionSync;
    }

    public void start(long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void poll() {
        try {
            String json = client.listEvents().join();
            Matcher matcher = EVENT.matcher(json == null ? "" : json);
            while (matcher.find()) {
                String type = matcher.group(1);
                String key = type + "@" + matcher.group(3);
                if (!seen.add(key) || !affectsPermissions(type)) {
                    continue;
                }
                String islandId = fields(matcher.group(2)).get("islandId");
                if (islandId != null && !islandId.isBlank()) {
                    permissionSync.sync(UUID.fromString(islandId));
                }
            }
            if (seen.size() > 2048) {
                seen.clear();
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to poll permission cache events: " + exception.getMessage());
        }
    }

    private boolean affectsPermissions(String type) {
        try {
            return CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(type)).contains(CacheInvalidationPlan.CacheTarget.PERMISSIONS);
        } catch (IllegalArgumentException ignored) {
            return type.equals("ISLAND_PERMISSION_SET")
                || type.equals("ISLAND_MEMBER_SET")
                || type.equals("ISLAND_MEMBER_REMOVE")
                || type.equals("ISLAND_OWNERSHIP_TRANSFER")
                || type.equals("ISLAND_VISITOR_BAN")
                || type.equals("ISLAND_VISITOR_PARDON");
        }
    }

    private Map<String, String> fields(String raw) {
        Map<String, String> result = new java.util.HashMap<>();
        Matcher matcher = FIELD.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(2));
        }
        return result;
    }
}
