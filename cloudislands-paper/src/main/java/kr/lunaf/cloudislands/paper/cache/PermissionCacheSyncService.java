package kr.lunaf.cloudislands.paper.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PermissionCacheSyncService {
    private static final Pattern FLAG = Pattern.compile("\"([A-Z_]+)\"\\s*:\\s*\"([^\"]*)\"");

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
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sync(islandId));
            return;
        }
        try {
            cache.invalidate(islandId);
            loadMembers(islandId, client.listIslandMembers(islandId).join());
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
        for (String object : objects(json, "members")) {
            try {
                cache.putRole(islandId, UUID.fromString(text(object, "playerUuid")), IslandRole.valueOf(text(object, "role")));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadRules(UUID islandId, String json) {
        for (String object : objects(json, "permissions")) {
            try {
                cache.putRule(islandId, IslandRole.valueOf(text(object, "role")), IslandPermission.valueOf(text(object, "permission")), bool(object, "allowed"));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadFlags(UUID islandId, String json) {
        Matcher matcher = FLAG.matcher(json == null ? "" : json);
        while (matcher.find()) {
            try {
                cache.putFlag(islandId, IslandFlag.valueOf(matcher.group(1)), matcher.group(2));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private List<String> objects(String json, String arrayField) {
        List<String> result = new ArrayList<>();
        String source = json == null ? "" : json;
        int arrayKey = source.indexOf("\"" + arrayField + "\"");
        if (arrayKey < 0) {
            return result;
        }
        int arrayStart = source.indexOf('[', arrayKey);
        if (arrayStart < 0) {
            return result;
        }
        int index = source.indexOf('{', arrayStart);
        while (index >= 0) {
            int end = matchingObjectEnd(source, index);
            if (end < 0) {
                break;
            }
            result.add(source.substring(index, end + 1));
            int nextArrayEnd = source.indexOf(']', end + 1);
            int nextObject = source.indexOf('{', end + 1);
            if (nextObject < 0 || (nextArrayEnd >= 0 && nextArrayEnd < nextObject)) {
                break;
            }
            index = nextObject;
        }
        return result;
    }

    private String text(String object, String field) {
        String needle = "\"" + field + "\"";
        int key = object.indexOf(needle);
        if (key < 0) {
            return "";
        }
        int colon = object.indexOf(':', key + needle.length());
        int valueStart = colon < 0 ? -1 : object.indexOf('"', colon + 1);
        if (valueStart < 0) {
            return "";
        }
        int valueEnd = jsonStringEnd(object, valueStart + 1);
        return valueEnd < 0 ? "" : unescape(object.substring(valueStart + 1, valueEnd));
    }

    private boolean bool(String object, String field) {
        String needle = "\"" + field + "\"";
        int key = object.indexOf(needle);
        if (key < 0) {
            return false;
        }
        int colon = object.indexOf(':', key + needle.length());
        if (colon < 0) {
            return false;
        }
        int valueStart = colon + 1;
        while (valueStart < object.length() && Character.isWhitespace(object.charAt(valueStart))) {
            valueStart++;
        }
        return object.startsWith("true", valueStart);
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

    private int jsonStringEnd(String source, int start) {
        boolean escaped = false;
        for (int i = start; i < source.length(); i++) {
            char current = source.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    private String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
