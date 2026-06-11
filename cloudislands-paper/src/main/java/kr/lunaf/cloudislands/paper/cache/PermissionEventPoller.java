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
import kr.lunaf.cloudislands.paper.generator.GeneratorLevelCache;
import kr.lunaf.cloudislands.paper.limit.IslandLimitCache;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PermissionEventPoller {
    private static final Pattern EVENT = Pattern.compile("\\{[^}]*\"type\":\"([^\"]+)\"[^}]*\"fields\":\\{([^}]*)}[^}]*\"occurredAt\":\"([^\"]+)\"[^}]*}");
    private static final Pattern FIELD = Pattern.compile("\"([^\"]+)\":\"([^\"]*)\"");

    private final Plugin plugin;
    private final CoreApiClient client;
    private final PermissionCacheSyncService permissionSync;
    private final GeneratorLevelCache generatorLevels;
    private final IslandLimitCache limits;
    private final String nodeId;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public PermissionEventPoller(Plugin plugin, CoreApiClient client, PermissionCacheSyncService permissionSync, GeneratorLevelCache generatorLevels, IslandLimitCache limits, String nodeId) {
        this.plugin = plugin;
        this.client = client;
        this.permissionSync = permissionSync;
        this.generatorLevels = generatorLevels;
        this.limits = limits;
        this.nodeId = nodeId;
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
                Map<String, String> fields = fields(matcher.group(2));
                String key = eventKey(type, fields, matcher.group(3));
                if (!seen.add(key)) {
                    continue;
                }
                if (handlesNodeOperation(type, fields)) {
                    continue;
                }
                if (affectsPermissions(type, fields)) {
                    String islandId = fields.get("islandId");
                    if (islandId != null && !islandId.isBlank()) {
                        permissionSync.sync(UUID.fromString(islandId));
                    } else if (isGlobalCacheEvent(type)) {
                        permissionSync.invalidateAll();
                    }
                }
                if (affectsGenerator(type, fields)) {
                    String islandId = fields.get("islandId");
                    if (islandId != null && !islandId.isBlank()) {
                        generatorLevels.invalidate(UUID.fromString(islandId));
                    } else if (isGlobalCacheEvent(type)) {
                        generatorLevels.invalidateAll();
                    }
                }
                if (affectsLimits(type, fields)) {
                    String islandId = fields.get("islandId");
                    if (islandId != null && !islandId.isBlank()) {
                        limits.invalidate(UUID.fromString(islandId));
                    } else if (isGlobalCacheEvent(type)) {
                        limits.invalidateAll();
                    }
                }
            }
            if (seen.size() > 2048) {
                seen.clear();
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to poll permission cache events: " + exception.getMessage());
        }
    }

    private String eventKey(String type, Map<String, String> fields, String occurredAt) {
        String identity = firstPresent(fields, "islandId", "ticketId", "jobId", "inviteId", "nodeId", "playerUuid");
        if (identity.isBlank()) {
            identity = fields.toString();
        }
        return type + "@" + occurredAt + "@" + identity;
    }

    private String firstPresent(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.getOrDefault(key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean handlesNodeOperation(String type, Map<String, String> fields) {
        String targetNode = fields.getOrDefault("nodeId", "");
        if (!targetNode.equals(nodeId)) {
            return false;
        }
        String reason = fields.getOrDefault("reason", "admin-request");
        String state = fields.getOrDefault("state", "");
        if ((type.equals(CloudIslandEventType.NODE_STATE_CHANGED.name()) && state.equals("KICKALL")) || type.equals("NODE_KICKALL")) {
            Bukkit.getScheduler().runTask(plugin, () -> kickPlayers(reason));
            return true;
        }
        if ((type.equals(CloudIslandEventType.NODE_STATE_CHANGED.name()) && state.equals("SHUTDOWN_SAFE")) || type.equals("NODE_SHUTDOWN_SAFE")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                kickPlayers(reason);
                Bukkit.shutdown();
            });
            return true;
        }
        return false;
    }

    private void kickPlayers(String reason) {
        String message = reason == null || reason.isBlank()
            ? "섬 점검으로 로비로 이동합니다."
            : "섬 점검으로 로비로 이동합니다. 사유: " + reason;
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            player.kickPlayer(message);
        }
    }

    private boolean affectsPermissions(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.PERMISSIONS)) {
            return true;
        }
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

    private boolean isGlobalCacheEvent(String type) {
        return type.equals(CloudIslandEventType.CORE_CACHE_CLEARED.name())
            || type.equals(CloudIslandEventType.CORE_RELOADED.name());
    }

    private boolean affectsGenerator(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.GENERATOR)) {
            return true;
        }
        try {
            if (!CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(type)).contains(CacheInvalidationPlan.CacheTarget.GENERATOR)) {
                return false;
            }
            return !type.equals(CloudIslandEventType.ISLAND_UPGRADE.name()) || fields.getOrDefault("upgradeKey", "").equalsIgnoreCase("generator");
        } catch (IllegalArgumentException ignored) {
            return type.equals("ISLAND_UPGRADE") && fields.getOrDefault("upgradeKey", "").equalsIgnoreCase("generator");
        }
    }

    private boolean affectsLimits(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.LIMITS)) {
            return true;
        }
        try {
            return CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(type)).contains(CacheInvalidationPlan.CacheTarget.LIMITS);
        } catch (IllegalArgumentException ignored) {
            return type.equals("ISLAND_LIMIT_SET");
        }
    }

    private boolean targetsInclude(Map<String, String> fields, CacheInvalidationPlan.CacheTarget target) {
        String cacheTargets = fields.getOrDefault("cacheTargets", "");
        for (String value : cacheTargets.split(",")) {
            if (value.trim().equals(target.name())) {
                return true;
            }
        }
        return false;
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
