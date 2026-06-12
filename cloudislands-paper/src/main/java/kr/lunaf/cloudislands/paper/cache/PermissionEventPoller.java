package kr.lunaf.cloudislands.paper.cache;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.generator.CropGrowthLevelCache;
import kr.lunaf.cloudislands.paper.generator.GeneratorLevelCache;
import kr.lunaf.cloudislands.paper.limit.IslandLimitCache;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PermissionEventPoller {
    private static final int MAX_SEEN_EVENTS = 8192;
    private final Plugin plugin;
    private final CoreApiClient client;
    private final PermissionCacheSyncService permissionSync;
    private final GeneratorLevelCache generatorLevels;
    private final CropGrowthLevelCache cropGrowthLevels;
    private final IslandLimitCache limits;
    private final ProtectionController protection;
    private final String nodeId;
    private final String fallbackServerName;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final Deque<String> seenOrder = new ArrayDeque<>();
    private long lastEventSequence;
    private BukkitTask task;

    public PermissionEventPoller(Plugin plugin, CoreApiClient client, PermissionCacheSyncService permissionSync, GeneratorLevelCache generatorLevels, CropGrowthLevelCache cropGrowthLevels, IslandLimitCache limits, ProtectionController protection, String nodeId, String fallbackServerName) {
        this.plugin = plugin;
        this.client = client;
        this.permissionSync = permissionSync;
        this.generatorLevels = generatorLevels;
        this.cropGrowthLevels = cropGrowthLevels;
        this.limits = limits;
        this.protection = protection;
        this.nodeId = nodeId;
        this.fallbackServerName = fallbackServerName == null || fallbackServerName.isBlank() ? "Lobby" : fallbackServerName;
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
            String json = client.listEventsSince(lastEventSequence, 512).join();
            for (ParsedEvent event : events(json)) {
                lastEventSequence = Math.max(lastEventSequence, event.sequence());
                String type = event.type();
                Map<String, String> fields = fields(event.fields());
                String key = eventKey(type, fields, event.occurredAt());
                if (!markSeen(key)) {
                    continue;
                }
                if (handlesNodeOperation(type, fields)) {
                    continue;
                }
                if (handlesVisitorKick(type, fields)) {
                    continue;
                }
                if (handlesIslandChat(type, fields)) {
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
                if (affectsCrop(type, fields)) {
                    String islandId = fields.get("islandId");
                    if (islandId != null && !islandId.isBlank()) {
                        cropGrowthLevels.invalidate(UUID.fromString(islandId));
                    } else if (isGlobalCacheEvent(type)) {
                        cropGrowthLevels.invalidateAll();
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
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to poll permission cache events: " + exception.getMessage());
        }
    }

    private synchronized boolean markSeen(String key) {
        if (!seen.add(key)) {
            return false;
        }
        seenOrder.addLast(key);
        while (seenOrder.size() > MAX_SEEN_EVENTS) {
            String oldest = seenOrder.removeFirst();
            seen.remove(oldest);
        }
        return true;
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

    private boolean handlesIslandChat(String type, Map<String, String> fields) {
        if (!type.equals(CloudIslandEventType.ISLAND_CHAT_SENT.name())) {
            return false;
        }
        String islandIdValue = fields.getOrDefault("islandId", "");
        String actorUuidValue = fields.getOrDefault("actorUuid", "");
        String message = fields.getOrDefault("message", "");
        if (islandIdValue.isBlank() || actorUuidValue.isBlank() || message.isBlank()) {
            return true;
        }
        UUID islandId = UUID.fromString(islandIdValue);
        String channel = fields.getOrDefault("channel", "ISLAND");
        String actorName = fields.getOrDefault("actorName", actorUuidValue);
        Bukkit.getScheduler().runTask(plugin, () -> broadcastIslandChat(islandId, actorName, channel, message));
        return true;
    }

    private void broadcastIslandChat(UUID islandId, String actorName, String channel, String chatMessage) {
        boolean teamChannel = channel.equalsIgnoreCase("TEAM");
        String normalizedChannel = teamChannel ? "팀" : "섬";
        String message = "[" + normalizedChannel + "] " + actorName + ": " + chatMessage;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (teamChannel) {
                if (protection.memberOrTrusted(islandId, online.getUniqueId())) {
                    online.sendMessage(message);
                }
                continue;
            }
            UUID currentIslandId = protection.islandAt(online.getLocation().getBlock()).orElse(null);
            if (islandId.equals(currentIslandId)) {
                online.sendMessage(message);
            }
        }
    }

    private boolean handlesVisitorKick(String type, Map<String, String> fields) {
        if (!type.equals(CloudIslandEventType.ISLAND_VISITOR_KICKED.name())) {
            return false;
        }
        String islandIdValue = fields.getOrDefault("islandId", "");
        String playerUuidValue = fields.getOrDefault("playerUuid", "");
        if (islandIdValue.isBlank() || playerUuidValue.isBlank()) {
            return true;
        }
        UUID islandId = UUID.fromString(islandIdValue);
        UUID playerUuid = UUID.fromString(playerUuidValue);
        Bukkit.getScheduler().runTask(plugin, () -> moveVisitorToFallback(islandId, playerUuid));
        return true;
    }

    private void moveVisitorToFallback(UUID islandId, UUID playerUuid) {
        Player target = Bukkit.getPlayer(playerUuid);
        if (target == null) {
            return;
        }
        UUID currentIslandId = protection.islandAt(target.getLocation().getBlock()).orElse(null);
        if (!islandId.equals(currentIslandId)) {
            return;
        }
        target.sendMessage("섬에서 추방되어 로비로 이동합니다.");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("Connect");
            output.writeUTF(fallbackServerName);
            target.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to move kicked visitor to fallback: " + exception.getMessage());
        }
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

    private boolean affectsCrop(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.CROP)) {
            return true;
        }
        try {
            if (!CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(type)).contains(CacheInvalidationPlan.CacheTarget.CROP)) {
                return false;
            }
            return !type.equals(CloudIslandEventType.ISLAND_UPGRADE.name()) || fields.getOrDefault("upgradeKey", "").equalsIgnoreCase("crop");
        } catch (IllegalArgumentException ignored) {
            return type.equals("ISLAND_UPGRADE") && fields.getOrDefault("upgradeKey", "").equalsIgnoreCase("crop");
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
        String source = raw == null ? "" : raw;
        int index = 0;
        while (index < source.length()) {
            int keyStart = source.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = jsonStringEnd(source, keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            int colon = source.indexOf(':', keyEnd + 1);
            int valueStart = colon < 0 ? -1 : source.indexOf('"', colon + 1);
            if (valueStart < 0) {
                break;
            }
            int valueEnd = jsonStringEnd(source, valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            result.put(unescape(source.substring(keyStart + 1, keyEnd)), unescape(source.substring(valueStart + 1, valueEnd)));
            index = valueEnd + 1;
        }
        return result;
    }

    private java.util.List<ParsedEvent> events(String json) {
        java.util.List<ParsedEvent> result = new java.util.ArrayList<>();
        String source = json == null ? "" : json;
        int arrayStart = source.indexOf("\"events\":[");
        if (arrayStart < 0) {
            return result;
        }
        int index = source.indexOf('{', arrayStart);
        while (index >= 0 && index < source.length()) {
            int objectEnd = matchingObjectEnd(source, index);
            if (objectEnd < 0) {
                break;
            }
            String object = source.substring(index, objectEnd + 1);
            String type = textField(object, "type");
            String fields = objectField(object, "fields");
            String occurredAt = textField(object, "occurredAt");
            long sequence = longField(object, "seq");
            if (!type.isBlank() && !occurredAt.isBlank()) {
                result.add(new ParsedEvent(sequence, type, fields, occurredAt));
            }
            index = source.indexOf('{', objectEnd + 1);
        }
        return result;
    }

    private long longField(String object, String key) {
        String needle = "\"" + key + "\":";
        int start = object.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        int valueStart = start + needle.length();
        int valueEnd = valueStart;
        while (valueEnd < object.length() && Character.isDigit(object.charAt(valueEnd))) {
            valueEnd++;
        }
        try {
            return Long.parseLong(object.substring(valueStart, valueEnd));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private int matchingObjectEnd(String source, int objectStart) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = objectStart; i < source.length(); i++) {
            char current = source.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = inString;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String textField(String object, String key) {
        String needle = "\"" + key + "\":\"";
        int start = object.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int valueEnd = jsonStringEnd(object, valueStart);
        return valueEnd < 0 ? "" : unescape(object.substring(valueStart, valueEnd));
    }

    private String objectField(String object, String key) {
        String needle = "\"" + key + "\":";
        int start = object.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int objectStart = object.indexOf('{', start + needle.length());
        if (objectStart < 0) {
            return "";
        }
        int objectEnd = matchingObjectEnd(object, objectStart);
        return objectEnd < 0 ? "" : object.substring(objectStart + 1, objectEnd);
    }

    private int jsonStringEnd(String source, int start) {
        boolean escaped = false;
        for (int i = start; i < source.length(); i++) {
            char current = source.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    private String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                builder.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            builder.append(current);
        }
        if (escaped) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private record ParsedEvent(long sequence, String type, String fields, String occurredAt) {}
}
