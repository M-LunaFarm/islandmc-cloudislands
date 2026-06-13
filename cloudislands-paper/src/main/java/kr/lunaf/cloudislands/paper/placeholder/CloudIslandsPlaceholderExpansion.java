package kr.lunaf.cloudislands.paper.placeholder;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class CloudIslandsPlaceholderExpansion extends PlaceholderExpansion {
    private static final long CACHE_TTL_MILLIS = 15_000L;
    private static final long MISS_TTL_MILLIS = 5_000L;

    private final Plugin plugin;
    private final CoreApiClient client;
    private final Map<UUID, Snapshot> cache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> refreshing = ConcurrentHashMap.newKeySet();

    public CloudIslandsPlaceholderExpansion(Plugin plugin, CoreApiClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cloudislands";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Snapshot snapshot = cache.get(playerUuid);
        if (snapshot == null || snapshot.expiresAtMillis() < now) {
            refresh(playerUuid);
        }
        if (snapshot == null) {
            return "";
        }
        return value(snapshot, params);
    }

    private void refresh(UUID playerUuid) {
        if (!refreshing.add(playerUuid)) {
            return;
        }
        client.islandInfoByOwner(playerUuid)
            .thenCompose(this::snapshotWithBank)
            .exceptionally(error -> Snapshot.empty(System.currentTimeMillis() + MISS_TTL_MILLIS))
            .thenAccept(snapshot -> cache.put(playerUuid, snapshot))
            .whenComplete((_result, _error) -> refreshing.remove(playerUuid));
    }

    private CompletableFuture<Snapshot> snapshotWithBank(String islandJson) {
        long expiresAt = System.currentTimeMillis() + CACHE_TTL_MILLIS;
        String islandId = text(islandJson, "islandId");
        if (islandId.isBlank()) {
            return CompletableFuture.completedFuture(Snapshot.empty(System.currentTimeMillis() + MISS_TTL_MILLIS));
        }
        UUID parsedIslandId = uuid(islandId);
        if (parsedIslandId == null) {
            return CompletableFuture.completedFuture(new Snapshot(islandJson, "", expiresAt));
        }
        return client.islandBank(parsedIslandId)
            .handle((bankJson, error) -> new Snapshot(islandJson, error == null ? bankJson : "", expiresAt));
    }

    private String value(Snapshot snapshot, String params) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT).replace('-', '_');
        String islandJson = snapshot.islandJson();
        String bankJson = snapshot.bankJson();
        return switch (key) {
            case "has_island" -> Boolean.toString(!text(islandJson, "islandId").isBlank());
            case "island_id", "id" -> text(islandJson, "islandId");
            case "island_name", "name" -> text(islandJson, "name");
            case "owner_uuid", "owner" -> text(islandJson, "ownerUuid");
            case "state", "island_state" -> text(islandJson, "state");
            case "size", "island_size" -> number(islandJson, "size");
            case "border", "island_border" -> number(islandJson, "border");
            case "level", "island_level" -> number(islandJson, "level");
            case "worth", "value", "island_worth" -> text(islandJson, "worth");
            case "public", "public_access", "is_public" -> bool(islandJson, "publicAccess");
            case "bank", "bank_balance", "balance" -> text(bankJson, "balance");
            default -> "";
        };
    }

    private static String text(String json, String key) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = valueStart; index < json.length(); index++) {
            char current = json.charAt(index);
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
                break;
            }
            value.append(current);
        }
        return value.toString();
    }

    private static String number(String json, String key) {
        String raw = raw(json, key);
        return raw.isBlank() ? "" : raw;
    }

    private static String bool(String json, String key) {
        String raw = raw(json, key);
        if (raw.equals("true") || raw.equals("false")) {
            return raw;
        }
        return "";
    }

    private static String raw(String json, String key) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char current = json.charAt(valueEnd);
            if (current == ',' || current == '}') {
                break;
            }
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record Snapshot(String islandJson, String bankJson, long expiresAtMillis) {
        private static Snapshot empty(long expiresAtMillis) {
            return new Snapshot("", "", expiresAtMillis);
        }
    }
}
