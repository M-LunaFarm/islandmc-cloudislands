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
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

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
        client.islands().getIslandByOwner(playerUuid)
            .thenCompose(this::snapshotWithBank)
            .exceptionally(error -> Snapshot.empty(System.currentTimeMillis() + MISS_TTL_MILLIS))
            .thenAccept(snapshot -> cache.put(playerUuid, snapshot))
            .whenComplete((_result, _error) -> refreshing.remove(playerUuid));
    }

    private CompletableFuture<Snapshot> snapshotWithBank(CoreGuiViews.IslandInfoView island) {
        long expiresAt = System.currentTimeMillis() + CACHE_TTL_MILLIS;
        String islandId = island == null ? "" : island.islandId();
        if (islandId == null || islandId.isBlank()) {
            return CompletableFuture.completedFuture(Snapshot.empty(System.currentTimeMillis() + MISS_TTL_MILLIS));
        }
        UUID parsedIslandId = uuid(islandId);
        if (parsedIslandId == null) {
            return CompletableFuture.completedFuture(new Snapshot(island, null, expiresAt));
        }
        return client.bank().islandBank(parsedIslandId)
            .handle((bank, error) -> new Snapshot(island, error == null ? bank : null, expiresAt));
    }

    private String value(Snapshot snapshot, String params) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT).replace('-', '_');
        CoreGuiViews.IslandInfoView island = snapshot.island();
        CoreGuiViews.BankView bank = snapshot.bank();
        return switch (key) {
            case "has_island" -> Boolean.toString(island != null && !island.islandId().isBlank());
            case "island_id", "id" -> island == null ? "" : island.islandId();
            case "island_name", "name" -> island == null ? "" : island.name();
            case "owner_uuid", "owner" -> island == null ? "" : island.ownerUuid();
            case "state", "island_state" -> island == null ? "" : island.state();
            case "size", "island_size" -> island == null ? "" : Long.toString(island.size());
            case "border", "island_border" -> island == null ? "" : Long.toString(island.border());
            case "level", "island_level" -> island == null ? "" : Long.toString(island.level());
            case "worth", "value", "island_worth" -> island == null ? "" : island.worth();
            case "public", "public_access", "is_public" -> island == null ? "" : Boolean.toString(island.publicAccess());
            case "bank", "bank_balance", "balance" -> bank == null ? "" : bank.balance();
            default -> "";
        };
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record Snapshot(CoreGuiViews.IslandInfoView island, CoreGuiViews.BankView bank, long expiresAtMillis) {
        private static Snapshot empty(long expiresAtMillis) {
            return new Snapshot(null, null, expiresAtMillis);
        }
    }
}
