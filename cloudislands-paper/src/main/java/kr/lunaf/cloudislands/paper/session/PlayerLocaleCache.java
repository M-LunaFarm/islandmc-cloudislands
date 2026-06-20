package kr.lunaf.cloudislands.paper.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import org.bukkit.entity.Player;

public final class PlayerLocaleCache {
    private final Map<UUID, String> locales = new ConcurrentHashMap<>();

    public String locale(Player player) {
        if (player == null) {
            return "";
        }
        return locale(player.getUniqueId(), player.getLocale());
    }

    public String locale(UUID playerUuid, String fallback) {
        if (playerUuid == null) {
            return PlayerIslandProfile.normalizeLocale(fallback);
        }
        return locales.getOrDefault(playerUuid, PlayerIslandProfile.normalizeLocale(fallback));
    }

    public void remember(UUID playerUuid, String locale) {
        if (playerUuid == null) {
            return;
        }
        locales.put(playerUuid, PlayerIslandProfile.normalizeLocale(locale));
    }

    public void remember(Player player) {
        if (player != null) {
            remember(player.getUniqueId(), player.getLocale());
        }
    }

    public void forget(UUID playerUuid) {
        if (playerUuid != null) {
            locales.remove(playerUuid);
        }
    }

    public void clear() {
        locales.clear();
    }

    public static String profileLocale(String json, String fallback) {
        String value = jsonText(json, "locale");
        return PlayerIslandProfile.normalizeLocale(value == null || value.isBlank() ? fallback : value);
    }

    private static String jsonText(String json, String field) {
        if (json == null || json.isBlank() || field == null || field.isBlank()) {
            return null;
        }
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        int index = start + needle.length();
        boolean escaped = false;
        while (index < json.length()) {
            char current = json.charAt(index++);
            if (escaped) {
                value.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return value.toString();
            }
            value.append(current);
        }
        return null;
    }
}
